package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandShipwreckStructure;
import java.lang.reflect.Method;
import java.util.function.BiFunction;
import java.util.function.ToIntBiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckPieces;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Native rotated ship templates must be checked at their eventual seabed height. */
public final class ShipwreckSupportRegression {
  private ShipwreckSupportRegression() {}

  public static void verify(ServerLevel level) {
    try {
      Method projection =
          IslandShipwreckStructure.class.getDeclaredMethod(
              "nativePlacementY", ShipwreckPieces.ShipwreckPiece.class, ToIntBiFunction.class);
      projection.setAccessible(true);
      Method support =
          IslandShipwreckStructure.class.getDeclaredMethod(
              "supported",
              TemplateStructurePiece.class,
              int.class,
              LevelHeightAccessor.class,
              BiFunction.class);
      support.setAccessible(true);
      int cases = 0;
      for (Rotation rotation : Rotation.values()) {
        var piece =
            new ShipwreckPieces.ShipwreckPiece(
                level.getStructureManager(),
                ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full"),
                new BlockPos(100, 90, 200),
                rotation,
                false);
        ToIntBiFunction<Integer, Integer> flatHeight = (x, z) -> 65;
        int placement = (int) projection.invoke(null, piece, flatHeight);
        require(placement == 65, "Shipwreck did not retain native first-free seabed height");
        NoiseColumn flat = column(level, 64, 80, false);
        require(
            accepted(support, piece, placement, level, (x, z) -> flat),
            "Shipwreck rejected ordinary submerged seabed");
        require(
            !accepted(support, piece, 90, level, (x, z) -> flat),
            "Shipwreck support still validates its placeholder rather than eventual height");
        require(
            accepted(
                support,
                piece,
                placement,
                level,
                (x, z) -> column(level, 62 + Math.floorMod(x + z, 5), 80, false)),
            "Shipwreck rejected modest native seabed unevenness");
        ToIntBiFunction<Integer, Integer> mixedHeights =
            (x, z) -> Math.floorMod(x, 2) == 0 ? 161 : 33;
        int mixedPlacement = (int) projection.invoke(null, piece, mixedHeights);
        require(
            mixedPlacement > 80 && mixedPlacement < 120,
            "Mixed island/floor fixture no longer produces a hanging native mean");
        require(
            !accepted(
                support,
                piece,
                mixedPlacement,
                level,
                (x, z) -> column(level, 32, 80, Math.floorMod(x, 2) == 0)),
            "Shipwreck accepted the unsupported mean between water and an upper island");
        NoiseColumn empty = column(level, level.getMinBuildHeight() - 1, 0, false);
        require(
            !accepted(support, piece, level.getMinBuildHeight(), level, (x, z) -> empty),
            "Shipwreck accepted world-bottom fallback");
        require(
            !accepted(support, piece, level.getMaxBuildHeight() - 1, level, (x, z) -> flat),
            "Shipwreck accepted a clipped native template");
        int width = piece.template().getSize().getX();
        int depth = piece.template().getSize().getZ();
        ToIntBiFunction<Integer, Integer> asymmetricHeight =
            (x, z) -> x < 100 + width && z < 200 + depth ? 65 : 145;
        require(
            (int) projection.invoke(null, piece, asymmetricHeight) == 65,
            "Shipwreck changed the pinned unrotated late height-sampling rectangle");
        largeBranch(level, rotation, projection, support, flat);
        cases++;
      }
      assemblyParity(level);
      LogUtils.getLogger()
          .info(
              "Shipwreck support regression passed: {} rotations, eventual height, uneven seabed, hanging mean and clipping",
              cases);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot exercise native shipwreck support", exception);
    }
  }

  private static void largeBranch(
      ServerLevel level, Rotation rotation, Method projection, Method support, NoiseColumn flat)
      throws ReflectiveOperationException {
    var piece = new LargeShipwreckPiece(level, rotation);
    require(piece.isTooBigToFitInWorldGenRegion(), "Large native shipwreck branch is untested");
    var context = context(level, new ChunkPos(0, 0));
    var bounds = piece.getBoundingBox();
    int expected =
        Structure.getMeanFirstOccupiedHeight(
            context, bounds.minX(), bounds.getXSpan(), bounds.minZ(), bounds.getZSpan());
    ToIntBiFunction<Integer, Integer> heights =
        (x, z) ->
            context
                .chunkGenerator()
                .getFirstOccupiedHeight(
                    x, z, Heightmap.Types.WORLD_SURFACE_WG, level, context.randomState());
    require(
        (int) projection.invoke(null, piece, heights) == expected,
        "Large shipwreck changed its native occupied-corner projection");
    piece.adjustPositionHeight(65);
    require(
        accepted(support, piece, 65, level, (x, z) -> flat),
        "Large shipwreck support relied on the stale Y90 native bounding box");
  }

