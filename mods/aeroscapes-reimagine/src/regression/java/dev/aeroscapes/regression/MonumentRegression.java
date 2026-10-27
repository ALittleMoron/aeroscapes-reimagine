package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandMonumentStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.phys.AABB;

/** Natural wet monuments retain their native rooms, saved geometry, and water container. */
final class MonumentRegression {
  private MonumentRegression() {}

  private static StructureStart start(ServerLevel level, ChunkPos chunk) {
    var structure =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .get(ResourceLocation.withDefaultNamespace("monument"));
    require(structure instanceof IslandMonumentStructure, "Missing island monument definition");
    var state = level.getChunkSource().getGeneratorState();
    var holder =
        level.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(structure);
    require(
        state.getPlacementsForStructure(holder).stream()
            .anyMatch(placement -> placement.isStructureChunk(state, chunk.x, chunk.z)),
        "Monument fixture is not a native structure-set candidate");
    StructureStart start = level.getChunk(chunk.x, chunk.z).getStartForStructure(structure);
    require(start != null && start.isValid(), "Missing natural monument " + chunk);
    return start;
  }

  private static BoundingBox nativeBounds(ServerLevel level, StructurePiece piece) {
    int[] box =
        piece.createTag(StructurePieceSerializationContext.fromLevel(level)).getIntArray("BB");
    return new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]);
  }

  private static List<String> layout(StructurePiece piece) {
    List<String> result = new ArrayList<>();
    for (StructurePiece child : ((OceanMonumentPieces.MonumentBuilding) piece).childPieces) {
      result.add(child.getClass().getName() + child.getOrientation() + child.getBoundingBox());
    }
    return result;
  }

  static void generate(ServerLevel level, int x, int z) {
    ChunkPos chunk = new ChunkPos(x, z);
    StructureStart start = start(level, chunk);
    emptyNoiseRanges(level, chunk, start.getStructure());
    if (x == 614 && z == 270) cartographerOffers(level, chunk, start);
    StructurePiece root = start.getPieces().getFirst();
    BoundingBox bounds = nativeBounds(level, root);
    require(bounds.minY() > 39, "Monument fell to the native void height");
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    var saved = start.createTag(serialization, chunk);
    var restored =
        Objects.requireNonNull(
            StructureStart.loadStaticStart(serialization, saved, level.getSeed()));
    StructurePiece restoredRoot = restored.getPieces().getFirst();
    require(
        nativeBounds(level, restoredRoot).equals(bounds)
            && layout(root).equals(layout(restoredRoot)),
        "Reload changed monument room geometry");
    require(
        restoredRoot.createTag(serialization).getString("id").equals("minecraft:omb"),
        "Changed native monument piece identity");
    var legacyPieces =
        OceanMonumentStructure.regeneratePiecesAfterLoad(
            chunk,
            level.getSeed(),
            PiecesContainer.load(saved.getList("Children", 10), serialization));
    var legacyTag =
        new StructureStart(start.getStructure(), chunk, 0, legacyPieces)
            .createTag(serialization, chunk);
    var legacy =
        Objects.requireNonNull(
            StructureStart.loadStaticStart(serialization, legacyTag, level.getSeed()));
    StructurePiece legacyRoot = legacy.getPieces().getFirst();
    require(
        legacyRoot.getClass() == OceanMonumentPieces.MonumentBuilding.class
            && nativeBounds(level, legacyRoot).minY() == 39,
        "Converted a pre-existing native monument in the void");
    BoundingBox container = root.getBoundingBox();
    // Complete every neighbouring decorator before measuring containment after fluid ticks.
    for (int chunkX = (container.minX() >> 4) - 1;
        chunkX <= (container.maxX() >> 4) + 1;
        chunkX++) {
      for (int chunkZ = (container.minZ() >> 4) - 1;
          chunkZ <= (container.maxZ() >> 4) + 1;
          chunkZ++) {
        level.getChunk(chunkX, chunkZ);
        if (chunkX >= container.minX() >> 4
            && chunkX <= container.maxX() >> 4
            && chunkZ >= container.minZ() >> 4
            && chunkZ <= container.maxZ() >> 4) {
          ChunkPos loaded = new ChunkPos(chunkX, chunkZ);
          level.getChunkSource().addRegionTicket(TicketType.FORCED, loaded, 1, loaded);
        }
      }
    }
  }

  private static void cartographerOffers(
      ServerLevel level, ChunkPos chunk, StructureStart original) {
    require(
        !level.getServer().scheduleExecutables(),
        "Cartographer fixture needs the server tick thread");
    var loaded = level.getChunk(chunk.x, chunk.z);
    var structure = original.getStructure();
    int references = original.getReferences();
    var placement =
        level
            .getChunkSource()
            .getGeneratorState()
            .getPlacementsForStructure(
                level
                    .registryAccess()
                    .registryOrThrow(Registries.STRUCTURE)
                    .wrapAsHolder(structure))
            .getFirst();
    // Refresh the native presence cache together with each temporary start; both must be restored.
    // Run twice in one JVM as well as supporting an existing-world regression restart.
    for (int attempt = 0; attempt < 2; attempt++) {
      StructureStart fresh =
          new StructureStart(structure, chunk, 0, new PiecesContainer(original.getPieces()));
      try {
        loaded.setStartForStructure(structure, fresh);
        level.onStructureStartsAvailable(loaded);
        cartographerOffer(level, chunk, fresh);
      } finally {
        loaded.setStartForStructure(structure, original);
        level.onStructureStartsAvailable(loaded);
      }
      require(
          loaded.getStartForStructure(structure) == original
              && original.getReferences() == references
              && level.structureManager().checkStructurePresence(chunk, structure, placement, true)
                  == (original.canBeReferenced()
                      ? StructureCheckResult.START_PRESENT
                      : StructureCheckResult.START_NOT_PRESENT),
          "Cartographer fixture changed the original start or reference count");
    }
  }

  private static void cartographerOffer(ServerLevel level, ChunkPos chunk, StructureStart start) {
    require(start.getReferences() == 0, "Cartographer fixture was already referenced");
    Villager villager = new Villager(EntityType.VILLAGER, level);
    villager.setPos(chunk.getMinBlockX(), 132, chunk.getMinBlockZ());
    var trade =
        new VillagerTrades.TreasureMapForEmeralds(
            13,
            StructureTags.ON_OCEAN_EXPLORER_MAPS,
            "filled_map.monument",
            MapDecorationTypes.OCEAN_MONUMENT,
            12,
            5);
    MerchantOffer offer = Objects.requireNonNull(trade.getOffer(villager, RandomSource.create(1)));
    var stack = offer.getResult();
    var decoration =
        Objects.requireNonNull(stack.get(DataComponents.MAP_DECORATIONS)).decorations().get("+");
    BlockPos target = BlockPos.containing(decoration.x(), 0, decoration.z());
    require(new ChunkPos(target).equals(chunk), "Cartographer changed the local native target");
    require(start.getReferences() == 1, "Cartographer did not increment target references once");
    var map = Objects.requireNonNull(MapItem.getSavedData(stack, level));
    require(map.scale == 2 && !map.locked, "Cartographer changed native map settings");
    villager.getOffers().add(offer);
    Villager restored = new Villager(EntityType.VILLAGER, level);
    restored.load(villager.saveWithoutId(new CompoundTag()));
    var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    var original = MerchantOffer.CODEC.encodeStart(ops, offer).getOrThrow();
    require(
        original.equals(
            MerchantOffer.CODEC.encodeStart(ops, restored.getOffers().getFirst()).getOrThrow()),
        "Cartographer offer or map components changed after villager reload");
    require(
        level.findNearestMapStructure(StructureTags.ON_OCEAN_EXPLORER_MAPS, target, 0, true) == null
            && target.equals(
                level.findNearestMapStructure(
                    StructureTags.ON_OCEAN_EXPLORER_MAPS, target, 0, false))
            && start.getReferences() == 1,
        "Cartographer changed native known-target selection or references");
    restored.discard();
    villager.discard();
    LogUtils.getLogger()
        .info("Cartographer offer persistence and known-target regression passed: {}", target);
  }

  private static void emptyNoiseRanges(ServerLevel level, ChunkPos chunk, Structure structure) {
    if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator))
      throw new IllegalStateException("Monument fixture requires native noise terrain");
    var noise = generator.generatorSettings().value().noiseSettings();
    LevelHeightAccessor aboveNoise =
        LevelHeightAccessor.create(noise.minY() + noise.height(), noise.getCellHeight() * 2);
    emptyAtHeight(level, chunk, structure, aboveNoise);
    // This native placement cell has no solid corner band on seed 1. Its last
    // partial cell must be sampled rather than leaving null column entries.
    emptyAtHeight(
        level,
        new ChunkPos(-55, -53),
        structure,
        LevelHeightAccessor.create(noise.minY(), noise.height() - 1));
  }

  private static void emptyAtHeight(
      ServerLevel level, ChunkPos chunk, Structure structure, LevelHeightAccessor height) {
    var generator = level.getChunkSource().getGenerator();
    var context =
        new Structure.GenerationContext(
            level.registryAccess(),
            generator,
            generator.getBiomeSource(),
            level.getChunkSource().randomState(),
            level.getStructureManager(),
            level.getSeed(),
            chunk,
            height,
            structure.biomes()::contains);
    require(
        structure.findValidGenerationPoint(context).isEmpty(),
        "Monument accepted an empty fixture at height bounds "
            + height.getMinBuildHeight()
            + ".."
            + height.getMaxBuildHeight());
  }

  static void settled(ServerLevel level, int x, int z) {
    StructurePiece root = start(level, new ChunkPos(x, z)).getPieces().getFirst();
    BoundingBox body = nativeBounds(level, root);
    BoundingBox container = root.getBoundingBox();
    try {
      if (x == -206 && z == 723) {
        // A canyon hole grew short grass before the native water washed it out.
        BlockPos grassHole = new BlockPos(-3325, 217, 11533);
        var sealed = level.getBlockState(grassHole);
        require(
            sealed.isCollisionShapeFullBlock(level, grassHole) && sealed.getFluidState().isEmpty(),
            "Monument soft-plant hole reopened after neighbouring features and fluid ticks");
      }
      int water = 0;
      int sponges = 0;
      int gold = 0;
      int lava = 0;
      int obsidian = 0;
      int cobblestone = 0;
      for (BlockPos position :
          BlockPos.betweenClosed(
              container.minX(),
              container.minY(),
              container.minZ(),
              container.maxX(),
              container.maxY(),
              container.maxZ())) {
        var state = level.getBlockState(position);
        if (state.is(Blocks.WATER)) water++;
        if (state.is(Blocks.WET_SPONGE)) sponges++;
        if (state.is(Blocks.GOLD_BLOCK)) gold++;
        if (state.is(Blocks.LAVA)) lava++;
        if (state.is(Blocks.OBSIDIAN)) obsidian++;
        if (state.is(Blocks.COBBLESTONE)) cobblestone++;
        if (position.getX() == container.minX()
            || position.getX() == container.maxX()
            || position.getZ() == container.minZ()
            || position.getZ() == container.maxZ()
            || position.getY() == container.minY()
            || position.getY() == container.maxY()) {
          require(
              !state.isAir() && state.getFluidState().isEmpty(),
              "Monument water container leaked after fluid ticks");
        }
      }
      require(water > 1000 && sponges > 0 && gold == 8, "Incomplete wet monument rooms");
      require(lava == 0 && obsidian == 0, "Lava entered the monument water container");
      require(
          level.getEntitiesOfClass(ElderGuardian.class, AABB.of(body).inflate(6)).size() == 3,
          "Missing native elder guardians");
      for (int columnX = body.minX(); columnX <= body.maxX(); columnX++) {
        for (int columnZ = body.minZ(); columnZ <= body.maxZ(); columnZ++) {
          for (int y = level.getMinBuildHeight() + 2; y < body.minY() - 4; y++) {
            BlockPos position = new BlockPos(columnX, y, columnZ);
            if (level.getBlockState(position).is(Blocks.PRISMARINE_BRICKS)) {
              require(
                  !level
                      .getChunkSource()
                      .getGenerator()
                      .getBaseColumn(columnX, columnZ, level, level.getChunkSource().randomState())
                      .getBlock(y)
                      .isAir(),
                  "Monument generated a support column in raw air");
            }
          }
        }
      }
      require(
          IslandMonumentStructure.seaLevel(level.getSeaLevel()) == level.getSeaLevel(),
          "Monument water context leaked");
      LogUtils.getLogger()
          .info(
              "Vanilla wet monument {},{} passed: water={}, wet_sponges={}, lava={},"
                  + " obsidian={}, cobblestone={}",
              x,
              z,
              water,
              sponges,
              lava,
              obsidian,
              cobblestone);
    } finally {
      for (int chunkX = container.minX() >> 4; chunkX <= container.maxX() >> 4; chunkX++) {
        for (int chunkZ = container.minZ() >> 4; chunkZ <= container.maxZ() >> 4; chunkZ++) {
          ChunkPos loaded = new ChunkPos(chunkX, chunkZ);
          level.getChunkSource().removeRegionTicket(TicketType.FORCED, loaded, 1, loaded);
        }
      }
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
