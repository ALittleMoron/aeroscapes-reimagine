package dev.aeroscapes.sky;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Authored geometry and animation envelopes supplement native collision parts during placement. */
public final class WhaleDecorationClearance {
  private static final Map<WhaleVariety, List<AABB>> EXTENTS = load();
  private static final Map<WhaleVariety, AABB> ENVELOPES = envelopes();

  private WhaleDecorationClearance() {}

  public static List<AABB> at(WhaleVariety variety, Vec3 position, float yaw) {
    List<AABB> result = new ArrayList<>();
    for (AABB model : EXTENTS.get(variety)) {
      result.add(at(model, position, yaw));
    }
    return List.copyOf(result);
  }

  public static AABB enclosing(WhaleVariety variety, Vec3 position, float yaw) {
    return at(ENVELOPES.get(variety), position, yaw);
  }

  private static Map<WhaleVariety, AABB> envelopes() {
    Map<WhaleVariety, AABB> result = new EnumMap<>(WhaleVariety.class);
    for (var entry : EXTENTS.entrySet()) {
      AABB envelope = entry.getValue().getFirst();
      for (AABB bounds : entry.getValue()) envelope = envelope.minmax(bounds);
      result.put(entry.getKey(), envelope);
    }
    return Map.copyOf(result);
  }

  private static AABB at(AABB model, Vec3 position, float yaw) {
    Vec3 forward = Vec3.directionFromRotation(0, yaw);
    double halfWidth = Math.max(Math.abs(model.minX), Math.abs(model.maxX)) / 16.0;
    double minimumX = Double.POSITIVE_INFINITY, maximumX = Double.NEGATIVE_INFINITY;
    double minimumZ = Double.POSITIVE_INFINITY, maximumZ = Double.NEGATIVE_INFINITY;
    for (double side : new double[] {-halfWidth, halfWidth}) {
      for (double ahead : new double[] {-model.maxZ / 16.0, -model.minZ / 16.0}) {
        double x = position.x + forward.x * ahead - forward.z * side;
        double z = position.z + forward.z * ahead + forward.x * side;
        minimumX = Math.min(minimumX, x);
        maximumX = Math.max(maximumX, x);
        minimumZ = Math.min(minimumZ, z);
        maximumZ = Math.max(maximumZ, z);
      }
    }
    return new AABB(
        minimumX,
        position.y + model.minY / 16.0,
        minimumZ,
        maximumX,
        position.y + model.maxY / 16.0,
        maximumZ);
  }

  private static Map<WhaleVariety, List<AABB>> load() {
    try (var input =
        WhaleDecorationClearance.class.getResourceAsStream(
            "/assets/aeroscapes_reimagine/whales/material_layout.json")) {
      if (input == null) throw new IOException("Missing whale habitat extents");
      JsonElement parsed =
          JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8));
      if (!parsed.isJsonObject()) throw new IOException("Whale habitat layout must be an object");
      var layout = parsed.getAsJsonObject();
      Map<WhaleVariety, List<AABB>> result = new EnumMap<>(WhaleVariety.class);
      for (WhaleVariety variety : WhaleVariety.values()) {
        var entry = layout.get(variety.id());
        if (entry == null || !entry.isJsonObject())
          throw new IOException("Missing whale habitat " + variety.id());
        var extents = entry.getAsJsonObject().get("spawn_clearance");
        if (extents == null || !extents.isJsonArray() || extents.getAsJsonArray().isEmpty())
          throw new IOException("Missing whale spawn clearance " + variety.id());
        List<AABB> bounds = new ArrayList<>();
        for (JsonElement value : extents.getAsJsonArray()) {
          if (!value.isJsonObject()) throw new IOException("Invalid whale habitat extent");
          Vec3 minimum = vector(value.getAsJsonObject().get("min"));
          Vec3 maximum = vector(value.getAsJsonObject().get("max"));
          if (minimum.x >= maximum.x || minimum.y >= maximum.y || minimum.z >= maximum.z)
            throw new IOException("Empty whale habitat extent");
          bounds.add(new AABB(minimum, maximum));
        }
        result.put(variety, List.copyOf(bounds));
      }
      return Map.copyOf(result);
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot load generated whale spawn clearance", exception);
    }
  }

  private static Vec3 vector(JsonElement value) throws IOException {
    if (value == null || !value.isJsonArray()) throw new IOException("Invalid habitat coordinate");
    JsonArray array = value.getAsJsonArray();
    if (array.size() != 3) throw new IOException("Expected three habitat coordinates");
    double[] coordinates = new double[3];
    for (int index = 0; index < coordinates.length; index++) {
      var coordinate = array.get(index);
      if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber())
        throw new IOException("Habitat coordinate must be numeric");
      coordinates[index] = coordinate.getAsDouble();
      if (!Double.isFinite(coordinates[index]))
        throw new IOException("Nonfinite habitat coordinate");
    }
    return new Vec3(coordinates[0], coordinates[1], coordinates[2]);
  }
}
