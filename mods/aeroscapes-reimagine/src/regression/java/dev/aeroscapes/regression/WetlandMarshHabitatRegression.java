package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.ecology.BirdHabitat;
import dev.aeroscapes.worldgen.ExposedIslandSurfaces;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Finds actual marsh surfaces and naturally saved vegetation without installing a test biome. */
final class WetlandMarshHabitatRegression {
  private static final String MARSH = "wetland_whimsy:marsh";
  private static final String VOID = "aeroscapes_reimagine:void";
  private static final TicketType<ChunkPos> TICKET =
      TicketType.create("aeroscapes_wetland_habitat", Comparator.comparingLong(ChunkPos::toLong));

  private WetlandMarshHabitatRegression() {}

  static void run(ServerLevel level, Path output) {
    if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)
        || !level.getServer().isDedicatedServer())
      throw new IllegalStateException("Wetland habitat needs a disposable native noise server");
    Audit audit = new Audit(level, generator, output);
    NeoForge.EVENT_BUS.addListener(audit::tick);
    LogUtils.getLogger()
        .info("Wetland natural habitat scan started: seed={} output={}", level.getSeed(), output);
  }

  private record Column(int x, int z) {}

  private static final class Audit {
    private final ServerLevel level;
    private final NoiseBasedChunkGenerator generator;
    private final IslandTerrain terrain;
    private final Path output;
    private final Climate.Sampler candidateSampler;
    private final long started = System.nanoTime();
    private final ArrayDeque<Column> columns = new ArrayDeque<>();
    private final Set<ChunkPos> candidates = new LinkedHashSet<>();
    private final ArrayDeque<ChunkPos> remaining = new ArrayDeque<>();
    private final Map<String, Integer> vegetation = new TreeMap<>();
    private final JsonObject report = new JsonObject();
    private final JsonArray rawSites = new JsonArray();
    private final JsonArray fullSites = new JsonArray();
    private final JsonArray surfaces = new JsonArray();
    private final JsonArray waters = new JsonArray();
    private final JsonArray shores = new JsonArray();
    private final JsonArray vegetationExamples = new JsonArray();
    private final JsonArray voidControls = new JsonArray();
    private CompletableFuture<ChunkResult<ChunkAccess>> pending;
    private ChunkPos pendingChunk;
    private int sourceColumns;
    private int rawColumns;
    private int fullChunks;
    private boolean sourceComplete;
    private boolean finished;

    private Audit(ServerLevel level, NoiseBasedChunkGenerator generator, Path output) {
      this.level = level;
      this.generator = generator;
      this.output = output;
      terrain = IslandTerrain.from(level.getChunkSource().randomState());
      if (terrain == null)
        throw new IllegalStateException("Wetland habitat needs the owned island generator");
      Climate.Sampler caller = level.getChunkSource().randomState().sampler();
      candidateSampler =
          new Climate.Sampler(
              caller.temperature(),
              caller.humidity(),
              caller.continentalness(),
              caller.erosion(),
              caller.depth(),
              caller.weirdness(),
              caller.spawnTarget());
      IslandBiomeSampler original = (IslandBiomeSampler) (Object) caller;
      IslandBiomeSampler copy = (IslandBiomeSampler) (Object) candidateSampler;
      copy.aeroscapesReimagine$terrainMask(original.aeroscapesReimagine$terrainMask());
      copy.aeroscapesReimagine$deferTerrainMask(true);
      copy.aeroscapesReimagine$generationSource(original.aeroscapesReimagine$generationSource());
      plan();
      report.addProperty("seed", level.getSeed());
      report.addProperty("source", generator.getBiomeSource().getClass().getName());
      report.add("raw_exterior_candidates", rawSites);
      report.add("FULL_chunks", fullSites);
      report.add("safe_marsh_surfaces", surfaces);
      report.add("marsh_water", waters);
      report.add("marsh_shores", shores);
      report.add("native_vegetation_examples", vegetationExamples);
      report.add("far_void_controls", voidControls);
      registeredContent();
    }

    private void plan() {
      Set<Column> plan = new LinkedHashSet<>();
      List<Column> known =
          level.getSeed() == 1
              ? List.of(
                  new Column(5250, -6782),
                  new Column(5314, -6782),
                  new Column(3458, -1342),
                  new Column(1922, -8126))
              : level.getSeed() == 2
                  ? List.of(
                      new Column(-3646, -1278),
                      new Column(1410, -318),
                      new Column(-126, 1858),
                      new Column(5122, -5374))
                  : List.of();
      if (!known.isEmpty())
        for (int radius = 0; radius <= 8; radius++)
          for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++) {
              if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
              // Existing raw witnesses only prioritize search; every surface is validated anew.
              for (Column origin : known)
                plan.add(new Column(origin.x() + dx * 16, origin.z() + dz * 16));
            }
      for (int z = -8192; z <= 8192; z += 512)
        for (int x = -8192; x <= 8192; x += 512) plan.add(new Column(x + 2, z + 2));
      RandomSource random = RandomSource.create(level.getSeed() ^ 0x5745544C414E44L);
      for (int index = 0; index < 4096; index++)
        plan.add(new Column(random.nextInt(4097) * 4 - 8190, random.nextInt(4097) * 4 - 8190));
      columns.addAll(plan);
    }

    private void tick(ServerTickEvent.Post event) {
      if (event.getServer() != level.getServer() || finished) return;
      try {
        if (System.nanoTime() - started > 180_000_000_000L) {
          finish("deadline");
          return;
        }
        if (!sourceComplete) {
          long slice = System.nanoTime() + 20_000_000L;
          while (!columns.isEmpty()
              && rawColumns < 256
              && candidates.size() < 24
              && System.nanoTime() < slice) sourceColumn(columns.removeFirst());
          if (columns.isEmpty() || rawColumns >= 256 || candidates.size() >= 24) {
            sourceComplete = true;
            remaining.addAll(candidates);
          }
          return;
        }
        if (pending != null) {
          if (!pending.isDone()) return;
          ChunkAccess chunk =
              pending
                  .join()
                  .orElseThrow(() -> new IllegalStateException("Marsh FULL chunk unavailable"));
          pending = null;
          try {
            inspect(chunk);
          } finally {
            release();
          }
          if (fullChunks >= 3 && !surfaces.isEmpty() && !vegetation.isEmpty() && !waters.isEmpty())
            finish("complete");
          return;
        }
        if (remaining.isEmpty() || fullChunks >= 24) {
          finish(surfaces.isEmpty() || vegetation.isEmpty() ? "finite_absence" : "complete");
          return;
        }
        pendingChunk = remaining.removeFirst();
        ChunkPos requested = pendingChunk;
        level.getChunkSource().addRegionTicket(TICKET, requested, 0, requested);
        pending =
            CompletableFuture.supplyAsync(
                    () ->
                        level
                            .getChunkSource()
                            .getChunkFuture(requested.x, requested.z, ChunkStatus.FULL, true),
                    Util.backgroundExecutor())
                .thenCompose(future -> future);
      } catch (RuntimeException failure) {
        if (!finished) {
          report.addProperty("failure", failure.toString());
          finish("failed");
        }
        throw failure;
      }
    }

    private void sourceColumn(Column column) {
      sourceColumns++;
      var source = generator.getBiomeSource();
      boolean climateCandidate = false;
      for (int y = level.getMinBuildHeight() + 16; y < level.getMaxBuildHeight(); y += 16)
        if (source
            .getNoiseBiome(column.x() >> 2, y >> 2, column.z() >> 2, candidateSampler)
            .is(ResourceLocation.parse(MARSH))) {
          climateCandidate = true;
          break;
        }
      if (!climateCandidate) return;
      NoiseColumn raw =
          generator.getBaseColumn(
              column.x(), column.z(), level, level.getChunkSource().randomState());
      rawColumns++;
      for (int y = level.getMaxBuildHeight() - 4; y >= level.getMinBuildHeight() + 1; y--) {
        if (!solid(raw.getBlock(y)) || !raw.getBlock(y + 1).isAir() || !raw.getBlock(y + 2).isAir())
          continue;
        if (ExposedIslandSurfaces.interpolated(
                column.x(),
                y + 1,
                column.z(),
                (x, height, z) -> terrain.biomeDensity(new SinglePointContext(x, height, z)))
            > 0) continue;
        BlockPos position = new BlockPos(column.x(), y + 1, column.z());
        if (!sourceBiome(position).equals(MARSH)) continue;
        ChunkPos chunk = new ChunkPos(position);
        if (candidates.add(chunk)) {
          JsonObject candidate = position(position);
          candidate.addProperty(
              "raw_floor", BuiltInRegistries.BLOCK.getKey(raw.getBlock(y).getBlock()).toString());
          rawSites.add(candidate);
        }
        return;
      }
    }

    private void inspect(ChunkAccess chunk) {
      if (chunk.getPersistedStatus() != ChunkStatus.FULL)
        throw new IllegalStateException("Marsh chunk is not FULL");
      fullChunks++;
      JsonObject summary = new JsonObject();
      summary.addProperty("chunk_x", chunk.getPos().x);
      summary.addProperty("chunk_z", chunk.getPos().z);
      Map<String, Integer> nativeBlocks = new TreeMap<>();
      List<BlockPos> localWater = new ArrayList<>();
      List<BlockPos> localLand = new ArrayList<>();
      int land = 0;
      int water = 0;
      for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++)
        for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
          NoiseColumn raw = null;
          for (int y = level.getMaxBuildHeight() - 3; y >= level.getMinBuildHeight(); y--) {
            BlockPos position = new BlockPos(x, y, z);
            BlockState state = chunk.getBlockState(position);
            ResourceLocation block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            String actual =
                chunk
                    .getNoiseBiome(x >> 2, y >> 2, z >> 2)
                    .unwrapKey()
                    .orElseThrow()
                    .location()
                    .toString();
            if (block.getNamespace().equals("wetland_whimsy")) {
              nativeBlocks.merge(block.toString(), 1, Integer::sum);
              if (flora(block.getPath()) && actual.equals(MARSH)) {
                vegetation.merge(block.toString(), 1, Integer::sum);
                if (vegetationExamples.size() < 16) {
                  JsonObject example = position(position);
                  example.addProperty("block", block.toString());
                  example.addProperty("FULL_biome", actual);
                  vegetationExamples.add(example);
                }
              }
            }
            if (!actual.equals(MARSH)) continue;
            if (state.getFluidState().is(FluidTags.WATER)) {
              water++;
              if (localWater.size() < 512) localWater.add(position);
              if (waters.size() < 12) {
                JsonObject witness = position(position);
                witness.addProperty("block", block.toString());
                waters.add(witness);
              }
            }
            if (!solid(state)
                || !chunk.getBlockState(position.above()).isAir()
                || !chunk.getBlockState(position.above(2)).isAir()) continue;
            if (raw == null)
              raw = generator.getBaseColumn(x, z, level, level.getChunkSource().randomState());
            if (!solid(raw.getBlock(y))
                || !raw.getBlock(y + 1).isAir()
                || !sourceBiome(position.above()).equals(MARSH)
                || !chunk
                    .getNoiseBiome(x >> 2, (y + 1) >> 2, z >> 2)
                    .is(ResourceLocation.parse(MARSH))
                || !BirdHabitat.exterior(level, position.above())) continue;
            land++;
            if (localLand.size() < 256) localLand.add(position.above());
            if (surfaces.size() < 24) {
              JsonObject witness = position(position.above());
              witness.addProperty("floor", block.toString());
              witness.addProperty("FULL_biome", actual);
              surfaces.add(witness);
            }
            if (voidControls.size() < 4) voidControl(chunk, raw, position.above());
          }
        }
      if (shores.size() < 4)
        for (BlockPos landPosition : localLand) {
          BlockPos waterPosition =
              localWater.stream()
                  .filter(
                      position ->
                          Math.abs(position.getX() - landPosition.getX())
                                      + Math.abs(position.getZ() - landPosition.getZ())
                                  <= 6
                              && Math.abs(position.getY() - landPosition.getY()) <= 4)
                  .findFirst()
                  .orElse(null);
          if (waterPosition == null) continue;
          JsonObject shore = new JsonObject();
          shore.add("safe_land", position(landPosition));
          shore.add("water", position(waterPosition));
          shores.add(shore);
          break;
        }
      JsonObject blocks = new JsonObject();
      nativeBlocks.forEach(blocks::addProperty);
      summary.add("native_blocks", blocks);
      summary.addProperty("marsh_exterior_land_sites", land);
      summary.addProperty("marsh_water_blocks", water);
      fullSites.add(summary);
      LogUtils.getLogger().info("Wetland FULL habitat witness: {}", summary);
      write();
    }

    private void voidControl(ChunkAccess chunk, NoiseColumn raw, BlockPos surface) {
      for (int offset = 32;
          offset <= 96 && surface.getY() + offset < level.getMaxBuildHeight();
          offset += 16) {
        BlockPos position = surface.above(offset);
        if (!raw.getBlock(position.getY()).isAir()
            || !chunk.getBlockState(position).isAir()
            || !sourceBiome(position).equals(VOID)
            || !chunk
                .getNoiseBiome(position.getX() >> 2, position.getY() >> 2, position.getZ() >> 2)
                .is(ResourceLocation.parse(VOID))) continue;
        voidControls.add(position(position));
        return;
      }
    }

    private String sourceBiome(BlockPos position) {
      return generator
          .getBiomeSource()
          .getNoiseBiome(
              position.getX() >> 2,
              position.getY() >> 2,
              position.getZ() >> 2,
              level.getChunkSource().randomState().sampler())
          .unwrapKey()
          .orElseThrow()
          .location()
          .toString();
    }

    private void registeredContent() {
      var marsh =
          level
              .registryAccess()
              .registryOrThrow(Registries.BIOME)
              .getHolder(ResourceLocation.parse(MARSH))
              .orElseThrow();
      JsonArray features = new JsonArray();
      marsh
          .value()
          .getGenerationSettings()
          .features()
          .forEach(
              group ->
                  group.forEach(
                      feature ->
                          feature
                              .unwrapKey()
                              .ifPresent(key -> features.add(key.location().toString()))));
      report.add("registered_features", features);
      JsonArray spawns = new JsonArray();
      for (MobCategory category : MobCategory.values())
        for (var spawn : marsh.value().getMobSettings().getMobs(category).unwrap()) {
          ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(spawn.type);
          if (!id.getNamespace().equals("wetland_whimsy")) continue;
          JsonObject row = new JsonObject();
          row.addProperty("entity", id.toString());
          row.addProperty("category", category.getName());
          row.addProperty("weight", spawn.getWeight().asInt());
          row.addProperty("min", spawn.minCount);
          row.addProperty("max", spawn.maxCount);
          spawns.add(row);
        }
      report.add("native_wetland_spawn_entries", spawns);
    }

    private void finish(String status) {
      finished = true;
      release();
      report.addProperty("status", status);
      report.addProperty("complete", status.equals("complete"));
      report.addProperty("water_observed_in_sample", !waters.isEmpty());
      report.addProperty("shore_observed_in_sample", !shores.isEmpty());
      report.addProperty("elapsed_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
      report.addProperty("source_columns", sourceColumns);
      report.addProperty("raw_columns", rawColumns);
      report.addProperty("FULL_requested_centers", fullChunks);
      report.addProperty("FULL_center_budget", 24);
      JsonObject flora = new JsonObject();
      vegetation.forEach(flora::addProperty);
      report.add("saved_native_vegetation", flora);
      report.addProperty(
          "limits",
          "Finite natural sample; FULL center budget excludes native generation halos. Spawn entries are configured habitats, not observed rare creatures. Missing water or a feature in this sample is not global absence. No biome or block was forced.");
      write();
      LogUtils.getLogger()
          .info(
              "Wetland natural habitat scan {}: FULL={} surfaces={} flora={} water={} output={}",
              status,
              fullChunks,
              surfaces.size(),
              vegetation,
              waters.size(),
              output);
    }

    private void release() {
      if (pendingChunk != null) {
        level.getChunkSource().removeRegionTicket(TICKET, pendingChunk, 0, pendingChunk);
        pendingChunk = null;
      }
      if (pending != null) {
        pending.cancel(false);
        pending = null;
      }
    }

    private void write() {
      try {
        Files.writeString(
            output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      } catch (IOException failure) {
        throw new IllegalStateException("Cannot write marsh habitat evidence", failure);
      }
    }
  }

  private static boolean solid(BlockState state) {
    return state.getFluidState().isEmpty()
        && Block.isFaceFull(
            state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP);
  }

  private static boolean flora(String path) {
    return path.equals("cordgrass")
        || path.equals("pennywort")
        || path.equals("fellcap_mushroom")
        || path.equals("aria_mushroom")
        || path.equals("bald_cypress_log")
        || path.equals("bald_cypress_leaves");
  }

  private static JsonObject position(BlockPos position) {
    JsonObject result = new JsonObject();
    result.addProperty("x", position.getX());
    result.addProperty("y", position.getY());
    result.addProperty("z", position.getZ());
    return result;
  }
}
