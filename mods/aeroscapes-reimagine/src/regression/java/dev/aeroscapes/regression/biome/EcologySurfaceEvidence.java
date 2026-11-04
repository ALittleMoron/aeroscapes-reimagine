package dev.aeroscapes.regression.biome;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.regression.biome.mixin.SurfaceContextProbeAccess;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.levelgen.RandomState;

/** Bounded observation of native surface conditions; does not change any rule result. */
public final class EcologySurfaceEvidence {
  private record Request(
      RandomState random,
      int x,
      int z,
      int bottom,
      int top,
      ConcurrentHashMap<Integer, JsonObject> rows) {}

  private static volatile Request active;

  private EcologySurfaceEvidence() {}

  public static void begin(RandomState random, int x, int z, int bottom, int top) {
    active = new Request(random, x, z, bottom, top, new ConcurrentHashMap<>());
  }

  public static void record(SurfaceContextProbeAccess context, int cutoff) {
    var request = active;
    if (request == null
        || request.random() != context.aeroscapes$random()
        || request.x() != context.aeroscapes$x()
        || request.z() != context.aeroscapes$z()) return;
    int y = context.aeroscapes$y();
    if (y < request.bottom() || y > request.top()) return;
    JsonObject row = new JsonObject();
    row.addProperty("y", y);
    row.addProperty("observed_cutoff", cutoff);
    row.addProperty("above_preliminary", y >= cutoff);
    row.addProperty(
        "actual_surface_biome",
        context.aeroscapes$biome().get().unwrapKey().orElseThrow().location().toString());
    row.addProperty("water_height", context.aeroscapes$waterHeight());
    row.addProperty("stone_depth_above", context.aeroscapes$stoneDepthAbove());
    row.addProperty("stone_depth_below", context.aeroscapes$stoneDepthBelow());
    row.addProperty("surface_depth", context.aeroscapes$surfaceDepth());
    request.rows().put(y, row);
  }

  public static JsonArray finish() {
    var request = active;
    active = null;
    JsonArray rows = new JsonArray();
    if (request != null)
      request.rows().entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(entry -> rows.add(entry.getValue()));
    return rows;
  }
}
