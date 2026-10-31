package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Finite live-seed source, natural-material and FULL-chunk coverage without forcing biome IDs. */
public final class BiomeCoverageRegression {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final String VOID = "aeroscapes_reimagine:void";
  private static final int GRID_RADIUS = 8192;
  private static final int GRID_STEP = 256;
  private static final int RANDOM_RADIUS = 32768;
  private static final int RANDOM_COLUMNS = 8192;
  private static final int SOURCE_HEIGHT_STEP = 32;
  private static final int CANDIDATES_PER_BIOME = 12;
  private static final int RAW_COLUMN_BUDGET = 768;
  private static final int WITNESSES_PER_BIOME = 3;
  private static final TicketType<ChunkPos> COVERAGE_TICKET =
      TicketType.create("aeroscapes_biome_coverage", Comparator.comparingLong(ChunkPos::toLong));
  private static final Set<String> ORE_BIOMES =
      Set.of(
          "terralith:basalt_cliffs",
          "terralith:cave/mantle_caves",
          "terralith:volcanic_crater",
          "terralith:volcanic_peaks",
          "terralith:ashen_savanna",
          "terralith:tropical_jungle",
          "natures_spirit:arid_highlands",
          "natures_spirit:shrubby_highlands",
          "natures_spirit:stratified_desert");

  private BiomeCoverageRegression() {}

