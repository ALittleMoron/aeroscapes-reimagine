package dev.aeroscapes.aquatic;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Native item states choose a model before NeoForge selects its display context. */
final class HeldItemModels {
  private HeldItemModels() {}

  static void anchorHeld(JsonObject display, Vector3f grip) {
    anchorHeld(display, grip, new Vector3f());
  }

  static void anchorHeld(JsonObject display, Vector3f grip, Vector3f target) {
    for (var entry : display.entrySet()) {
      String context = entry.getKey();
      if (!context.startsWith("firstperson_") && !context.startsWith("thirdperson_")) continue;
      JsonObject transform = entry.getValue().getAsJsonObject();
      float side = context.endsWith("lefthand") ? -1 : 1;
      JsonArray scale = transform.getAsJsonArray("scale");
      JsonArray rotation = transform.getAsJsonArray("rotation");
      Vector3f offset = new Vector3f(grip).sub(8, 8, 8);
      for (int axis = 0; axis < 3; axis++)
        offset.setComponent(axis, offset.get(axis) * scale.get(axis).getAsFloat());
      offset.rotate(
          new Quaternionf()
              .rotationXYZ(
                  (float) Math.toRadians(rotation.get(0).getAsFloat()),
                  (float) Math.toRadians(rotation.get(1).getAsFloat() * side),
                  (float) Math.toRadians(rotation.get(2).getAsFloat() * side)));
      JsonArray translation = new JsonArray();
      translation.add(target.x - offset.x * side);
      translation.add(target.y - offset.y);
      translation.add(target.z - offset.z);
      transform.add("translation", translation);
    }
  }

  static void resizeHeld(
      JsonObject display, float firstPersonScale, float thirdPersonScale, Vector3f grip) {
    for (var entry : display.entrySet()) {
      String context = entry.getKey();
      if (!context.startsWith("firstperson_") && !context.startsWith("thirdperson_")) continue;
      JsonObject transform = entry.getValue().getAsJsonObject();
      float scale = context.startsWith("firstperson_") ? firstPersonScale : thirdPersonScale;
      float side = context.endsWith("lefthand") ? -1 : 1;
      JsonArray oldScale = transform.getAsJsonArray("scale");
      JsonArray rotation = transform.getAsJsonArray("rotation");
      Vector3f offset = new Vector3f(grip).sub(8, 8, 8);
      for (int axis = 0; axis < 3; axis++)
        offset.setComponent(
            axis,
            offset.get(axis) * ((oldScale == null ? 1 : oldScale.get(axis).getAsFloat()) - scale));
      offset.rotate(
          new Quaternionf()
              .rotationXYZ(
                  (float) Math.toRadians(rotation.get(0).getAsFloat()),
                  (float) Math.toRadians(rotation.get(1).getAsFloat() * side),
                  (float) Math.toRadians(rotation.get(2).getAsFloat() * side)));
      offset.x *= side;
      JsonArray translation = transform.getAsJsonArray("translation");
      JsonArray moved = new JsonArray(), resized = new JsonArray();
      for (int axis = 0; axis < 3; axis++) {
        moved.add(translation.get(axis).getAsFloat() + offset.get(axis));
        resized.add(scale);
      }
      transform.add("translation", moved);
      transform.add("scale", resized);
    }
  }

  static void add(Path assets, Map<Path, byte[]> output, Map<String, JsonObject> meshes)
      throws IOException {
    for (var entry : meshes.entrySet()) {
      String[] id = entry.getKey().split(":", 2);
      String held = id[0] + ":item/equipment/held/" + id[1];
      JsonObject mesh = entry.getValue().deepCopy();
      mesh.remove("overrides");
      write(output, assets.resolve(id[0] + "/models/item/equipment/held/" + id[1] + ".json"), mesh);
      write(
          output,
          assets.resolve(id[0] + "/models/item/" + id[1] + ".json"),
          entry.getKey().equals("supplementaries:altimeter_template")
              ? nativeModel(entry.getKey())
              : wrapper(entry.getKey(), held, meshes.keySet()));
    }
  }

  static JsonObject wrapper(String id, String held, Set<String> replaced) throws IOException {
    JsonObject nativeModel = nativeModel(id);
    JsonObject wrapper = new JsonObject();
    wrapper.addProperty("loader", "neoforge:separate_transforms");
    wrapper.addProperty("gui_light", "front");
    JsonObject base = nativeBase(nativeModel, replaced);
    wrapper.add("base", base);
    if (base.has("textures")) {
      JsonObject textures = base.getAsJsonObject("textures").deepCopy();
      if (!textures.has("particle") && textures.has("layer0"))
        textures.addProperty("particle", "#layer0");
      wrapper.add("textures", textures);
    }
    JsonObject hands = new JsonObject();
    for (String hand :
        List.of(
            "firstperson_righthand", "firstperson_lefthand",
            "thirdperson_righthand", "thirdperson_lefthand")) {
      JsonObject model = new JsonObject();
      model.addProperty("parent", held);
      hands.add(hand, model);
    }
    wrapper.add("perspectives", hands);
    if (nativeModel.has("overrides"))
      wrapper.add("overrides", nativeModel.get("overrides").deepCopy());
    return wrapper;
  }

  private static JsonObject nativeBase(JsonObject model, Set<String> replaced) throws IOException {
    JsonObject base = new JsonObject();
    if (model.has("parent")) {
      String parent = model.get("parent").getAsString();
      if (replaced.contains(parent.replace(":item/", ":")))
        base = nativeBase(nativeModel(parent.replace(":item/", ":")), replaced);
    }
    for (var property : model.entrySet()) {
      String key = property.getKey();
      if (key.equals("overrides")
          || key.equals("parent")
              && replaced.contains(property.getValue().getAsString().replace(":item/", ":")))
        continue;
      if ((key.equals("textures") || key.equals("display")) && base.has(key)) {
        for (var child : property.getValue().getAsJsonObject().entrySet())
          base.getAsJsonObject(key).add(child.getKey(), child.getValue().deepCopy());
      } else base.add(key, property.getValue().deepCopy());
    }
    return base;
  }

  static JsonObject nativeModel(String id) throws IOException {
    String path = "/assets/" + id.replace(":", "/models/item/") + ".json";
    try (var stream = HeldItemModels.class.getResourceAsStream(path)) {
      if (stream == null) throw new IOException("Missing pinned native item model: " + id);
      try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        return JsonParser.parseReader(reader).getAsJsonObject();
      }
    }
  }

  private static void write(Map<Path, byte[]> output, Path path, JsonObject model) {
    EquipmentModels.text(
        output, path, new GsonBuilder().setPrettyPrinting().create().toJson(model) + "\n");
  }
}
