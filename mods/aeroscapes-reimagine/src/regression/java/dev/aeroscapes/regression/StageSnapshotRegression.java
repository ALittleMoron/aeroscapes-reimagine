package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.regression.noise.mixin.FeatureIndexAccess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Frozen natural stage outputs for isolating changes after the exact NOISE fixture. */
public final class StageSnapshotRegression {
  private static final List<ChunkPos> SITES = sites();

  private static List<ChunkPos> sites() {
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
    if (level.getSeed() != 1 || level.dimension() != Level.OVERWORLD)
      throw new IllegalStateException("Stage snapshots require disposable seed-1 Overworld");
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
      int minX = SITES.stream().mapToInt(site -> site.x).min().orElseThrow() - 1;
      int maxX = SITES.stream().mapToInt(site -> site.x).max().orElseThrow() + 1;
      int minZ = SITES.stream().mapToInt(site -> site.z).min().orElseThrow() - 1;
      int maxZ = SITES.stream().mapToInt(site -> site.z).max().orElseThrow() + 1;
      for (int z = minZ; z <= maxZ; z++)
        for (int x = minX; x <= maxX; x++)
          level.getChunkSource().getChunk(x, z, ChunkStatus.FEATURES, true);
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

  public static void captureOpenCl(
      RandomState state, StaticCache2D<ProtoChunk> chunks, ChunkPos start, int size) {
    Capture capture = active;
    if (capture == null || capture.level.getChunkSource().randomState() != state) return;
    for (ChunkPos site : SITES) {
      if (site.x < start.x
          || site.x >= start.x + size
          || site.z < start.z
          || site.z >= start.z + size) continue;
      ProtoChunk chunk = chunks.get(site.x, site.z);
      if (chunk.getPersistedStatus() != ChunkStatus.NOISE)
        throw new IllegalStateException("Late OpenCL NOISE capture");
      capture
          .chunks
          .get(ChunkStatus.NOISE)
          .computeIfAbsent(
              site.toLong(), ignored -> NoiseSnapshotRegression.snapshot(capture.level, chunk));
    }
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
