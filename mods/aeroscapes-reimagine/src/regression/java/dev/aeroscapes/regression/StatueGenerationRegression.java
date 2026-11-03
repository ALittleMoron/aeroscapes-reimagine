package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.SupportedStructure;
import java.time.Duration;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import tictim.paraglider.contents.Contents;
import tictim.paraglider.contents.worldgen.BaseHornedStatuePiece;

/** Native selected FULL starts and committed statues, with bounded template-only preflight. */
public final class StatueGenerationRegression {
  private static final int REGION_RADIUS = 12;

  private record Target(String path, String block, boolean underground) {}

  private StatueGenerationRegression() {}

  public static void run(MinecraftServer server) {
    require(
        server.isDedicatedServer() && server.isSameThread(), "Dedicated server thread required");
    require(server.overworld().getSeed() == 1L, "Statue generation fixture requires seed 1");
    require(
        IslandTerrain.from(server.overworld().getChunkSource().randomState()) != null,
        "Statue support fixture requires the owned island generator");
    verifyRotatedBounds(server.overworld());
    for (Target target :
        List.of(
            new Target("tarrey_town_goddess_statue", "goddess_statue", false),
            new Target("underground_horned_statue", "horned_statue", true))) {
      search(server.overworld(), target, true);
    }
    ServerLevel nether = server.getLevel(Level.NETHER);
    require(nether != null, "Statue fixture requires the Nether");
    search(nether, new Target("nether_horned_statue", "horned_statue", true), false);
    server.saveAllChunks(true, true, true);
    LogUtils.getLogger()
        .info(
            "Statue generation regression passed: three natural saved FULL starts and void guards");
  }

  private static void verifyRotatedBounds(ServerLevel level) {
    ResourceLocation template = id("underground_horned_statue");
    BlockPos position = new BlockPos(101, 90, 207);
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    for (Rotation rotation : Rotation.values()) {
      BaseHornedStatuePiece piece =
          new BaseHornedStatuePiece(
                  Contents.get().undergroundHornedStatuePiece(),
                  level.getStructureManager(),
                  template,
                  position)
              .rot(BlockPos.ZERO, rotation);
      BoundingBox expected =
          level
              .getStructureManager()
              .getOrCreate(template)
              .getBoundingBox(
                  new StructurePlaceSettings()
                      .setRotation(rotation)
                      .setRotationPivot(BlockPos.ZERO),
                  position);
      require(
          piece.getBoundingBox().equals(expected),
          "Rotated statue prefit bounds differ from the actual native template: " + rotation);
      var saved = piece.createTag(serialization);
      var restored = Contents.get().undergroundHornedStatuePiece().load(serialization, saved);
      require(
          restored.getBoundingBox().equals(expected),
          "Rotated statue bounds changed after piece serialization: " + rotation);
    }
  }