  public static void start(ServerLevel level, Path output) {
    if (!level.getServer().isDedicatedServer() || level.dimension() != Level.OVERWORLD)
      throw new IllegalStateException("Biome coverage needs a disposable dedicated Overworld");
    if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator))
      throw new IllegalStateException("Biome coverage needs the supported normal noise generator");
    Audit audit = new Audit(level, generator, output.toAbsolutePath().normalize());
    NeoForge.EVENT_BUS.addListener(audit::tick);
    LOGGER.info(
        "Biome coverage started: seed={} possible={} label={} FULL-budget={} output={}",
        level.getSeed(),
        audit.possible.size(),
        audit.label,
        audit.fullBudget,
        audit.output);
  }

  private record Column(int x, int z) {
    private ChunkPos chunk() {
      return new ChunkPos(x >> 4, z >> 4);
    }
  }

  private static final class Coverage {
    private final String id;
    private final Map<Column, BlockPos> candidates = new LinkedHashMap<>();
    private final Map<ChunkPos, BlockPos> raw = new LinkedHashMap<>();
    private long sourceHits;
    private BlockPos firstSource;
    private JsonObject full;

    private Coverage(String id) {
      this.id = id;
    }
  }

  private static final class Audit {
    private final ServerLevel level;
    private final NoiseBasedChunkGenerator generator;
    private final BiomeSource source;
    private final RandomState randomState;
    private final Path output;
    private final String label =
        System.getProperty("aeroscapes.regression.biomeCoverage.label", "unspecified");
    private final int fullBudget =
        Integer.getInteger("aeroscapes.regression.biomeCoverageFullBudget", 64);
    private final int timeoutSeconds =
        Integer.getInteger("aeroscapes.regression.biomeCoverageTimeoutSeconds", 1200);
    private final boolean focus = Boolean.getBoolean("aeroscapes.regression.biomeCoverageFocus");
    private final int gridStep = focus ? 64 : GRID_STEP;
    private final int sourceHeightStep = focus ? 16 : SOURCE_HEIGHT_STEP;
    private final Climate.Sampler candidateSampler;
    private final long started = System.nanoTime();
    private final RandomSource random;
    private final Set<String> possible = new HashSet<>();
    private final Set<String> targets = new TreeSet<>();
    private final Map<String, Coverage> coverage = new TreeMap<>();
    private final Set<ChunkPos> fullChunks = new LinkedHashSet<>();
    private final JsonArray violations = new JsonArray();
    private final JsonArray nativeClimateTies = new JsonArray();
    private final JsonArray farControls = new JsonArray();
    private final JsonArray caveAirControls = new JsonArray();
    private final JsonArray oreSamples = new JsonArray();
    private List<Column> rawPlan = List.of();
    private int phase;
    private int cursor;
    private int gridColumns;
    private int randomColumns;
    private int rawColumns;
    private int fullRawColumns;
    private int farRawColumns;
    private int caveAirCandidates;
    private long sourceQueries;
    private long candidateQueries;
    private long targetCandidateQueries;
    private long targetValidationQueries;
    private long fullMaterialSamples;
    private long violationCount;
    private long nativeClimateTieCount;
    private long nextProgress;
    private ChunkPos pendingPosition;
    private CompletableFuture<ChunkResult<ChunkAccess>> pending;
    private boolean finished;
    private String failureMessage;

    private Audit(ServerLevel level, NoiseBasedChunkGenerator generator, Path output) {
      if (fullBudget < 1 || fullBudget > 512 || timeoutSeconds < 1 || timeoutSeconds > 7200)
        throw new IllegalArgumentException("Invalid biome coverage FULL budget or timeout");
      this.level = level;
      this.generator = generator;
      this.source = generator.getBiomeSource();
      this.randomState = level.getChunkSource().randomState();
      this.output = output;
      random = RandomSource.create(level.getSeed() ^ 0x4F3B9A629C71D025L);
      for (Holder<Biome> biome : source.possibleBiomes()) {
        String id = biomeId(biome);
        possible.add(id);
        coverage.put(id, new Coverage(id));
      }
      String configuredTargets =
          System.getProperty("aeroscapes.regression.biomeCoverageTargets", "").trim();
      if (!configuredTargets.isEmpty())
        for (String value : configuredTargets.split(",", -1)) {
          String id = value.trim();
          ResourceLocation parsed = ResourceLocation.tryParse(id);
          if (parsed == null || !id.equals(parsed.toString()) || !possible.contains(id))
            throw new IllegalArgumentException(
                "Invalid or unavailable biome coverage target " + id);
          targets.add(id);
        }
      if (focus && targets.isEmpty())
        throw new IllegalArgumentException(
            "Biome coverage focus requires nonempty biomeCoverageTargets");
      candidateSampler = focus ? focusSampler() : null;
    }

    private Climate.Sampler focusSampler() {
      Climate.Sampler caller = randomState.sampler();
      IslandBiomeSampler callerContext = (IslandBiomeSampler) (Object) caller;
      if (callerContext.aeroscapesReimagine$terrainMask() == null
          || callerContext.aeroscapesReimagine$deferTerrainMask())
        throw new IllegalStateException("Focus needs the normal initialized caller sampler");
      Climate.Sampler copy =
          new Climate.Sampler(
              caller.temperature(),
              caller.humidity(),
              caller.continentalness(),
              caller.erosion(),
              caller.depth(),
              caller.weirdness(),
              caller.spawnTarget());
      IslandBiomeSampler copyContext = (IslandBiomeSampler) (Object) copy;
      copyContext.aeroscapesReimagine$terrainMask(callerContext.aeroscapesReimagine$terrainMask());
      copyContext.aeroscapesReimagine$deferTerrainMask(true);
      if (copy == caller || callerContext.aeroscapesReimagine$deferTerrainMask())
        throw new IllegalStateException("Focus changed the caller sampler");
      return copy;
    }

    private void verifyFocusSampler() {
      if (!focusSamplerPreserved())
        throw new IllegalStateException("Focus sampler isolation was lost");
    }

    private boolean focusSamplerPreserved() {
      if (!focus) return true;
      IslandBiomeSampler caller = (IslandBiomeSampler) (Object) randomState.sampler();
      IslandBiomeSampler copy = (IslandBiomeSampler) (Object) candidateSampler;
      return !caller.aeroscapesReimagine$deferTerrainMask()
          && copy.aeroscapesReimagine$deferTerrainMask()
          && caller.aeroscapesReimagine$terrainMask() == copy.aeroscapesReimagine$terrainMask();
    }

    private void tick(ServerTickEvent.Post event) {
      if (finished || event.getServer() != level.getServer()) return;
      try {
        advance();
      } catch (RuntimeException error) {
        releasePending();
        if (!finished) {
          finished = true;
          failureMessage = error.toString();
          write("error");
        }
        throw error;
      }
    }

    private void advance() {
      long now = System.nanoTime();
      if (now - started > timeoutSeconds * 1_000_000_000L) {
        finished = true;
        releasePending();
        write("deadline");
        throw new IllegalStateException("Biome coverage exceeded " + timeoutSeconds + " seconds");
      }
      if (now >= nextProgress) {
        nextProgress = now + 15_000_000_000L;
        LOGGER.info(
            "Biome coverage progress: seed={} phase={} sourceQueries={} selected={} raw={} FULL={}/{} confirmed={}",
            level.getSeed(),
            phase,
            sourceQueries,
            coverage.values().stream().filter(entry -> entry.firstSource != null).count(),
            rawColumns,
            fullChunks.size(),
            fullBudget,
            confirmed());
      }
      long sliceEnd = now + 25_000_000L;
      do {
        if (phase == 0) gridColumn();
        else if (phase == 1) randomColumn();
        else if (phase == 2) rawColumn();
        else {
          fullChunk();
          return;
        }
      } while (System.nanoTime() < sliceEnd);
    }

    private void gridColumn() {
      int width = GRID_RADIUS * 2 / gridStep + 1;
      if (cursor >= width * width) {
        phase = 1;
        cursor = 0;
        return;
      }
      int x = cursor % width * gridStep - GRID_RADIUS + 2;
      int z = cursor / width * gridStep - GRID_RADIUS + 2;
      cursor++;
      gridColumns++;
      sourceColumn(x, z);
    }

    private void randomColumn() {
      if (cursor++ >= RANDOM_COLUMNS) {
        prepareRaw();
        return;
      }
      int x = (random.nextInt(RANDOM_RADIUS / 2 + 1) - RANDOM_RADIUS / 4) * 4 + 2;
      int z = (random.nextInt(RANDOM_RADIUS / 2 + 1) - RANDOM_RADIUS / 4) * 4 + 2;
      randomColumns++;
      sourceColumn(x, z);
    }

    private void sourceColumn(int x, int z) {
      Column column = new Column(x, z);
      for (int y = level.getMinBuildHeight() + 16;
          y < level.getMaxBuildHeight();
          y += sourceHeightStep) {
        if (focus) {
          candidateQueries++;
          String candidate =
              biomeId(source.getNoiseBiome(x >> 2, y >> 2, z >> 2, candidateSampler));
          if (!targets.contains(candidate) && !targets.contains(VOID)) continue;
          if (targets.contains(candidate)) targetCandidateQueries++;
          targetValidationQueries++;
        }
        String id = sourceBiome(x, y, z);
        if (focus && !targets.contains(id)) continue;
        Coverage entry = coverage.computeIfAbsent(id, Coverage::new);
        entry.sourceHits++;
        BlockPos position = new BlockPos(x, y, z);
        if (entry.firstSource == null) entry.firstSource = position;
        if (entry.candidates.containsKey(column)) continue;
        if (entry.candidates.size() < CANDIDATES_PER_BIOME) {
          entry.candidates.put(column, position);
          continue;
        }
        Column worst =
            entry.candidates.keySet().stream()
                .max((first, second) -> Long.compareUnsigned(rank(first, id), rank(second, id)))
                .orElseThrow();
        if (Long.compareUnsigned(rank(column, id), rank(worst, id)) < 0) {
          entry.candidates.remove(worst);
          entry.candidates.put(column, position);
        }
      }
      verifyFocusSampler();
    }

    private long rank(Column column, String id) {
      long value =
          BlockPos.asLong(column.x(), id.hashCode(), column.z())
              ^ level.getSeed()
              ^ 0x706A1F2D63B94085L;
      value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
      value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
      return value ^ (value >>> 31);
    }

    private void prepareRaw() {
      List<Coverage> entries =
          coverage.values().stream()
              .filter(entry -> entry.sourceHits > 0)
              .sorted(
                  Comparator.<Coverage>comparingInt(entry -> targets.contains(entry.id) ? 0 : 1)
                      .thenComparingLong(entry -> entry.sourceHits))
              .toList();
      Set<Column> columns = new LinkedHashSet<>();
      for (int candidate = 0; candidate < CANDIDATES_PER_BIOME; candidate++)
        for (Coverage entry : entries) {
          var choices = new ArrayList<>(entry.candidates.keySet());
          if (candidate < choices.size()) columns.add(choices.get(candidate));
        }
      rawPlan = List.copyOf(columns);
      cursor = 0;
      phase = 2;
    }

    private void rawColumn() {
      if (cursor >= rawPlan.size() || rawColumns >= RAW_COLUMN_BUDGET) {
        phase = 3;
        return;
      }
      Column column = rawPlan.get(cursor++);
      NoiseColumn blocks = baseColumn(column.x(), column.z());
      rawColumns++;
      for (int y = level.getMinBuildHeight() + 2; y < level.getMaxBuildHeight(); y += 4) {
        boolean material = naturalMaterial(blocks.getBlock(y));
        String id = sourceBiome(column.x(), y, column.z());
        if (!material && !id.equals(VOID)) continue;
        if (id.equals(VOID) && material) {
          violation("source_void_on_raw_material", new BlockPos(column.x(), y, column.z()), id);
          continue;
        }
        if (id.equals(VOID) && !blocks.getBlock(y).isAir()) continue;
        Coverage entry = coverage.computeIfAbsent(id, Coverage::new);
        if (entry.firstSource == null) entry.firstSource = new BlockPos(column.x(), y, column.z());
        if (entry.raw.size() < WITNESSES_PER_BIOME)
          entry.raw.putIfAbsent(column.chunk(), new BlockPos(column.x(), y, column.z()));
      }
    }

    private void fullChunk() {
      if (pending != null) {
        if (!pending.isDone()) return;
        ChunkAccess chunk =
            pending.join().orElseThrow(() -> new IllegalStateException("FULL chunk unavailable"));
        fullChunks.add(pendingPosition);
        pending = null;
        try {
          inspectFull(chunk);
        } finally {
          releasePending();
        }
        return;
      }
      ChunkPos next = nextChunk();
      if (next == null || fullChunks.size() >= fullBudget) {
        if (!focus && (fullChunks.isEmpty() || fullMaterialSamples == 0))
          violation(
              "missing_FULL_natural_positive", BlockPos.ZERO, "No natural FULL material found");
        finished = true;
        write(violationCount == 0 ? "complete" : "violations");
        if (violationCount > 0)
          throw new IllegalStateException("Biome coverage found " + violationCount + " violations");
        LOGGER.info(
            "Biome coverage regression complete: seed={} possible={} selected={} natural={} FULL={} confirmed={} output={}",
            level.getSeed(),
            possible.size(),
            coverage.values().stream().filter(entry -> entry.firstSource != null).count(),
            coverage.values().stream().filter(entry -> !entry.raw.isEmpty()).count(),
            fullChunks.size(),
            confirmed(),
            output);
        return;
      }
      pendingPosition = next;
      level.getChunkSource().addRegionTicket(COVERAGE_TICKET, next, 0, next);
      // The native API managed-blocks on the server thread, but schedules safely from a worker.
      pending =
          CompletableFuture.supplyAsync(
                  () ->
                      level.getChunkSource().getChunkFuture(next.x, next.z, ChunkStatus.FULL, true),
                  Util.backgroundExecutor())
              .thenCompose(future -> future);
    }

    private void releasePending() {
      if (pendingPosition != null) {
        level
            .getChunkSource()
            .removeRegionTicket(COVERAGE_TICKET, pendingPosition, 0, pendingPosition);
        pendingPosition = null;
      }
      if (pending != null) {
        pending.cancel(false);
        pending = null;
      }
    }

    private ChunkPos nextChunk() {
      Map<ChunkPos, Integer> scores = new LinkedHashMap<>();
      for (Coverage entry : coverage.values()) {
        if (entry.full != null) continue;
        int weight = targets.contains(entry.id) ? 100 : ORE_BIOMES.contains(entry.id) ? 2 : 1;
        for (ChunkPos position : entry.raw.keySet())
          if (!fullChunks.contains(position)) scores.merge(position, weight, Integer::sum);
      }
      return scores.entrySet().stream()
          .max(
              Comparator.<Map.Entry<ChunkPos, Integer>>comparingInt(Map.Entry::getValue)
                  .thenComparing(
                      (first, second) ->
                          Long.compareUnsigned(
                              fullRank(first.getKey()), fullRank(second.getKey()))))
          .map(Map.Entry::getKey)
          .orElse(null);
    }

    private long fullRank(ChunkPos position) {
      return rank(new Column(position.getMinBlockX(), position.getMinBlockZ()), "FULL");
    }

    private void inspectFull(ChunkAccess chunk) {
      Map<Column, NoiseColumn> columns = new HashMap<>();
      Set<String> chunkBiomes = new HashSet<>();
      int originX = chunk.getPos().getMinBlockX();
      int originZ = chunk.getPos().getMinBlockZ();
      for (int x = originX + 2; x < originX + 16; x += 4)
        for (int z = originZ + 2; z < originZ + 16; z += 4) {
          NoiseColumn raw = columns.computeIfAbsent(new Column(x, z), this::fullBaseColumn);
          for (int y = level.getMinBuildHeight() + 2; y < level.getMaxBuildHeight(); y += 4) {
            BlockPos position = new BlockPos(x, y, z);
            String actual = biomeId(chunk.getNoiseBiome(x >> 2, y >> 2, z >> 2));
            chunkBiomes.add(actual);
            BlockState block = chunk.getBlockState(position);
            if (block.isAir() && (caveBiome(actual) || actual.equals(VOID)))
              caveAirControl(chunk, raw, position, actual);
            boolean material = naturalMaterial(raw.getBlock(y)) && naturalMaterial(block);
            String selected = null;
            if (material) {
              fullMaterialSamples++;
              selected = sourceBiome(x, y, z);
              if (actual.equals(VOID) || selected.equals(VOID))
                violation("void_on_FULL_natural_material", position, selected + "/" + actual);
            }
            Coverage entry = coverage.computeIfAbsent(actual, Coverage::new);
            if (entry.full != null) continue;
            boolean emptyVoid = actual.equals(VOID) && raw.getBlock(y).isAir() && block.isAir();
            if (!material && !emptyVoid) continue;
            if (selected == null) selected = sourceBiome(x, y, z);
            if (!selected.equals(actual)) {
              JsonObject tie =
                  BiomeMismatchRegression.nativeTie(level, source, position, selected, actual);
              if (tie == null) {
                violation("source_FULL_biome_mismatch", position, selected + "/" + actual);
              } else {
                nativeClimateTieCount++;
                if (nativeClimateTies.size() < 32) nativeClimateTies.add(tie);
              }
              continue;
            }
            if (entry.firstSource == null) entry.firstSource = position;
            entry.full = witness(position, block, raw, emptyVoid);
            entry.raw.putIfAbsent(chunk.getPos(), position);
          }
        }
      if (farControls.size() < 4) farControl(chunk);
      if (chunkBiomes.stream().anyMatch(ORE_BIOMES::contains) && oreSamples.size() < 256)
        ores(chunk);
    }

    private void caveAirControl(
        ChunkAccess chunk, NoiseColumn raw, BlockPos center, String actual) {
      if (caveAirControls.size() >= 4 || caveAirCandidates >= 32) return;
      BlockPos floor = caveSupport(chunk, raw, center, -1);
      BlockPos roof = caveSupport(chunk, raw, center, 1);
      if (floor == null || roof == null) return;
      if (!caveBiome(actual)
          && !caveBiome(
              biomeId(chunk.getNoiseBiome(floor.getX() >> 2, floor.getY() >> 2, floor.getZ() >> 2)))
          && !caveBiome(
              biomeId(chunk.getNoiseBiome(roof.getX() >> 2, roof.getY() >> 2, roof.getZ() >> 2))))
        return;
      caveAirCandidates++;
      int connection = caveSupportConnection(chunk, floor, roof);
      if (connection == 0) return;
      String selected = sourceBiome(center.getX(), center.getY(), center.getZ());
      JsonObject control = position(center);
      control.addProperty("source_biome", selected);
      control.addProperty("FULL_biome", actual);
      control.addProperty("raw_center", blockId(raw.getBlock(center.getY())));
      control.addProperty("floor_block", blockId(chunk.getBlockState(floor)));
      control.addProperty("roof_block", blockId(chunk.getBlockState(roof)));
      control.add("floor", position(floor));
      control.add("roof", position(roof));
      control.addProperty("FULL_connection_visited_voxels", connection);
      control.addProperty("raw_natural_support_endpoints", true);
      control.addProperty(
          "minimum_vertical_clearance",
          Math.min(center.getY() - floor.getY(), roof.getY() - center.getY()));
      caveAirControls.add(control);
      if (selected.equals(VOID) || actual.equals(VOID) || !selected.equals(actual))
        violation("cave_air_lost_biome", center, selected + "/" + actual);
    }

    private BlockPos caveSupport(
        ChunkAccess chunk, NoiseColumn raw, BlockPos center, int direction) {
      for (int distance = 1; distance <= 64; distance++) {
        int y = center.getY() + distance * direction;
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return null;
        BlockPos position = new BlockPos(center.getX(), y, center.getZ());
        if (naturalSolid(raw.getBlock(y)) && naturalSolid(chunk.getBlockState(position)))
          return position;
      }
      return null;
    }

    private int caveSupportConnection(ChunkAccess chunk, BlockPos floor, BlockPos roof) {
      ArrayDeque<BlockPos> pendingNodes = new ArrayDeque<>();
      Set<BlockPos> visited = new HashSet<>();
      pendingNodes.add(floor);
      visited.add(floor);
      int minimumX = chunk.getPos().getMinBlockX();
      int minimumZ = chunk.getPos().getMinBlockZ();
      while (!pendingNodes.isEmpty()) {
        BlockPos current = pendingNodes.removeFirst();
        if (current.equals(roof)) return visited.size();
        for (Direction direction : Direction.values()) {
          BlockPos neighbor = current.relative(direction);
          if (neighbor.getX() < minimumX
              || neighbor.getX() > minimumX + 15
              || neighbor.getZ() < minimumZ
              || neighbor.getZ() > minimumZ + 15
              || neighbor.getY() < floor.getY()
              || neighbor.getY() > roof.getY()
              || visited.contains(neighbor)) continue;
          if (visited.size() >= 4096) return 0;
          visited.add(neighbor);
          if (!naturalSolid(chunk.getBlockState(neighbor))) continue;
          pendingNodes.addLast(neighbor);
        }
      }
      return 0;
    }

    private NoiseColumn fullBaseColumn(Column column) {
      fullRawColumns++;
      return baseColumn(column.x(), column.z());
    }

    private JsonObject witness(
        BlockPos position, BlockState block, NoiseColumn raw, boolean emptyVoid) {
      JsonObject result = position(position);
      result.addProperty("block", blockId(block));
      result.addProperty("environment", emptyVoid ? "empty_space" : "natural_solid_or_water");
      result.addProperty("raw_block", blockId(raw.getBlock(position.getY())));
      int nearbyMaterial = 0;
      for (int offset = -2; offset <= 2; offset++)
        if (naturalMaterial(raw.getBlock(position.getY() + offset))) nearbyMaterial++;
      result.addProperty("raw_material_in_vertical_2_block_buffer", nearbyMaterial);
      return result;
    }

    private void farControl(ChunkAccess chunk) {
      int x = chunk.getPos().getMiddleBlockX();
      int z = chunk.getPos().getMiddleBlockZ();
      int y = Math.floorDiv(level.getMaxBuildHeight() - 24, 8) * 8;
      BlockPos center = new BlockPos(x, y, z);
      if (!chunk.getBlockState(center).isAir()) return;
      for (int dx = -12; dx <= 12; dx += 4)
        for (int dz = -12; dz <= 12; dz += 4) {
          NoiseColumn column = baseColumn(x + dx, z + dz);
          farRawColumns++;
          for (int dy = -16; dy <= 16; dy += 8) if (!column.getBlock(y + dy).isAir()) return;
        }
      String selected = sourceBiome(x, y, z);
      String actual = biomeId(chunk.getNoiseBiome(x >> 2, y >> 2, z >> 2));
      JsonObject control = position(center);
      control.addProperty("source_biome", selected);
      control.addProperty("FULL_biome", actual);
      control.addProperty("raw_air_nodes", 245);
      control.addProperty("void_expected", possible.contains(VOID));
      farControls.add(control);
      if (possible.contains(VOID) && (!selected.equals(VOID) || !actual.equals(VOID)))
        violation("nonvoid_far_from_sampled_natural_terrain", center, selected + "/" + actual);
    }

    private void ores(ChunkAccess chunk) {
      int found = 0;
      Map<String, Integer> examples = new HashMap<>();
      int minX = chunk.getPos().getMinBlockX();
      int minZ = chunk.getPos().getMinBlockZ();
      for (int y = level.getMinBuildHeight() + 1; y < level.getMaxBuildHeight() - 1; y++)
        for (int x = minX + 1; x < minX + 15; x++)
          for (int z = minZ + 1; z < minZ + 15; z++) {
            BlockPos position = new BlockPos(x, y, z);
            BlockState block = chunk.getBlockState(position);
            if (!blockId(block).endsWith("_ore")) continue;
            String biome = biomeId(chunk.getNoiseBiome(x >> 2, y >> 2, z >> 2));
            if (!ORE_BIOMES.contains(biome)) continue;
            Set<String> hostIds = new TreeSet<>();
            for (Direction direction : Direction.values())
              hostIds.add(blockId(chunk.getBlockState(position.relative(direction))));
            String example = blockId(block) + hostIds;
            int previous = examples.getOrDefault(example, 0);
            if (previous >= 4) continue;
            examples.put(example, previous + 1);
            JsonObject sample = position(position);
            sample.addProperty("ore", blockId(block));
            sample.addProperty("biome", biome);
            JsonObject neighbors = new JsonObject();
            for (Direction direction : Direction.values())
              neighbors.addProperty(
                  direction.getName(), blockId(chunk.getBlockState(position.relative(direction))));
            sample.add("neighbors", neighbors);
            oreSamples.add(sample);
            if (++found >= 32 || oreSamples.size() >= 256) return;
          }
    }

    private NoiseColumn baseColumn(int x, int z) {
      return generator.getBaseColumn(x, z, level, randomState);
    }

    private String sourceBiome(int x, int y, int z) {
      sourceQueries++;
      return biomeId(source.getNoiseBiome(x >> 2, y >> 2, z >> 2, randomState.sampler()));
    }

    private long confirmed() {
      return coverage.values().stream().filter(entry -> entry.full != null).count();
    }

    private void violation(String kind, BlockPos position, String detail) {
      violationCount++;
      if (violations.size() >= 32) return;
      JsonObject evidence = position(position);
      evidence.addProperty("kind", kind);
      evidence.addProperty("detail", detail);
      violations.add(evidence);
    }

    private void write(String status) {
      JsonObject report = new JsonObject();
      report.addProperty("format", 1);
      report.addProperty("label", label);
      if (focus) report.addProperty("mode", "focus_supplemental");
      report.addProperty("seed", level.getSeed());
      report.addProperty("status", status);
      report.addProperty("phase", phase);
      if (failureMessage != null) report.addProperty("failure", failureMessage);
      report.addProperty("elapsed_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
      report.addProperty("biome_source", source.getClass().getName());
      report.addProperty("generator", generator.getClass().getName());
      report.addProperty(
          "generator_settings",
          generator
              .generatorSettings()
              .unwrapKey()
              .map(key -> key.location().toString())
              .orElse("inline"));
      report.addProperty("sea_level", generator.generatorSettings().value().seaLevel());
      report.addProperty("min_y", level.getMinBuildHeight());
      report.addProperty("max_y_exclusive", level.getMaxBuildHeight());
      JsonObject sampling = new JsonObject();
      JsonArray targetIds = new JsonArray();
      targets.forEach(targetIds::add);
      sampling.add("targets", targetIds);
      sampling.addProperty("grid_radius", GRID_RADIUS);
      sampling.addProperty("grid_step", gridStep);
      sampling.addProperty("horizontal_quart_center_offset", 2);
      sampling.addProperty("grid_columns_completed", gridColumns);
      sampling.addProperty("random_radius", RANDOM_RADIUS);
      sampling.addProperty("random_columns", RANDOM_COLUMNS);
      sampling.addProperty("random_columns_completed", randomColumns);
      sampling.addProperty("source_height_step", sourceHeightStep);
      if (focus) {
        sampling.addProperty("source_height_start", level.getMinBuildHeight() + 16);
        sampling.addProperty("source_height_max_exclusive", level.getMaxBuildHeight());
        sampling.addProperty("candidate_source_queries", candidateQueries);
        sampling.addProperty("target_candidate_queries", targetCandidateQueries);
        sampling.addProperty("target_mask_validation_queries", targetValidationQueries);
        sampling.addProperty("caller_sampler_mask_preserved", focusSamplerPreserved());
        sampling.addProperty(
            "source_scan_scope",
            "Deferred-mask outer-source candidates; only target candidates are rechecked with the"
                + " normal actual source and mask. Void targets require normal validation of every"
                + " candidate. Source hits of other IDs are not audited; raw/FULL controls remain"
                + " normal, and incidental positive witnesses are retained. An empty targeted"
                + " result remains unconfirmed and does not require a FULL natural positive.");
      }
      sampling.addProperty("candidate_columns_per_biome", CANDIDATES_PER_BIOME);
      sampling.addProperty("raw_column_budget", RAW_COLUMN_BUDGET);
      sampling.addProperty("raw_candidate_columns", rawPlan.size());
      sampling.addProperty("raw_columns", rawColumns);
      sampling.addProperty("FULL_requested_center_budget", fullBudget);
      sampling.addProperty(
          "FULL_selection",
          "Greedy unconfirmed biome witnesses; target weight 100, ore-biome weight 2, other 1;"
              + " seed/chunk hash breaks ties");
      sampling.addProperty("FULL_requested_centers", fullChunks.size());
      sampling.addProperty("FULL_raw_columns", fullRawColumns);
      sampling.addProperty("far_control_raw_columns", farRawColumns);
      sampling.addProperty("cave_air_candidates_examined", caveAirCandidates);
      sampling.addProperty("cave_air_candidate_budget", 32);
      sampling.addProperty("cave_support_connection_node_budget", 4096);
      sampling.addProperty("source_queries", sourceQueries);
      sampling.addProperty("FULL_natural_material_samples", fullMaterialSamples);
      sampling.addProperty("deadline_seconds", timeoutSeconds);
      sampling.addProperty("source_scan_seed_salt", "4f3b9a629c71d025");
      report.add("sampling", sampling);
      JsonArray entries = new JsonArray();
      for (Coverage entry : coverage.values()) {
        JsonObject result = new JsonObject();
        result.addProperty("id", entry.id);
        result.addProperty("possible", possible.contains(entry.id));
        if (!focus || targets.contains(entry.id))
          result.addProperty("source_scan_hits", entry.sourceHits);
        if (focus) result.addProperty("source_scan_audited", targets.contains(entry.id));
        result.addProperty(
            "status",
            entry.full != null
                ? "FULL_confirmed"
                : !entry.raw.isEmpty()
                    ? "raw_witness_only"
                    : entry.firstSource != null
                        ? "source_only"
                        : focus && !targets.contains(entry.id)
                            ? "not_audited_in_focus_mode"
                            : "not_selected_in_finite_scan");
        if (entry.firstSource != null) result.add("first_source", position(entry.firstSource));
        JsonArray raw = new JsonArray();
        entry.raw.values().forEach(value -> raw.add(position(value)));
        result.add("raw_witnesses", raw);
        if (entry.full != null) result.add("FULL_witness", entry.full);
        entries.add(result);
      }
      report.add("coverage", entries);
      JsonArray excluded = new JsonArray();
      level.registryAccess().registryOrThrow(Registries.BIOME).keySet().stream()
          .map(Object::toString)
          .filter(id -> !possible.contains(id) && !coverage.containsKey(id))
          .sorted()
          .forEach(excluded::add);
      report.add("registered_not_possible", excluded);
      JsonArray centers = new JsonArray();
      fullChunks.forEach(
          chunk -> {
            JsonObject center = new JsonObject();
            center.addProperty("x", chunk.x);
            center.addProperty("z", chunk.z);
            centers.add(center);
          });
      report.add("FULL_centers", centers);
      report.addProperty("violation_count", violationCount);
      report.add("violation_examples", violations);
      report.addProperty("native_climate_tie_count", nativeClimateTieCount);
      report.add("native_climate_tie_examples", nativeClimateTies);
      report.add("far_empty_controls", farControls);
      report.add("cave_air_controls", caveAirControls);
      report.add("FULL_ore_adjacency_samples", oreSamples);
      report.addProperty(
          "limits",
          "Finite source and natural FULL sampling does not prove global absence, resource balance,"
              + " visuals or survival reachability. FULL center budget excludes native generation"
              + " dependencies. Ore observations do not identify the feature that placed them."
              + " Cave-air controls require a local FULL solid connection and raw natural"
              + " roof/floor endpoints, without proving every connecting block natural."
              + " No local control does not prove absence of caves."
              + " Proven native equal-distance boundaries are recorded separately and never used"
              + " as FULL witnesses."
              + " Ordinary source/FULL label comparisons serve witness discovery; already confirmed"
              + " biome IDs skip later label comparisons. Material void checks remain unconditional."
              + " Compare host seeds through separate actual world launches.");
      try {
        Files.createDirectories(output.getParent());
        Files.writeString(
            output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      } catch (IOException error) {
        throw new IllegalStateException("Cannot write biome coverage " + output, error);
      }
    }
  }

  private static String biomeId(Holder<Biome> biome) {
    return biome.unwrapKey().orElseThrow().location().toString();
  }

  private static String blockId(BlockState state) {
    return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
  }

  static boolean naturalMaterial(BlockState state) {
    return state.is(Blocks.WATER)
        || state.getFluidState().isEmpty()
            && Block.isFaceFull(
                state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP);
  }

  private static boolean naturalSolid(BlockState state) {
    return state.getFluidState().isEmpty() && naturalMaterial(state);
  }

  private static boolean caveBiome(String id) {
    return id.startsWith("terralith:cave/")
        || id.equals("minecraft:lush_caves")
        || id.equals("minecraft:dripstone_caves")
        || id.equals("minecraft:deep_dark")
        || id.equals("galosphere:crystal_canyons")
        || id.equals("galosphere:lichen_caves")
        || id.equals("galosphere:pink_salt_caves");
  }

  private static JsonObject position(BlockPos position) {
    JsonObject result = new JsonObject();
    result.addProperty("x", position.getX());
    result.addProperty("y", position.getY());
    result.addProperty("z", position.getZ());
    return result;
  }
}
