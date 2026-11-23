package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.regression.noise.mixin.FeatureIndexAccess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Frozen natural stage outputs for isolating changes after the exact NOISE fixture. */
public final class StageSnapshotRegression {
  private static final List<ChunkPos> SITES = sites();

  private static List<ChunkPos> sites() {
    String configured = System.getProperty("aeroscapes.regression.stageSnapshotSites");
    if (configured != null) {
      List<ChunkPos> selected = new ArrayList<>();
      for (String entry : configured.split(";")) {
        String[] coordinates = entry.split(",", -1);
        if (coordinates.length != 2)
          throw new IllegalArgumentException("Expected chunk x,z in stageSnapshotSites: " + entry);
        selected.add(
            new ChunkPos(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1])));
      }
      return List.copyOf(selected);
    }
    if (!Boolean.getBoolean("aeroscapes.regression.stageSnapshotAll"))
      return List.of(
          new ChunkPos(764, 766), new ChunkPos(765, 766),
          new ChunkPos(764, 767), new ChunkPos(765, 767));
    List<ChunkPos> sites = new ArrayList<>();
    for (int z = 764; z <= 772; z++) for (int x = 764; x <= 772; x++) sites.add(new ChunkPos(x, z));
    return List.copyOf(sites);
  }

  private static final List<ChunkStatus> STAGES =
      List.of(ChunkStatus.NOISE, ChunkStatus.SURFACE, ChunkStatus.CARVERS, ChunkStatus.FEATURES);
  private static volatile Capture active;

  private StageSnapshotRegression() {}

  static void verify(ServerLevel level) {
    if (level.dimension() != Level.OVERWORLD
        || level.getSeed() != 1
            && System.getProperty("aeroscapes.regression.stageSnapshotSites") == null)
      throw new IllegalStateException("Stage snapshots outside seed 1 require explicit sites");
    Path path = Path.of(System.getProperty("aeroscapes.regression.stageSnapshotFile")).normalize();
    if (!path.isAbsolute() || !path.toString().contains("/.runtime/"))
      throw new IllegalStateException("stageSnapshotFile must be absolute in ignored .runtime");
    if (active != null) throw new IllegalStateException("Another stage capture is active");
    Capture capture = new Capture(level);
    active = capture;
    if (Boolean.getBoolean("aeroscapes.regression.stageSnapshotConcurrent")) {
      NeoForge.EVENT_BUS.addListener(new ConcurrentCapture(level, path, capture)::tick);
      ChunkPerformanceRegression.start(level);
      return;
    }
    try {
      // Every centre that can write into the targets must finish before the settled snapshot.
      var neighborhood = new LinkedHashSet<ChunkPos>();
      for (ChunkPos site : SITES)
        for (int z = site.z - 1; z <= site.z + 1; z++)
          for (int x = site.x - 1; x <= site.x + 1; x++) neighborhood.add(new ChunkPos(x, z));
      for (ChunkPos site : neighborhood)
        level.getChunkSource().getChunk(site.x, site.z, ChunkStatus.FEATURES, true);
      writeCaptured(level, path, capture);
    } finally {
      active = null;
    }
  }

  private static void writeCaptured(ServerLevel level, Path path, Capture capture) {
    for (ChunkPos site : SITES) level.getChunk(site.x, site.z);
    CompoundTag result = new CompoundTag();
    result.putInt("format", 1);
    result.putLong("seed", level.getSeed());
    ListTag featureInputs = new ListTag();
    for (ChunkPos site : SITES) {
      CompoundTag input = capture.featureInputs.get(site.toLong());
      if (input == null) throw new IllegalStateException("Missing feature input snapshot " + site);
      featureInputs.add(input);
    }
    result.put("featureInputs", featureInputs);
    ListTag centers = new ListTag();
    for (ChunkPos site : capture.featureCenters)
      centers.add(StringTag.valueOf(site.x + "," + site.z));
    result.put("featureCenters", centers);
    for (ChunkStatus stage : STAGES) {
      ListTag chunks = new ListTag();
      for (ChunkPos site : SITES) {
        CompoundTag chunk = capture.chunks.get(stage).get(site.toLong());
        if (chunk == null)
          throw new IllegalStateException("Use fresh stage chunks: missing " + stage + " " + site);
        chunks.add(chunk);
      }
      result.put(stage.toString(), chunks);
    }
    // FEATURES snapshots follow the centre task, not later neighbour writes. FULL is separate.
    ListTag full = new ListTag();
    for (ChunkPos site : SITES)
      full.add(NoiseSnapshotRegression.snapshot(level, level.getChunk(site.x, site.z)));
    result.put("full", full);
    if (Boolean.getBoolean("aeroscapes.regression.stageSnapshotTerrain"))
      result.put("terrainFields", terrainFields(level));
    ListTag featureOrder = new ListTag();
    var registry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
    for (var step :
        ((FeatureIndexAccess) level.getChunkSource().getGenerator())
            .aeroscapesRegression$featuresPerStep()
            .get()) {
      ListTag features = new ListTag();
      for (int index = 0; index < step.features().size(); index++) {
        var feature = step.features().get(index);
        if (step.indexMapping().applyAsInt(feature) != index)
          throw new IllegalStateException("Inconsistent native feature index");
        var key = registry.getKey(feature);
        CompoundTag entry = new CompoundTag();
        if (key != null) entry.putString("id", key.toString());
        else
          entry.put(
              "inline",
              PlacedFeature.DIRECT_CODEC
                  .encodeStart(
                      level.registryAccess().createSerializationContext(NbtOps.INSTANCE), feature)
                  .getOrThrow());
        features.add(entry);
      }
      featureOrder.add(features);
    }
    result.put("featureOrder", featureOrder);
    try {
      Files.createDirectories(path.getParent());
      NbtIo.writeCompressed(result, path);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot write stage snapshots " + path, exception);
    }
    LogUtils.getLogger()
        .info("Stage snapshot regression captured: chunks={} path={}", SITES.size(), path);
  }

  private static ListTag terrainFields(ServerLevel level) {
    var terrain = dev.aeroscapes.worldgen.IslandTerrain.from(level.getChunkSource().randomState());
    if (terrain == null) throw new IllegalStateException("Expected owned island terrain");
    var fields = new ListTag();
    for (ChunkPos site : SITES) {
      int x = site.getMiddleBlockX();
      int z = site.getMiddleBlockZ();
      var column = terrain.column(x, z);
      var data = new CompoundTag();
      data.putInt("x", x);
      data.putInt("z", z);
      data.putDouble("largeTop", column.large().top());
      data.putDouble("largeBottom", column.large().bottom());
      data.putDouble("largeCap", column.large().cap());
      data.putDouble("basinTop", column.basinShape().top());
      int original = level.getMinBuildHeight();
      int large = original;
      for (int y = level.getMaxBuildHeight() - 1; y > level.getMinBuildHeight(); y--) {
        var position =
            new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, y, z);
        if (original == level.getMinBuildHeight()
            && y < 304
            && y >= -64
            && terrain.original().compute(position) > 0) original = y;
        if (large == level.getMinBuildHeight()
            && column.largeDensity(y, terrain.form().compute(position)) > 0) large = y;
      }
      data.putInt("originalSurface", original);
      data.putInt("largeSurface", large);
      fields.add(data);
    }
    return fields;
  }

  public static CompletableFuture<ChunkAccess> capture(
      ServerLevel level, ChunkStatus stage, ChunkPos site, CompletableFuture<ChunkAccess> future) {
    Capture capture = active;
    if (capture == null || capture.level != level || !SITES.contains(site)) return future;
    return future.thenApply(
        chunk -> {
          capture
              .chunks
              .get(stage)
              .putIfAbsent(site.toLong(), NoiseSnapshotRegression.snapshot(level, chunk));
          return chunk;
        });
  }

  public static void captureFeatureInput(ServerLevel level, ChunkAccess chunk) {
    Capture capture = active;
    if (capture == null || capture.level != level || !SITES.contains(chunk.getPos())) return;
    capture.featureCenters.add(chunk.getPos());
    capture.featureInputs.computeIfAbsent(
        chunk.getPos().toLong(), ignored -> NoiseSnapshotRegression.snapshot(level, chunk));
  }

  private static final class ConcurrentCapture {
    private final ServerLevel level;
    private final Path path;
    private final Capture capture;
    private final long deadline = System.nanoTime() + 120_000_000_000L;

    private ConcurrentCapture(ServerLevel level, Path path, Capture capture) {
      this.level = level;
      this.path = path;
      this.capture = capture;
    }

    private void tick(ServerTickEvent.Post event) {
      if (active != capture || event.getServer() != level.getServer()) return;
      if (System.nanoTime() > deadline)
        throw new IllegalStateException("Concurrent stage capture timed out");
      if (capture.chunks.get(ChunkStatus.FEATURES).size() != SITES.size()) return;
      try {
        writeCaptured(level, path, capture);
      } finally {
        active = null;
      }
    }
  }

  private static final class Capture {
    private final ServerLevel level;
    private final Map<ChunkStatus, Map<Long, CompoundTag>> chunks = new ConcurrentHashMap<>();
    private final Map<Long, CompoundTag> featureInputs = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<ChunkPos> featureCenters = new ConcurrentLinkedQueue<>();

    private Capture(ServerLevel level) {
      this.level = level;
      for (ChunkStatus stage : STAGES) chunks.put(stage, new ConcurrentHashMap<>());
    }
  }
}