  private static void search(ServerLevel level, Target target, boolean needsVoidWitness) {
    Holder<Structure> holder =
        level
            .registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .getHolder(id(target.path()))
            .orElseThrow();
    Structure structure = holder.value();
    require(
        needsVoidWitness == (structure instanceof SupportedStructure),
        "Unexpected native/adapted statue root: " + target.path());
    RandomSpreadStructurePlacement placement =
        level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).stream()
            .filter(RandomSpreadStructurePlacement.class::isInstance)
            .map(RandomSpreadStructurePlacement.class::cast)
            .findFirst()
            .orElseThrow();
    long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
    int attempts = 0;
    int acceptedPrefits = 0;
    boolean positive = false;
    boolean voidWitness = !needsVoidWitness;
    for (int radius = 0; radius <= REGION_RADIUS && (!positive || !voidWitness); radius++) {
      for (int regionX = -radius; regionX <= radius; regionX++) {
        for (int regionZ = -radius; regionZ <= radius; regionZ++) {
          if (Math.max(Math.abs(regionX), Math.abs(regionZ)) != radius) continue;
          require(
              System.nanoTime() < deadline,
              "Statue search timed out: "
                  + target.path()
                  + " attempts="
                  + attempts
                  + " prefits="
                  + acceptedPrefits
                  + " FULL="
                  + positive
                  + " void="
                  + voidWitness);
          attempts++;
          ChunkPos origin =
              placement.getPotentialStructureChunk(
                  level.getSeed(), regionX * placement.spacing(), regionZ * placement.spacing());
          if (!placement.isStructureChunk(
              level.getChunkSource().getGeneratorState(), origin.x, origin.z)) continue;
          if (!voidWitness && voidColumn(level, origin)) {
            require(
                structure
                    .findValidGenerationPoint(context(level, origin, structure, true))
                    .isEmpty(),
                "Statue accepted an unsupported void-column candidate: " + target.path() + origin);
            voidWitness = true;
            LogUtils.getLogger()
                .info("Statue void-negative witness id={} origin={}", id(target.path()), origin);
          }
          if (positive) continue;
          var prefit = structure.findValidGenerationPoint(context(level, origin, structure, false));
          if (prefit.isEmpty()) continue;
          acceptedPrefits++;
          var predicted = prefit.orElseThrow().getPiecesBuilder().build();
          var full = level.getChunk(origin.x, origin.z);
          require(full.getPersistedStatus() == ChunkStatus.FULL, "Statue chunk did not reach FULL");
          StructureStart start = full.getStartForStructure(structure);
          if (start == null || !start.isValid()) continue;
          require(start.getChunkPos().equals(origin), "Natural statue start lost its owning chunk");
          var serialization = StructurePieceSerializationContext.fromLevel(level);
          ListTag expected = new ListTag();
          for (var piece : predicted.pieces()) expected.add(piece.createTag(serialization));
          BoundingBox bounds = start.getBoundingBox();
          for (int chunkX = Math.floorDiv(bounds.minX(), 16);
              chunkX <= Math.floorDiv(bounds.maxX(), 16);
              chunkX++) {
            for (int chunkZ = Math.floorDiv(bounds.minZ(), 16);
                chunkZ <= Math.floorDiv(bounds.maxZ(), 16);
                chunkZ++) level.getChunk(chunkX, chunkZ);
          }
          ListTag actual = new ListTag();
          for (var piece : start.getPieces()) actual.add(piece.createTag(serialization));
          require(
              actual.equals(expected),
              "Native statue pieces or rotated bounds changed between prefit and FULL: "
                  + target.path());
          var saved = start.createTag(serialization, origin);
          StructureStart reloaded =
              StructureStart.loadStaticStart(serialization, saved, level.getSeed());
          require(
              reloaded != null
                  && reloaded.isValid()
                  && reloaded.getBoundingBox().equals(start.getBoundingBox()),
              "Native statue start failed its saved-data roundtrip: " + target.path());
          verifyCommittedStatue(level, target, start);
          positive = true;
          LogUtils.getLogger()
              .info(
                  "Natural FULL statue witness id={} dimension={} seed={} origin={} bounds={} attempts={} prefits={}",
                  id(target.path()),
                  level.dimension().location(),
                  level.getSeed(),
                  origin,
                  bounds,
                  attempts,
                  acceptedPrefits);
        }
      }
    }
    require(
        positive && voidWitness,
        "Bounded statue search incomplete: "
            + target.path()
            + " attempts="
            + attempts
            + " prefits="
            + acceptedPrefits
            + " FULL="
            + positive
            + " void="
            + voidWitness);
  }

  private static boolean voidColumn(ServerLevel level, ChunkPos origin) {
    var generator = level.getChunkSource().getGenerator();
    var randomState = level.getChunkSource().randomState();
    if (generator.getBaseHeight(
            origin.getMiddleBlockX(),
            origin.getMiddleBlockZ(),
            Heightmap.Types.OCEAN_FLOOR_WG,
            level,
            randomState)
        > level.getMinBuildHeight()) return false;
    for (int x = origin.getMinBlockX(); x <= origin.getMaxBlockX(); x++) {
      for (int z = origin.getMinBlockZ(); z <= origin.getMaxBlockZ(); z++) {
        if (generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState)
            > level.getMinBuildHeight()) return false;
      }
    }
    return true;
  }

  private static Structure.GenerationContext context(
      ServerLevel level, ChunkPos origin, Structure structure, boolean ignoreBiome) {
    var generator = level.getChunkSource().getGenerator();
    return new Structure.GenerationContext(
        level.registryAccess(),
        generator,
        generator.getBiomeSource(),
        level.getChunkSource().randomState(),
        level.getStructureManager(),
        level.getSeed(),
        origin,
        level,
        biome -> ignoreBiome || structure.biomes().contains(biome));
  }

  private static void verifyCommittedStatue(
      ServerLevel level, Target target, StructureStart start) {
    var statue = BuiltInRegistries.BLOCK.getOptional(id(target.block())).orElseThrow();
    BoundingBox bounds = start.getBoundingBox();
    int found = 0;
    for (BlockPos position :
        BlockPos.betweenClosed(
            bounds.minX(),
            bounds.minY(),
            bounds.minZ(),
            bounds.maxX(),
            bounds.maxY(),
            bounds.maxZ())) {
      if (!level.getBlockState(position).is(statue)) continue;
      require(
          !level.getBlockState(position.below()).isAir(),
          "Naturally generated statue has no supporting block: " + position);
      found++;
    }
    require(found > 0, "FULL start contains no actual statue block: " + target.path());
    if (!target.underground()) {
      var generator = level.getChunkSource().getGenerator();
      int tested = 0;
      int deeplySupported = 0;
      for (int x = bounds.minX(); ; x = Math.min(x + 4, bounds.maxX())) {
        for (int z = bounds.minZ(); ; z = Math.min(z + 4, bounds.maxZ())) {
          var natural = generator.getBaseColumn(x, z, level, level.getChunkSource().randomState());
          boolean grounded = false;
          for (int depth = 1; depth <= 8; depth++)
            if (solid(natural.getBlock(bounds.minY() - depth))) grounded = true;
          require(grounded, "Natural goddess platform bridges an unsupported island edge");
          tested++;
          if (solid(natural.getBlock(bounds.minY() - 4))) deeplySupported++;
          if (z == bounds.maxZ()) break;
        }
        if (x == bounds.maxX()) break;
      }
      require(
          deeplySupported * 10 >= tested * 9,
          "Natural goddess platform lacks a substantial island foundation");
    } else if (level.dimension() == Level.OVERWORLD) {
      int roof = 0;
      int floor = 0;
      for (int x : new int[] {bounds.minX(), bounds.getCenter().getX(), bounds.maxX()}) {
        for (int z : new int[] {bounds.minZ(), bounds.getCenter().getZ(), bounds.maxZ()}) {
          var natural =
              level
                  .getChunkSource()
                  .getGenerator()
                  .getBaseColumn(x, z, level, level.getChunkSource().randomState());
          boolean grounded = false;
          for (int depth = 1; depth <= 5; depth++)
            if (solid(natural.getBlock(bounds.minY() - depth))) grounded = true;
          if (grounded) floor++;
          if (solid(natural.getBlock(bounds.maxY() + 2))) roof++;
        }
      }
      require(floor >= 6 && roof >= 4, "Underground statue lacks natural floor or roof support");
    }
  }

  private static boolean solid(BlockState state) {
    return state.getFluidState().isEmpty()
        && Block.isFaceFull(
            state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP);
  }

  private static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath("paraglider", path);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
