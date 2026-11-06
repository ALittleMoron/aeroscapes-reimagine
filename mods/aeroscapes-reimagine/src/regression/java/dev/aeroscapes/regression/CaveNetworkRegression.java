package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandLayout;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.GenerationStep;

/** Natural interior paths with a floor, two-block headroom and bounded player steps. */
public final class CaveNetworkRegression {
  private static volatile Capture active;

  private record Capture(
      net.minecraft.world.level.levelgen.RandomState state,
      net.minecraft.world.level.levelgen.structure.BoundingBox bounds,
      java.util.Set<Long> writes,
      java.util.concurrent.ConcurrentMap<ChunkPos, RawChunk> raw,
      java.util.Set<Start> starts) {}

  private record RawChunk(java.util.List<java.util.List<Body>> columns) {
    private java.util.List<Body> at(int x, int z) {
      return columns.get((z & 15) * 16 + (x & 15));
    }
  }

  private record Start(
      ChunkPos source, String engine, dev.aeroscapes.worldgen.IslandCaveHosts.Range layer) {}

  private record Host(
      BlockPos point,
      IslandLayout.SmallIsland small,
      IslandLayout.LargeIsland large,
      int layer,
      dev.aeroscapes.worldgen.IslandCaveHosts.Range body) {
    private Host(
        BlockPos point, IslandLayout.SmallIsland small, IslandLayout.LargeIsland large, int layer) {
      this(point, small, large, layer, null);
    }

    private IslandLayout.VerticalShape shape(long seed, int x, int z) {
      return small != null ? small.shape(seed, x, z) : large != null ? large.shape(x, z) : null;
    }
  }

  public static boolean tracking(
      net.minecraft.world.level.levelgen.carver.CarvingContext context, BlockPos position) {
    Capture capture = active;
    return capture != null
        && capture.state() == context.randomState()
        && capture.bounds().isInside(position);
  }

  public static void carved(
      net.minecraft.world.level.levelgen.carver.CarvingContext context, BlockPos position) {
    Capture capture = active;
    if (capture != null
        && capture.state() == context.randomState()
        && capture.bounds().isInside(position)) capture.writes().add(position.asLong());
  }

