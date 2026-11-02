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
      java.util.Set<Long> writes) {}

  private record Host(
      BlockPos point, IslandLayout.SmallIsland small, IslandLayout.LargeIsland large, int layer) {
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

  private CaveNetworkRegression() {}

  public static void verify(ServerLevel level) {
    if (!level.getServer().isDedicatedServer()
        || level.getServer().getPlayerList().getPlayerCount() != 0)
      throw new IllegalStateException("Cave fixture requires an empty disposable dedicated server");
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    if (terrain == null) throw new IllegalStateException("Missing island terrain");
    String kind = System.getProperty("aeroscapes.regression.caveKind", "high");
    var hosts =
        kind.equals("high")
            ? java.util.List.of(new Host(findHost(level, terrain), null, null, -1))
            : findAddedHosts(terrain, kind);
    int passed = 0;
    for (var selected : hosts) if (verifyHost(level, terrain, selected, kind)) passed++;
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
      ServerLevel level, IslandTerrain terrain, Host selected, String kind) {
    BlockPos host = selected.point();
    LogUtils.getLogger()
        .info(
            "Cave selected host: kind={} point={} layer={} shape={}",
            kind,
            host,
            selected.layer(),
            selected.shape(terrain.layout().seed(), host.getX(), host.getZ()));
    int minimumSpan = kind.equals("small") ? 16 : kind.equals("medium") ? 24 : 40;
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
                + terrain.caveHosts(new ChunkPos(host)).ranges());
      LogUtils.getLogger()
          .info(
              "Cave source bands: host={} bands={}",
              host,
              terrain.caveHosts(new ChunkPos(host)).ranges());
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
          for (int y = 0; y < height; y++) {
            int blockY = minY + y;
            point.set(blockX, blockY, blockZ);
            if (!chunk.getBlockState(point).isAir() || !chunk.getBlockState(point.above()).isAir())
              continue;
            airPairs++;
            if (!chunk.getBlockState(point.below()).isFaceSturdy(chunk, point.below(), Direction.UP)
                || !chunk.getBlockState(point.below()).getFluidState().isEmpty()) continue;
            supported++;
            if (envelope != null
                    && (envelope.density(blockY) < 0.1 || envelope.density(blockY + 6) < 0.1)
                || terrain.biomeDensity(new SinglePointContext(blockX, blockY, blockZ)) < 0.15
                || terrain.biomeDensity(new SinglePointContext(blockX, blockY + 6, blockZ)) < 0.15)
              continue;
            interiorCells++;
            walkable[(z * width + x) * height + y] = true;
          }
        }
      }
      int largest = 0;
      int longest = 0;
      LogUtils.getLogger()
          .info(
              "Cave FULL cells: host={} airPairs={} supported={} interior={} actualCarvedAir={}",
              host,
              airPairs,
              supported,
              interiorCells,
              capture.writes().size());
      for (int start = 0; start < walkable.length; start++) {
        if (!walkable[start]) continue;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        walkable[start] = false;
        int count = 0;
        int carvedCells = 0;
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
          if (capture.writes().contains(foot.asLong())
              || capture.writes().contains(foot.above().asLong())) carvedCells++;
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
              .info("Cave FULL component: host={} cells={} horizontalSpan={}", host, count, span);
        if (span >= minimumSpan
            && count >= minimumSpan * 3
            && carvedCells >= 8
            && carvedCells * 3 >= count) {
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
