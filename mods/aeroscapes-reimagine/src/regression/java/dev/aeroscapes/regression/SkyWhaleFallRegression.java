package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.sky.SkyWhaleFallFeature;
import dev.aeroscapes.sky.SkyWhaleFallShape;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Generates natural FULL terrain, finds whale-fall sites by their whale bones, checks the modest
 * yield and both surface and underside forms, and dumps blocks around each site for renders.
 */
final class SkyWhaleFallRegression {
  private static final Block WHALE_BONE =
      BuiltInRegistries.BLOCK.get(
          ResourceLocation.fromNamespaceAndPath("skywhales", "whale_bone_block"));
  private static final Block CRACKED =
      BuiltInRegistries.BLOCK.get(
          ResourceLocation.fromNamespaceAndPath("skywhales", "cracked_whale_bone_block"));

  private SkyWhaleFallRegression() {}

  static void verify(ServerLevel level, int radiusChunks, Path report) throws IOException {
    var center = new ChunkPos(level.getSharedSpawnPos());
    long started = System.nanoTime();
    for (int x = -radiusChunks; x <= radiusChunks; x++) {
      for (int z = -radiusChunks; z <= radiusChunks; z++) {
        level.getChunk(center.x + x, center.z + z, ChunkStatus.FULL, true);
      }
    }
    long generation = System.nanoTime() - started;
    List<BlockPos> found = new ArrayList<>();
    var cursor = new BlockPos.MutableBlockPos();
    for (int x = -radiusChunks; x <= radiusChunks; x++) {
      for (int z = -radiusChunks; z <= radiusChunks; z++) {
        var chunk = level.getChunk(center.x + x, center.z + z);
        for (int index = 0; index < chunk.getSectionsCount(); index++) {
          var section = chunk.getSection(index);
          if (!section.maybeHas(
              state -> state.is(WHALE_BONE) || state.is(CRACKED) || state.is(Blocks.BONE_BLOCK)))
            continue;
          int minimumY = chunk.getMinBuildHeight() + index * 16;
          for (int y = 0; y < 16; y++) {
            for (int bx = 0; bx < 16; bx++) {
              for (int bz = 0; bz < 16; bz++) {
                var state = section.getBlockState(bx, y, bz);
                if (state.is(WHALE_BONE) || state.is(CRACKED) || state.is(Blocks.BONE_BLOCK)) {
                  cursor.set(
                      chunk.getPos().getMinBlockX() + bx,
                      minimumY + y,
                      chunk.getPos().getMinBlockZ() + bz);
                  found.add(cursor.immutable());
                }
              }
            }
          }
        }
      }
    }
    int naturalBones =
        (int)
            found.stream()
                .filter(position -> !level.getBlockState(position).is(Blocks.BONE_BLOCK))
                .count();
    // Acceptance of the real planner on every inner chunk of the saved terrain; a few accepted
    // plans are then written so renders show each form even when natural sites are rare.
    int probes = 0;
    int surfacePlans = 0;
    int undersidePlans = 0;
    List<SkyWhaleFallFeature.Plan> samples = new ArrayList<>();
    for (int x = -radiusChunks + 2; x <= radiusChunks - 2; x++) {
      for (int z = -radiusChunks + 2; z <= radiusChunks - 2; z++) {
        var chunk = new ChunkPos(center.x + x, center.z + z);
        probes++;
        for (var site : SkyWhaleFallFeature.Site.values()) {
          var plan =
              SkyWhaleFallFeature.plan(
                  level, chunk, RandomSource.create(level.getSeed() ^ chunk.toLong()), site);
          if (plan == null) continue;
          if (site == SkyWhaleFallFeature.Site.SURFACE) surfacePlans++;
          else undersidePlans++;
          long kept = samples.stream().filter(sample -> sample.site() == site).count();
          if (kept < 3
              && samples.stream()
                  .noneMatch(
                      sample ->
                          sample.positions().getFirst().distManhattan(plan.positions().getFirst())
                              < 160)) {
            samples.add(plan);
          }
        }
      }
    }
    for (var sample : samples) {
      for (int index = 0; index < sample.positions().size(); index++) {
        level.setBlock(sample.positions().get(index), sample.states().get(index), 2);
      }
      found.addAll(
          sample.positions().stream()
              .filter(
                  position ->
                      level.getBlockState(position).is(WHALE_BONE)
                          || level.getBlockState(position).is(CRACKED)
                          || level.getBlockState(position).is(Blocks.BONE_BLOCK))
              .toList());
    }
    // One site per connected group of bones (gaps up to three blocks, as in the tail).
    List<List<BlockPos>> sites = new ArrayList<>();
    var remaining = new java.util.HashSet<>(found);
    while (!remaining.isEmpty()) {
      var start = remaining.iterator().next();
      remaining.remove(start);
      List<BlockPos> site = new ArrayList<>();
      var queue = new java.util.ArrayDeque<BlockPos>();
      queue.add(start);
      while (!queue.isEmpty()) {
        var position = queue.poll();
        site.add(position);
        for (var other :
            BlockPos.betweenClosed(position.offset(-3, -3, -3), position.offset(3, 3, 3))) {
          if (remaining.remove(other)) queue.add(other.immutable());
        }
      }
      if (site.stream()
          .anyMatch(
              position ->
                  level.getBlockState(position).is(WHALE_BONE)
                      || level.getBlockState(position).is(CRACKED))) {
        sites.add(site);
      }
    }
    var json = new JsonObject();
    var entries = new JsonArray();
    int surface = 0;
    int underside = 0;
    List<String> excessive = new ArrayList<>();
    for (var site : sites) {
      int intact = 0;
      int cracked = 0;
      long sumX = 0;
      long sumY = 0;
      long sumZ = 0;
      for (var position : site) {
        if (level.getBlockState(position).is(WHALE_BONE)) intact++;
        else if (level.getBlockState(position).is(CRACKED)) cracked++;
        sumX += position.getX();
        sumY += position.getY();
        sumZ += position.getZ();
      }
      var middle =
          new BlockPos(
              (int) (sumX / site.size()), (int) (sumY / site.size()), (int) (sumZ / site.size()));
      boolean hanging = hangsBelowIsland(level, site);
      if (hanging) underside++;
      else surface++;
      if (intact > SkyWhaleFallShape.MAXIMUM_INTACT_BONE
          || cracked > SkyWhaleFallShape.MAXIMUM_CRACKED_BONE) {
        excessive.add(middle + ": " + intact + "/" + cracked);
      }
      var entry = new JsonObject();
      entry.addProperty("x", middle.getX());
      entry.addProperty("y", middle.getY());
      entry.addProperty("z", middle.getZ());
      entry.addProperty("site", hanging ? "underside" : "surface");
      entry.addProperty("intact", intact);
      entry.addProperty("cracked", cracked);
      entry.add("blocks", dump(level, middle));
      entries.add(entry);
    }
    json.add("sites", entries);
    json.addProperty("natural_whale_bones", naturalBones);
    json.addProperty("probes", probes);
    json.addProperty("surface_plans", surfacePlans);
    json.addProperty("underside_plans", undersidePlans);
    json.addProperty("chunks", (2 * radiusChunks + 1) * (2 * radiusChunks + 1));
    json.addProperty("generation_ms", generation / 1_000_000);
    json.addProperty("feature_attempts", SkyWhaleFallFeature.ATTEMPTS.sum());
    json.addProperty("feature_ms", SkyWhaleFallFeature.NANOS.sum() / 1_000_000.0);
    Files.writeString(report, new GsonBuilder().create().toJson(json) + "\n");
    require(excessive.isEmpty(), "Whale-fall yield stays modest per skeleton: " + excessive);
    require(surfacePlans > 0, "Natural islands can host a surface whale fall");
    require(undersidePlans > 0, "Island undersides can host a hanging whale fall");
    System.out.println(
        "Sky whale fall regression passed: surface="
            + surface
            + " underside="
            + underside
            + " surface_plans="
            + surfacePlans
            + " underside_plans="
            + undersidePlans
            + " probes="
            + probes
            + " natural_bones="
            + naturalBones
            + " attempts="
            + SkyWhaleFallFeature.ATTEMPTS.sum()
            + " feature_ms="
            + SkyWhaleFallFeature.NANOS.sum() / 1_000_000.0
            + " generation_ms="
            + generation / 1_000_000);
  }