  public static void beforeCarvers(
      ServerLevel level, net.minecraft.world.level.chunk.ChunkAccess chunk) {
    Capture capture = active;
    if (capture == null
        || capture.state() != level.getChunkSource().randomState()
        || !inside(capture, chunk.getPos())
        || capture.raw().containsKey(chunk.getPos())) return;
    var columns = new java.util.ArrayList<java.util.List<Body>>(256);
    var point = new BlockPos.MutableBlockPos();
    for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++)
      for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
        var bodies = new java.util.ArrayList<Body>();
        int start = Integer.MIN_VALUE;
        for (int y = chunk.getMinBuildHeight(); y <= chunk.getMaxBuildHeight(); y++) {
          point.set(x, y, z);
          var block = chunk.getBlockState(point);
          boolean solid =
              y < chunk.getMaxBuildHeight()
                  && block.getFluidState().isEmpty()
                  && block.isFaceSturdy(chunk, point, Direction.UP);
          if (solid && start == Integer.MIN_VALUE) start = y;
          if (!solid && start != Integer.MIN_VALUE) {
            bodies.add(new Body(start, y));
            start = Integer.MIN_VALUE;
          }
        }
        columns.add(java.util.List.copyOf(bodies));
      }
    capture.raw().putIfAbsent(chunk.getPos(), new RawChunk(java.util.List.copyOf(columns)));
  }

  public static void started(
      net.minecraft.world.level.levelgen.carver.CarvingContext context,
      net.minecraft.world.level.chunk.ChunkAccess target,
      ChunkPos source,
      String engine) {
    Capture capture = active;
    if (capture == null
        || capture.state() != context.randomState()
        || !inside(capture, target.getPos())) return;
    var layer =
        ((dev.aeroscapes.worldgen.IslandCarvingContext) (Object) context).aeroscapes$layer();
    capture.starts().add(new Start(source, engine, layer));
  }

  private static boolean inside(Capture capture, ChunkPos chunk) {
    return chunk.getMaxBlockX() >= capture.bounds().minX()
        && chunk.getMinBlockX() <= capture.bounds().maxX()
        && chunk.getMaxBlockZ() >= capture.bounds().minZ()
        && chunk.getMinBlockZ() <= capture.bounds().maxZ();
  }

  private CaveNetworkRegression() {}

  public static void verify(ServerLevel level) {
    if (!level.getServer().isDedicatedServer()
        || level.getServer().getPlayerList().getPlayerCount() != 0)
      throw new IllegalStateException("Cave fixture requires an empty disposable dedicated server");
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    if (terrain == null) throw new IllegalStateException("Missing island terrain");
    hostThicknessControls();
    String kind = System.getProperty("aeroscapes.regression.caveKind", "high");
    String biome = System.getProperty("aeroscapes.regression.caveBiome");
    String witness = System.getProperty("aeroscapes.regression.caveWitness");
    if (witness != null) {
      if (biome == null) throw new IllegalArgumentException("A named cave witness needs caveBiome");
      String[] coordinates = witness.split(",", -1);
      if (coordinates.length != 3) throw new IllegalArgumentException("Expected caveWitness=x,y,z");
      BlockPos point =
          new BlockPos(
              Integer.parseInt(coordinates[0]),
              Integer.parseInt(coordinates[1]),
              Integer.parseInt(coordinates[2]));
      Body body =
          nativeColumnBodies(level, point.getX(), point.getZ()).stream()
              .filter(range -> range.minimum() <= point.getY() && point.getY() < range.maximum())
              .filter(range -> range.qualifies(kind))
              .filter(range -> sourceSurfaceBiome(level, point.getX(), point.getZ(), range, biome))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Named cave witness lost its natural body/biome: " + point));
      var selected =
          new Host(
              point,
              null,
              null,
              -1,
              new dev.aeroscapes.worldgen.IslandCaveHosts.Range(body.minimum(), body.maximum()));
      if (!verifyHost(level, terrain, selected, kind, biome))
        throw new IllegalStateException(
            "Named cave witness lost its traversable network: " + point);
      LogUtils.getLogger()
          .info(
              "Cave network regression passed: seed={} kind={} biome={} namedWitness={}",
              level.getSeed(),
              kind,
              biome,
              point);
      return;
    }
    if (biome != null) {
      var cohort = new BiomeCohort(level, terrain, biome, kind);
      net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cohort.listener);
      return;
    }
    var hosts =
        kind.equals("high")
            ? java.util.List.of(new Host(findHost(level, terrain), null, null, -1))
            : findAddedHosts(terrain, kind);
    int passed = 0;
    for (var selected : hosts) if (verifyHost(level, terrain, selected, kind, null)) passed++;
    int required = (hosts.size() * 3 + 3) / 4;
    if (passed < required)
      throw new IllegalStateException(
          "Too few traversable "
              + kind
              + " island caves: "
              + passed
              + "/"
              + hosts.size()
              + "; require "
              + required);
    LogUtils.getLogger()
        .info(
            "Cave network regression passed: seed={} kind={} traversableIslands={}/{}",
            level.getSeed(),
            kind,
            passed,
            hosts.size());
  }

  @SuppressWarnings("deprecation") // Native per-source carver selection cache.
  private static boolean verifyHost(
      ServerLevel level, IslandTerrain terrain, Host selected, String kind, String biome) {
    BlockPos host = selected.point();
    LogUtils.getLogger()
        .info(
            "Cave selected host: kind={} point={} layer={} shape={}",
            kind,
            host,
            selected.layer(),
            selected.shape(terrain.layout().seed(), host.getX(), host.getZ()));
    int minimumSpan =
        kind.equals("small") || kind.equals("thin") ? 16 : kind.equals("medium") ? 24 : 40;
    int bottom = Math.max(-112, host.getY() - 40);
    for (int x = (host.getX() - 32) >> 4; x <= (host.getX() + 32) >> 4; x++) {
      for (int z = (host.getZ() - 32) >> 4; z <= (host.getZ() + 32) >> 4; z++) {
        var existing = level.getChunkSource().getChunk(x, z, ChunkStatus.EMPTY, false);
        if (existing != null && existing.getPersistedStatus().isOrAfter(ChunkStatus.CARVERS))
          throw new IllegalStateException(
              "Cave capture requires uncarved chunks at "
                  + host
                  + "; use an isolated fresh fixture");
        if (level
            .getChunkSource()
            .chunkMap
            .read(new ChunkPos(x, z))
            .orTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .join()
            .isPresent())
          throw new IllegalStateException(
              "Cave capture requires unsaved chunks at "
                  + host
                  + "; use an isolated fresh fixture");
      }
    }
    var capture =
        new Capture(
            level.getChunkSource().randomState(),
            new net.minecraft.world.level.levelgen.structure.BoundingBox(
                host.getX() - 32,
                bottom,
                host.getZ() - 32,
                host.getX() + 32,
                bottom + 82,
                host.getZ() + 32),
            java.util.concurrent.ConcurrentHashMap.newKeySet(),
            new java.util.concurrent.ConcurrentHashMap<>(),
            java.util.concurrent.ConcurrentHashMap.newKeySet());
    active = capture;
    try {
      var center =
          level
              .getChunkSource()
              .getChunk(host.getX() >> 4, host.getZ() >> 4, ChunkStatus.CARVERS, true);
      if (center == null) throw new IllegalStateException("Missing CARVERS source chunk");
      var carvers =
          center
              .carverBiome(
                  () -> {
                    throw new IllegalStateException("CARVERS chunk never selected carvers");
                  })
              .getCarvers(GenerationStep.Carving.AIR);
      if (!carvers.iterator().hasNext())
        throw new IllegalStateException(
            "Missing island carver donor: kind="
                + kind
                + " host="
                + host
                + " bands="
                + terrain
                    .caveHosts(
                        new ChunkPos(host),
                        (net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator)
                            level.getChunkSource().getGenerator(),
                        level.getChunkSource().randomState(),
                        level)
                    .ranges());
      LogUtils.getLogger()
          .info(
              "Cave source bands: host={} bands={}",
              host,
              terrain
                  .caveHosts(
                      new ChunkPos(host),
                      (net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator)
                          level.getChunkSource().getGenerator(),
                      level.getChunkSource().randomState(),
                      level)
                  .ranges());
      for (var carver : carvers)
        LogUtils.getLogger()
            .info(
                "Cave source donor: host={} id={} engine={} probability={}",
                host,
                carver.unwrapKey().orElseThrow().location(),
                carver.value().worldCarver().getClass().getName(),
                carver.value().config().probability);
      Map<ChunkPos, LevelChunk> chunks = new HashMap<>();
      int width = 65;
      int height = 81;
      int minX = host.getX() - 32;
      int minY = Math.max(-112, host.getY() - 40);
      int minZ = host.getZ() - 32;
      boolean[] walkable = new boolean[width * width * height];
      boolean[] cohort = new boolean[walkable.length];
      BlockPos.MutableBlockPos point = new BlockPos.MutableBlockPos();
      int airPairs = 0;
      int supported = 0;
      int interiorCells = 0;
      for (int z = 0; z < width; z++) {
        for (int x = 0; x < width; x++) {
          int blockX = minX + x;
          int blockZ = minZ + z;
          LevelChunk chunk =
              chunks.computeIfAbsent(
                  new ChunkPos(blockX >> 4, blockZ >> 4), key -> level.getChunk(key.x, key.z));
          var envelope = selected.shape(terrain.layout().seed(), blockX, blockZ);
          RawChunk raw = capture.raw().get(chunk.getPos());
          if (raw == null)
            throw new IllegalStateException(
                "Missing actual pre-CARVERS body snapshot " + chunk.getPos());
          var localBodies =
              raw.at(blockX, blockZ).stream()
                  .filter(
                      body ->
                          selected.body() == null
                              || body.minimum() < selected.body().maximum()
                                  && body.maximum() > selected.body().minimum())
                  .toList();
          var qualifying =
              biome == null
                  ? java.util.List.<Body>of()
                  : localBodies.stream()
                      .filter(body -> body.qualifies(kind))
                      .filter(body -> surfaceBiome(level, chunk, blockX, blockZ, body, biome))
                      .toList();
          for (int y = 0; y < height; y++) {
            int blockY = minY + y;
            if (localBodies.stream().noneMatch(body -> body.containsPlayer(blockY))) continue;
            point.set(blockX, blockY, blockZ);
            if (!chunk.getBlockState(point).isAir() || !chunk.getBlockState(point.above()).isAir())
              continue;
            airPairs++;
            if (!chunk.getBlockState(point.below()).isFaceSturdy(chunk, point.below(), Direction.UP)
                || !chunk.getBlockState(point.below()).getFluidState().isEmpty()) continue;
            supported++;
            if (envelope != null
                    && (envelope.density(blockY) < 0.1 || envelope.density(blockY + 6) < 0.1)
                || biome == null
                    && (terrain.biomeDensity(new SinglePointContext(blockX, blockY, blockZ)) < 0.15
                        || terrain.biomeDensity(new SinglePointContext(blockX, blockY + 6, blockZ))
                            < 0.15)) continue;
            interiorCells++;
            walkable[(z * width + x) * height + y] = true;
            cohort[(z * width + x) * height + y] =
                qualifying.stream().anyMatch(body -> body.containsPlayer(blockY));
          }
        }
      }
      int largest = 0;
      int longest = 0;
      LogUtils.getLogger()
          .info(
              "Cave FULL cells: host={} airPairs={} supported={} interior={} actualCarvedAir={} surfaceBiomeCohort={}",
              host,
              airPairs,
              supported,
              interiorCells,
              capture.writes().size(),
              biome);
      LogUtils.getLogger()
          .info(
              "Cave actual selected source layers: host={} all={} nearBody={}",
              host,
              capture.starts().size(),
              capture.starts().stream()
                  .filter(
                      start ->
                          start.layer() != null
                              && start.layer().maximum() >= host.getY() - 20
                              && start.layer().minimum() <= host.getY() + 20)
                  .sorted(
                      java.util.Comparator.comparingInt((Start start) -> start.source().x)
                          .thenComparingInt(start -> start.source().z)
                          .thenComparing(Start::engine))
                  .limit(64)
                  .toList());
      for (int start = 0; start < walkable.length; start++) {
        if (!walkable[start]) continue;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        walkable[start] = false;
        int count = 0;
        int carvedCells = 0;
        int cohortCells = 0;
        int cohortCarvedCells = 0;
        java.util.Set<Long> cohortColumns = new java.util.HashSet<>();
        int lowX = width;
        int highX = 0;
        int lowZ = width;
        int highZ = 0;
        while (!queue.isEmpty()) {
          int index = queue.removeFirst();
          int y = index % height;
          int x = index / height % width;
          int z = index / height / width;
          count++;
          BlockPos foot = new BlockPos(minX + x, minY + y, minZ + z);
          boolean carved =
              capture.writes().contains(foot.asLong())
                  || capture.writes().contains(foot.above().asLong());
          if (carved) carvedCells++;
          if (cohort[index]) {
            cohortCells++;
            if (carved) {
              cohortCarvedCells++;
              cohortColumns.add(new BlockPos(foot.getX(), 0, foot.getZ()).asLong());
            }
          }
          lowX = Math.min(lowX, x);
          highX = Math.max(highX, x);
          lowZ = Math.min(lowZ, z);
          highZ = Math.max(highZ, z);
          for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nextX = x + direction[0];
            int nextZ = z + direction[1];
            if (nextX < 0 || nextX >= width || nextZ < 0 || nextZ >= width) continue;
            for (int step = -3; step <= 1; step++) {
              int nextY = y + step;
              if (nextY < 0 || nextY >= height) continue;
              int next = (nextZ * width + nextX) * height + nextY;
              if (!walkable[next]) continue;
              LevelChunk from = chunks.get(new ChunkPos((minX + x) >> 4, (minZ + z) >> 4));
              LevelChunk to = chunks.get(new ChunkPos((minX + nextX) >> 4, (minZ + nextZ) >> 4));
              boolean clear = true;
              for (int sweep = Math.min(y, nextY); sweep <= Math.max(y, nextY) + 1; sweep++) {
                if (sweep >= y
                    && !from.getBlockState(new BlockPos(minX + x, minY + sweep, minZ + z)).isAir())
                  clear = false;
                if (sweep >= nextY
                    && !to.getBlockState(new BlockPos(minX + nextX, minY + sweep, minZ + nextZ))
                        .isAir()) clear = false;
              }
              if (!clear) continue;
              walkable[next] = false;
              queue.add(next);
            }
          }
        }
        int span = Math.max(highX - lowX, highZ - lowZ);
        if (span > longest) longest = span;
        largest = Math.max(largest, count);
        if (count >= 16)
          LogUtils.getLogger()
              .info(
                  "Cave FULL component: host={} cells={} horizontalSpan={} cohortCells={} cohortCarved={} cohortColumns={}",
                  host,
                  count,
                  span,
                  cohortCells,
                  cohortCarvedCells,
                  cohortColumns.size());
        if (span >= minimumSpan
            && count >= minimumSpan * 3
            && carvedCells >= 8
            && carvedCells * 3 >= count
            && (biome == null
                || cohortCells >= 8 && cohortCarvedCells >= 8 && cohortColumns.size() >= 4)) {
          LogUtils.getLogger()
              .info(
                  "Cave traversable witness: seed={} kind={} host={} cells={} horizontalSpan={} FULLchunks={} carvedPlayerCells={}",
                  level.getSeed(),
                  kind,
                  host,
                  count,
                  span,
                  chunks.size(),
                  carvedCells);
          return true;
        }
      }
      LogUtils.getLogger()
          .info(
              "No traversable interior cave at "
                  + host
                  + "; largest="
                  + largest
                  + " longest="
                  + longest);
      return false;
    } finally {
      active = null;
    }
  }

  private record Body(int minimum, int maximum) {
    private boolean qualifies(String kind) {
      int thickness = maximum - minimum;
      return thickness >= 12 && (kind.equals("thin") ? thickness < 24 : thickness >= 24);
    }

    private boolean containsPlayer(int footY) {
      return footY - 1 >= minimum && footY + 3 < maximum;
    }
  }

  private static boolean surfaceBiome(
      ServerLevel level, LevelChunk chunk, int x, int z, Body body, String biome) {
    int surfaceY = body.maximum() - 1;
    var stored = chunk.getNoiseBiome(x >> 2, surfaceY >> 2, z >> 2);
    return sourceSurfaceBiome(level, x, z, body, biome)
        && stored.unwrapKey().orElseThrow().location().toString().equals(biome);
  }

  private static boolean sourceSurfaceBiome(
      ServerLevel level, int x, int z, Body body, String biome) {
    int surfaceY = body.maximum() - 1;
    var state = level.getChunkSource().randomState();
    var source =
        level
            .getChunkSource()
            .getGenerator()
            .getBiomeSource()
            .getNoiseBiome(x >> 2, surfaceY >> 2, z >> 2, state.sampler());
    return source.unwrapKey().orElseThrow().location().toString().equals(biome);
  }

  private static java.util.List<Body> nativeColumnBodies(ServerLevel level, int x, int z) {
    var generator = level.getChunkSource().getGenerator();
    var column = generator.getBaseColumn(x, z, level, level.getChunkSource().randomState());
    var bodies = new java.util.ArrayList<Body>();
    int start = Integer.MIN_VALUE;
    for (int y = level.getMinBuildHeight(); y <= level.getMaxBuildHeight(); y++) {
      var block = column.getBlock(y);
      boolean solid =
          y < level.getMaxBuildHeight()
              && block.getFluidState().isEmpty()
              && net.minecraft.world.level.block.Block.isFaceFull(
                  block.getCollisionShape(
                      net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                  Direction.UP);
      if (solid && start == Integer.MIN_VALUE) start = y;
      if (!solid && start != Integer.MIN_VALUE) {
        bodies.add(new Body(start, y));
        start = Integer.MIN_VALUE;
      }
    }
    return java.util.List.copyOf(bodies);
  }

  private static BlockPos findHost(ServerLevel level, IslandTerrain terrain) {
    var source = level.getChunkSource().getGenerator().getBiomeSource();
    var sampler = level.getChunkSource().randomState().sampler();
    for (int x = -2040; x < 2048; x += 32) {
      for (int z = -2040; z < 2048; z += 32) {
        var atZero = source.getNoiseBiome(x >> 2, 0, z >> 2, sampler);
        if (!atZero
            .unwrapKey()
            .orElseThrow()
            .location()
            .toString()
            .equals("aeroscapes_reimagine:void")) continue;
        int run = 0;
        for (int y = 72; y <= 272; y += 4) {
          if (terrain.biomeDensity(new SinglePointContext(x, y, z)) > 0.5) run += 4;
          else run = 0;
          if (run >= 56) return new BlockPos(x, y - 28, z);
        }
      }
    }
    throw new IllegalStateException("No bounded thick high island with empty Y=0 found");
  }

  private static void hostThicknessControls() {
    for (int thickness : new int[] {8, 12, 24}) {
      var zero = net.minecraft.world.level.levelgen.DensityFunctions.zero();
      var empty = net.minecraft.world.level.levelgen.DensityFunctions.constant(-1);
      var original =
          net.minecraft.world.level.levelgen.DensityFunctions.min(
              net.minecraft.world.level.levelgen.DensityFunctions.yClampedGradient(96, 100, -1, 1),
              net.minecraft.world.level.levelgen.DensityFunctions.yClampedGradient(
                  96 + thickness, 100 + thickness, 1, -1));
      var terrain =
          new IslandTerrain(
              original,
              empty,
              zero,
              zero,
              net.minecraft.world.level.levelgen.DensityFunctions.constant(1));
      ChunkPos source = null;
      for (int x = 256; x < 512; x++) {
        var candidate = new ChunkPos(x, 256);
        if (!terrain.column(candidate.getMiddleBlockX(), candidate.getMiddleBlockZ()).hasDetail()) {
          source = candidate;
          break;
        }
      }
      if (source == null)
        throw new IllegalStateException("No empty layout column for host thickness control");
      var hosts = dev.aeroscapes.worldgen.IslandCaveHosts.sample(terrain, source).ranges();
      if (thickness == 8 ? !hosts.isEmpty() : hosts.size() != 1)
        throw new IllegalStateException(
            "Native host sample lost viable thickness=" + thickness + ": " + hosts);
      if (!hosts.isEmpty()) {
        int margin = thickness == 12 ? 4 : 8;
        if (hosts.getFirst().minimum() != 100 + margin
            || hosts.getFirst().maximum() != 100 + thickness - margin)
          throw new IllegalStateException("Thin-host floor/roof controls failed: " + hosts);
      }
    }
  }

  private static java.util.List<Host> findBiomeHosts(
      ServerLevel level,
      IslandTerrain terrain,
      String biome,
      boolean thin,
      int startX,
      int endX,
      java.util.List<Host> selected) {
    var source = level.getChunkSource().getGenerator().getBiomeSource();
    var sampler = level.getChunkSource().randomState().sampler();
    int originZ = Integer.getInteger("aeroscapes.regression.caveOriginZ", 0);
    for (int x = startX; x < endX; x += 32)
      for (int z = originZ - 4088; z < originZ + 4096; z += 32) {
        int start = Integer.MIN_VALUE;
        for (int y = -112; y <= 368; y += 4) {
          var column = terrain.column(x, z);
          boolean solid =
              y < 368
                  && !column.liner(y)
                  && !(column.lavaIsland() != null
                      && column
                          .lavaShape()
                          .containsHabitat(
                              y,
                              column.lavaIsland().waterline(),
                              dev.aeroscapes.worldgen.IslandBiomeSource.VOLCANIC_HABITAT_MARGIN))
                  && terrain.biomeDensity(new SinglePointContext(x, y, z)) > 0.15;
          if (solid && start == Integer.MIN_VALUE) start = y;
          if (solid || start == Integer.MIN_VALUE) continue;
          int thickness = y - start;
          if (thickness >= 12
              && (thin ? thickness < 24 : thickness >= 24)
              && source
                  .getNoiseBiome(x >> 2, (y - 4) >> 2, z >> 2, sampler)
                  .unwrapKey()
                  .orElseThrow()
                  .location()
                  .toString()
                  .equals(biome)) {
            int selectedX = x;
            int selectedZ = z;
            int anchorY = (start + y) / 2;
            Body physical =
                nativeColumnBodies(level, x, z).stream()
                    .filter(body -> body.minimum() <= anchorY && anchorY < body.maximum())
                    .filter(body -> body.qualifies(thin ? "thin" : "high"))
                    .filter(body -> sourceSurfaceBiome(level, selectedX, selectedZ, body, biome))
                    .findFirst()
                    .orElse(null);
            if (physical == null) {
              start = Integer.MIN_VALUE;
              continue;
            }
            if (selected.stream()
                .noneMatch(
                    host ->
                        Math.abs(host.point().getX() - selectedX) < 128
                            && Math.abs(host.point().getZ() - selectedZ) < 128)) {
              selected.add(
                  new Host(
                      new BlockPos(x, anchorY, z),
                      null,
                      null,
                      -1,
                      new dev.aeroscapes.worldgen.IslandCaveHosts.Range(
                          physical.minimum(), physical.maximum())));
              LogUtils.getLogger()
                  .info(
                      "Cave biome cohort: biome={} thickness={} body={}..{} point={},{}",
                      biome,
                      physical.maximum() - physical.minimum(),
                      physical.minimum(),
                      physical.maximum(),
                      x,
                      z);
              if (selected.size() == 4) return java.util.List.copyOf(selected);
            }
          }
          start = Integer.MIN_VALUE;
        }
      }
    return java.util.List.copyOf(selected);
  }

  private static final class BiomeCohort {
    private final ServerLevel level;
    private final IslandTerrain terrain;
    private final String biome;
    private final String kind;
    private final java.util.List<Host> hosts = new java.util.ArrayList<>();
    private final java.util.function.Consumer<
            net.neoforged.neoforge.event.tick.ServerTickEvent.Post>
        listener = this::tick;
    private final int maximumX = Integer.getInteger("aeroscapes.regression.caveOriginX", 0) + 4096;
    private int nextX = maximumX - 8184;
    private int verified;
    private int passed;

    private BiomeCohort(ServerLevel level, IslandTerrain terrain, String biome, String kind) {
      this.level = level;
      this.terrain = terrain;
      this.biome = biome;
      this.kind = kind;
    }

    private void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
      boolean keep = false;
      try {
        if (hosts.size() < 4 && nextX < maximumX) {
          findBiomeHosts(level, terrain, biome, kind.equals("thin"), nextX, nextX + 32, hosts);
          nextX += 32;
          keep = true;
          return;
        }
        if (hosts.isEmpty())
          throw new IllegalStateException(
              "Only " + hosts.size() + " independent " + biome + " cave hosts; kind=" + kind);
        if (verifyHost(level, terrain, hosts.get(verified++), kind, biome)) passed++;
        if (verified < hosts.size()) {
          keep = true;
          return;
        }
        LogUtils.getLogger()
            .info(
                "Cave biome observations completed: seed={} kind={} biome={} traversableIslands={}/{}; use a named witness for acceptance",
                level.getSeed(),
                kind,
                biome,
                passed,
                hosts.size());
      } finally {
        if (!keep) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
      }
    }
  }

  private static java.util.List<Host> findAddedHosts(IslandTerrain terrain, String kind) {
    if (!java.util.Set.of("small", "medium", "large").contains(kind))
      throw new IllegalArgumentException("Unknown cave host kind: " + kind);
    int cellSize = kind.equals("large") ? 1536 : 256;
    var selected = new java.util.ArrayList<Host>();
    for (int cellX = -6; cellX <= 6; cellX++) {
      for (int cellZ = -6; cellZ <= 6; cellZ++) {
        for (int layer = 0; layer < (kind.equals("large") ? 1 : 4); layer++) {
          int x;
          int z;
          IslandLayout.SmallIsland small = null;
          IslandLayout.LargeIsland large = null;
          if (kind.equals("large")) {
            var island = terrain.layout().largeIsland(cellX * cellSize, cellZ * cellSize);
            if (island == null) continue;
            x = (int) island.centerX();
            z = (int) island.centerZ();
            large = island;
          } else {
            var island = terrain.layout().smallIsland(cellX * cellSize, cellZ * cellSize, layer);
            if (island == null
                || (kind.equals("small")
                    ? island.radius() < 24 || island.radius() >= 40
                    : island.radius() < 40)) continue;
            x = island.centerX();
            z = island.centerZ();
            small = island;
          }
          var hosts = terrain.caveHosts(new ChunkPos(x >> 4, z >> 4));
          var shape =
              small != null ? small.shape(terrain.layout().seed(), x, z) : large.shape(x, z);
          for (var interior : hosts.ranges()) {
            int lower = Math.max(interior.minimum(), (int) Math.ceil(shape.bottom()) + 8);
            int upper = Math.min(interior.maximum(), (int) Math.floor(shape.top()) - 8);
            if (upper - lower < 12) continue;
            if (selected.stream()
                .anyMatch(
                    host ->
                        Math.abs(host.point().getX() - x) < 128
                            && Math.abs(host.point().getZ() - z) < 128)) continue;
            selected.add(new Host(new BlockPos(x, (lower + upper) / 2, z), small, large, layer));
            if (selected.size() == 8) return java.util.List.copyOf(selected);
            break;
          }
        }
      }
    }
    throw new IllegalStateException(
        "Found " + selected.size() + " bounded " + kind + " cave hosts; fixture requires eight");
  }
}
