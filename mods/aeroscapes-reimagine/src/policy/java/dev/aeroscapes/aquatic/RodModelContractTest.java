package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;
import punchy.client.render.FishingLineProjection;

/** Checks the actual mesh data consumed by baking and the native line-tip detector. */
public final class RodModelContractTest {
  private RodModelContractTest() {}

  public static void main(String[] arguments) {
    int variants = 0;
    for (String style : RodModels.palettes().keySet()) {
      for (String suffix : List.of("", "_left", "_fp", "_fp_left")) {
        Map<String, JsonObject> idle = parts(RodModels.model(style, false, suffix));
        Map<String, JsonObject> cast = parts(RodModels.model(style, true, suffix));
        require(idle.size() - cast.size() == 7, "Cast removes exactly the hanging tackle");
        for (var part : cast.entrySet()) {
          require(
              part.getValue().equals(idle.get(part.getKey())), "Retain the complete cast rod body");
          require(
              !stowed(part.getKey()), "Cast geometry cannot contain a second bobber/hanging line");
        }
        for (var part : idle.entrySet()) {
          require(
              cast.containsKey(part.getKey()) || stowed(part.getKey()),
              "Only stowed tackle is removed");
          geometry(part.getValue());
        }
        require(
            cast.get("flange_inner_middle").getAsJsonObject("faces").size() == 6,
            "The reel is a solid drum, not the inherited sprite plane");
        tip(idle, style + suffix + " idle");
        tip(cast, style + suffix + " cast");
        variants += 2;
      }
      mirror(parts(RodModels.model(style, true, "")), parts(RodModels.model(style, true, "_left")));
    }
    System.out.println(
        "Rod meshes: "
            + variants
            + " variants, cast tackle, mirrored drums, atlas UVs and native tip detection passed");
  }

  private static Map<String, JsonObject> parts(String source) {
    Map<String, JsonObject> parts = new LinkedHashMap<>();
    for (var value : JsonParser.parseString(source).getAsJsonObject().getAsJsonArray("elements")) {
      JsonObject part = value.getAsJsonObject();
      require(
          parts.put(part.get("name").getAsString(), part) == null,
          "Part IDs are unique within a mesh");
    }
    return parts;
  }

  private static boolean stowed(String name) {
    return name.startsWith("hanging_")
        || name.startsWith("bobber_")
        || name.startsWith("hook_")
        || name.equals("tip_return");
  }

  private static void geometry(JsonObject part) {
    JsonArray low = part.getAsJsonArray("from");
    JsonArray high = part.getAsJsonArray("to");
    for (int axis = 0; axis < 3; axis++) {
      float minimum = low.get(axis).getAsFloat();
      float maximum = high.get(axis).getAsFloat();
      require(
          minimum >= -16 && maximum <= 32 && maximum - minimum >= 0.05f,
          "Legal solid cuboids, including the reel");
    }
    require(part.getAsJsonObject("faces").size() == 6, "No sprite planes in the held mesh");
    for (var face : part.getAsJsonObject("faces").entrySet())
      for (var coordinate : face.getValue().getAsJsonObject().getAsJsonArray("uv"))
        require(
            coordinate.getAsFloat() >= 0 && coordinate.getAsFloat() <= 16,
            "Local atlas UVs stay in their texture");
  }

  private static void mirror(Map<String, JsonObject> right, Map<String, JsonObject> left) {
    require(right.keySet().equals(left.keySet()), "Both hands retain the same rod components");
    for (var entry : right.entrySet()) {
      JsonObject mirrored = left.get(entry.getKey());
      JsonArray low = entry.getValue().getAsJsonArray("from");
      JsonArray high = entry.getValue().getAsJsonArray("to");
      require(
          Math.abs(
                  mirrored.getAsJsonArray("from").get(0).getAsFloat()
                      + high.get(0).getAsFloat()
                      - 16)
              < 0.0001f,
          "Mirror the reel/crank around the shaft");
      require(
          Math.abs(mirrored.getAsJsonArray("to").get(0).getAsFloat() + low.get(0).getAsFloat() - 16)
              < 0.0001f,
          "Mirror the full reel thickness");
    }
  }

  private static void tip(Map<String, JsonObject> parts, String label) {
    List<int[]> vertices = new ArrayList<>();
    for (JsonObject part : parts.values()) {
      JsonArray low = part.getAsJsonArray("from");
      JsonArray high = part.getAsJsonArray("to");
      // Native modelTip consumes baked four-vertex quads with the vanilla eight-int stride.
      for (int side = 0; side < 2; side++) {
        int[] quad = new int[32];
        for (int corner = 0; corner < 4; corner++) {
          quad[corner * 8] =
              Float.floatToIntBits((corner % 2 == 0 ? low : high).get(0).getAsFloat() / 16);
          quad[corner * 8 + 1] =
              Float.floatToIntBits((corner < 2 ? low : high).get(1).getAsFloat() / 16);
          quad[corner * 8 + 2] =
              Float.floatToIntBits((side == 0 ? low : high).get(2).getAsFloat() / 16);
        }
        vertices.add(quad);
      }
    }
    Vector3f point = FishingLineProjection.modelTip(vertices);
    require(
        point != null && point.distance(new Vector3f(8 / 16f, 30.2f / 16, 11.55f / 16)) < 0.8f / 16,
        "The native line exits the final eye: " + label + " " + point);
  }

  private static void require(boolean passed, String message) {
    if (!passed) throw new AssertionError(message);
  }
}