  /** A hanging skeleton touches rock from below more often than it rests on ground. */
  private static boolean hangsBelowIsland(ServerLevel level, List<BlockPos> site) {
    int hanging = 0;
    int resting = 0;
    for (var position : site) {
      if (ground(level.getBlockState(position.above()))) hanging++;
      if (ground(level.getBlockState(position.below()))) resting++;
    }
    return hanging > resting;
  }

  private static boolean ground(net.minecraft.world.level.block.state.BlockState state) {
    return state.is(net.minecraft.tags.BlockTags.DIRT)
        || state.is(net.minecraft.tags.BlockTags.SAND)
        || state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD);
  }

  /** Block names in a box around the site, for deterministic renders outside Minecraft. */
  private static JsonObject dump(ServerLevel level, BlockPos middle) {
    int half = 26;
    int down = 14;
    int up = 14;
    var palette = new JsonArray();
    var indices = new java.util.LinkedHashMap<String, Integer>();
    var cells = new StringBuilder();
    for (int y = middle.getY() - down; y <= middle.getY() + up; y++) {
      for (int z = middle.getZ() - half; z <= middle.getZ() + half; z++) {
        for (int x = middle.getX() - half; x <= middle.getX() + half; x++) {
          var state = level.getBlockState(new BlockPos(x, y, z));
          String name =
              state.isAir() ? "air" : BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
          Integer index = indices.get(name);
          if (index == null) {
            index = indices.size();
            indices.put(name, index);
            palette.add(name);
          }
          cells.append(Integer.toString(index, 36)).append(',');
        }
      }
    }
    var result = new JsonObject();
    result.addProperty("size_x", 2 * half + 1);
    result.addProperty("size_y", down + up + 1);
    result.addProperty("size_z", 2 * half + 1);
    result.add("palette", palette);
    result.addProperty("cells", cells.toString());
    return result;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