  private static void assemblyParity(ServerLevel level) {
    var settings = new Structure.StructureSettings(HolderSet.direct());
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    for (boolean beached : new boolean[] {false, true}) {
      var nativeStructure = new ShipwreckStructure(settings, beached);
      var adapted = new IslandShipwreckStructure(settings, beached);
      for (int index = 0; index < 8; index++) {
        ChunkPos chunk = new ChunkPos(index * 7, index * 11);
        Structure.GenerationContext nativeContext = context(level, chunk);
        Structure.GenerationContext adaptedContext = context(level, chunk);
        var original = nativeStructure.findGenerationPoint(nativeContext).orElseThrow();
        var expected = original.getPiecesBuilder().build();
        var actual = adapted.findGenerationPoint(adaptedContext);
        if (beached) require(actual.isPresent(), "Adapter changed native beached eligibility");
        if (actual.isPresent()) {
          var stub = actual.orElseThrow();
          var pieces = stub.getPiecesBuilder().build();
          require(stub.position().equals(original.position()), "Shipwreck moved its native anchor");
          require(
              pieces.pieces().size() == expected.pieces().size(),
              "Shipwreck changed native pieces");
          for (int piece = 0; piece < pieces.pieces().size(); piece++)
            require(
                pieces
                    .pieces()
                    .get(piece)
                    .createTag(serialization)
                    .equals(expected.pieces().get(piece).createTag(serialization)),
                "Shipwreck changed its selected native template, rotation or saved fields");
        }
        require(
            nativeContext.random().nextLong() == adaptedContext.random().nextLong(),
            "Shipwreck preflight rerolled the native assembly or consumed extra RNG");
      }
    }
  }

  private static Structure.GenerationContext context(ServerLevel level, ChunkPos chunk) {
    var generator = level.getChunkSource().getGenerator();
    return new Structure.GenerationContext(
        level.registryAccess(),
        generator,
        generator.getBiomeSource(),
        level.getChunkSource().randomState(),
        level.getStructureManager(),
        level.getSeed(),
        chunk,
        level,
        biome -> true);
  }

  private static boolean accepted(
      Method support,
      TemplateStructurePiece piece,
      int placement,
      LevelHeightAccessor height,
      BiFunction<Integer, Integer, NoiseColumn> columns)
      throws ReflectiveOperationException {
    return (boolean) support.invoke(null, piece, placement, height, columns);
  }

  private static NoiseColumn column(
      LevelHeightAccessor height, int floor, int waterline, boolean upperIsland) {
    BlockState[] blocks = new BlockState[height.getHeight()];
    for (int y = height.getMinBuildHeight(); y < height.getMaxBuildHeight(); y++) {
      BlockState block = Blocks.AIR.defaultBlockState();
      if (y <= floor || upperIsland && y >= 120 && y <= 160)
        block = Blocks.STONE.defaultBlockState();
      else if (y <= waterline && y > floor) block = Blocks.WATER.defaultBlockState();
      blocks[y - height.getMinBuildHeight()] = block;
    }
    return new NoiseColumn(height.getMinBuildHeight(), blocks);
  }

  private static void require(boolean accepted, String message) {
    if (!accepted) throw new IllegalStateException(message);
  }

  private static final class LargeShipwreckPiece extends ShipwreckPieces.ShipwreckPiece {
    private LargeShipwreckPiece(ServerLevel level, Rotation rotation) {
      super(
          level.getStructureManager(),
          ResourceLocation.withDefaultNamespace("shipwreck/rightsideup_full"),
          new BlockPos(100, 90, 200),
          rotation,
          false);
      CompoundTag data = new CompoundTag();
      ListTag size = new ListTag();
      size.add(IntTag.valueOf(33));
      size.add(IntTag.valueOf(8));
      size.add(IntTag.valueOf(9));
      data.put("size", size);
      data.put("blocks", new ListTag());
      data.put("palette", new ListTag());
      template = new StructureTemplate();
      template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), data);
      boundingBox = template.getBoundingBox(placeSettings, templatePosition);
    }
  }
}
