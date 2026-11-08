package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

/** Geometry and native predicate contracts, without starting a Minecraft client. */
public final class ToolModelContractTest {
  private ToolModelContractTest() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 1)
      throw new IllegalArgumentException("Expected the pinned Minecraft 1.21.1 client asset JAR");
    Path vanillaArtifact = Path.of(arguments[0]);
    Map<String, JsonObject> models = ToolModels.models();
    for (var entry : models.entrySet()) geometry(entry.getKey(), entry.getValue());
    for (String id : models.keySet()) {
      if (id.equals("supplementaries:altimeter_template")) continue;
      JsonObject nativeModel = HeldItemModels.nativeModel(id);
      JsonObject wrapper =
          HeldItemModels.wrapper(id, id.replace(":", ":item/equipment/held/"), models.keySet());
      require(
          java.util.Objects.equals(nativeModel.get("overrides"), wrapper.get("overrides")),
          "Display routing preserves every native active-state predicate: " + id);
      require(
          wrapper.getAsJsonObject("perspectives").size() == 4,
          "Only the four held contexts select custom geometry: " + id);
      for (String context : List.of("gui", "ground", "fixed", "head", "none"))
        require(
            !wrapper.getAsJsonObject("perspectives").has(context),
            "Non-held contexts retain the original item model: " + id);
      if (!id.startsWith("supplementaries:slingshot_pulling")) {
        JsonObject expected = nativeModel.deepCopy();
        expected.remove("overrides");
        require(
            wrapper.getAsJsonObject("base").equals(expected),
            "Native inventory geometry, texture layers and transforms are unchanged: " + id);
      }
    }
    for (String id :
        List.of(
            "tide:starlight_bow",
            "spawn:coastal_crab_claw",
            "betterarcheology:iron_brush",
            "betterarcheology:diamond_brush",
            "betterarcheology:netherite_brush",
            "naturescompass:naturescompass",
            "explorerscompass:explorerscompass",
            "legendarysurvivaloverhaul:thermometer",
            "sophisticatedcore:ender_linker",
            "minecraft:clock",
            "minecraft:compass",
            "minecraft:recovery_compass",
            "minecraft:goat_horn",
            "horseman:copper_horn",
            "create:clipboard")) {
      JsonObject nativeModel = nativeModel(id, vanillaArtifact);
      require(
          nativeModel
              .getAsJsonArray("overrides")
              .equals(models.get(id).getAsJsonArray("overrides")),
          "Retain exact native predicates and all target model identities: " + id);
    }
    for (String id : List.of("tide:starlight_bow", "supplementaries:slingshot")) {
      JsonObject idle = models.get(id);
      if (id.equals("supplementaries:slingshot"))
        require(
            !idle.getAsJsonArray("elements")
                .equals(models.get(id + "_pulling_2").getAsJsonArray("elements")),
            "Fully drawn ranged equipment changes its solid geometry: " + id);
      for (var override : idle.getAsJsonArray("overrides")) {
        String target =
            override.getAsJsonObject().get("model").getAsString().replace(":item/", ":");
        require(models.containsKey(target), "Every active state resolves to solid owned geometry");
      }
    }
    require(
        !solid(models.get("supplementaries:altimeter_template"), 8, 11.5, 8.5)
            && solid(models.get("supplementaries:altimeter_template"), 8, 5.5, 8.5),
        "The housing leaves the complete native moving scale visible at y6..12");
    require(
        extent(models.get("supplementaries:slingshot_pulling_2"), 2, true)
            > extent(models.get("supplementaries:slingshot"), 2, true),
        "The sling pouch pulls away from the fork");
    for (String id : List.of("supplementaries:bubble_blower_using", "minecraft:tooting_goat_horn"))
      require(
          !models
              .get(id)
              .getAsJsonObject("display")
              .equals(
                  models
                      .get(
                          id.contains("bubble")
                              ? "supplementaries:bubble_blower"
                              : "minecraft:goat_horn")
                      .getAsJsonObject("display")),
          "Using retains its distinct native pose: " + id);
    boolean biomeTint = false;
    for (var part : models.get("quark:pathfinders_quill_with_biome").getAsJsonArray("elements"))
      for (var face : part.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
        var renderedFace = face.getValue().getAsJsonObject();
        if (renderedFace.get("texture").getAsString().equals("#layer1"))
          biomeTint |= renderedFace.get("tintindex").getAsInt() == 1;
      }
    require(biomeTint, "The biome-selected quill retains its native second tint layer");
    System.out.println(
        "Equipment: "
            + models.size()
            + " solid meshes, native material references, motifs and active states passed");
  }

  private static void geometry(String id, JsonObject model) {
    if (ArchaeologyBrushModels.supports(id)) return; // The existing provider owns the cuboids.
    if (model.has("loader") && model.get("loader").getAsString().equals("neoforge:composite")) {
      require(
          id.equals("create_aeronautics_throwable_rope_connector:throwable_rope_connector")
              || id.equals("galosphere:spectre_flare"),
          "Composite equipment has its real components and item passes checked by UtilityEquipmentModelTest");
      return;
    }
    if (model.has("loader") && model.get("loader").getAsString().equals("neoforge:obj")) {
      require(
          AquaticToolModels.supports(id)
              || VanillaEquipmentMesh.supports(id)
              || KnifeEquipmentModels.supports(id)
              || ReviewedEquipmentModels.supports(id)
              || CreateEquipmentModels.supports(id)
              || GalosphereEquipmentModels.supports(id)
              || UtilityEquipmentModels.supports(id),
          "Only registered authored model families use OBJ: " + id);
      return; // Their actual parsed/baked surfaces are checked by VanillaEquipmentModelTest.
    }
    JsonArray parts = model.getAsJsonArray("elements");
    require(
        parts != null && parts.size() > 2, "Functional equipment has a constructed shape: " + id);

    for (var value : parts) {
      JsonObject part = value.getAsJsonObject();
      JsonArray low = part.getAsJsonArray("from");
      JsonArray high = part.getAsJsonArray("to");
      for (int axis = 0; axis < 3; axis++) {
        double minimum = low.get(axis).getAsDouble();
        double maximum = high.get(axis).getAsDouble();
        require(
            minimum >= -16 && maximum <= 32 && maximum > minimum,
            "Baked geometry has legal solid dimensions: " + id);
      }
      require(!part.getAsJsonObject("faces").isEmpty(), "A rendered surface has faces: " + id);
      if (part.has("rotation"))
        require(
            List.of(-45.0, -22.5, 0.0, 22.5, 45.0)
                .contains(part.getAsJsonObject("rotation").get("angle").getAsDouble()),
            "Legal baked element rotation: " + id);
      for (var face : part.getAsJsonObject("faces").entrySet()) {
        JsonObject renderedFace = face.getValue().getAsJsonObject();
        if (renderedFace.get("texture").getAsString().equals("#layer0"))
          require(
              renderedFace.get("tintindex").getAsInt() == 0,
              "Native icon materials retain dye/component item colors: " + id);
        for (var coordinate : face.getValue().getAsJsonObject().getAsJsonArray("uv"))
          require(
              coordinate.getAsDouble() >= 0 && coordinate.getAsDouble() <= 16,
              "UV samples stay within the referenced native icon: " + id);
      }
    }
    for (String hand :
        List.of(
            "firstperson_righthand",
            "firstperson_lefthand",
            "thirdperson_righthand",
            "thirdperson_lefthand"))
      require(
          model.getAsJsonObject("display").has(hand),
          "Both hands have explicit grip transforms: " + id);
  }

  private static double extent(JsonObject model, int axis, boolean high) {
    double result = high ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    for (var part : model.getAsJsonArray("elements")) {
      double value =
          part.getAsJsonObject().getAsJsonArray(high ? "to" : "from").get(axis).getAsDouble();
      result = high ? Math.max(result, value) : Math.min(result, value);
    }
    return result;
  }

  private static boolean solid(JsonObject model, double x, double y, double z) {
    for (var value : model.getAsJsonArray("elements")) {
      JsonObject part = value.getAsJsonObject();
      double[] point = {x, y, z};
      if (part.has("rotation")) {
        var rotation = part.getAsJsonObject("rotation");
        String axis = rotation.get("axis").getAsString();
        int a = axis.equals("x") ? 1 : axis.equals("y") ? 2 : 0,
            b = axis.equals("x") ? 2 : axis.equals("y") ? 0 : 1;
        var origin = rotation.getAsJsonArray("origin");
        double u = point[a] - origin.get(a).getAsDouble(),
            v = point[b] - origin.get(b).getAsDouble(),
            angle = Math.toRadians(-rotation.get("angle").getAsDouble());
        point[a] = origin.get(a).getAsDouble() + u * Math.cos(angle) - v * Math.sin(angle);
        point[b] = origin.get(b).getAsDouble() + u * Math.sin(angle) + v * Math.cos(angle);
      }
      boolean inside = true;
      for (int axis = 0; axis < 3; axis++)
        inside &=
            point[axis] > part.getAsJsonArray("from").get(axis).getAsDouble()
                && point[axis] < part.getAsJsonArray("to").get(axis).getAsDouble();
      if (inside) return true;
    }
    return false;
  }

  private static JsonObject nativeModel(String id, Path vanillaArtifact) throws IOException {
    String path = "/assets/" + id.replace(":", "/models/item/") + ".json";
    if (id.startsWith("minecraft:")) {
      try (ZipFile archive = new ZipFile(vanillaArtifact.toFile())) {
        var version = archive.getEntry("version.json");
        if (version == null) throw new IOException("Missing version in pinned vanilla asset JAR");
        try (var reader =
            new InputStreamReader(archive.getInputStream(version), StandardCharsets.UTF_8)) {
          require(
              JsonParser.parseReader(reader)
                  .getAsJsonObject()
                  .get("id")
                  .getAsString()
                  .equals("1.21.1"),
              "Vanilla contracts must use Minecraft 1.21.1 assets");
        }
        var entry = archive.getEntry(path.substring(1));
        if (entry == null) throw new IOException("Missing pinned vanilla model " + id);
        try (var reader =
            new InputStreamReader(archive.getInputStream(entry), StandardCharsets.UTF_8)) {
          return JsonParser.parseReader(reader).getAsJsonObject();
        }
      }
    }
    try (var stream = ToolModelContractTest.class.getResourceAsStream(path)) {
      if (stream == null) throw new IOException("Missing pinned provider model " + id);
      try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        return JsonParser.parseReader(reader).getAsJsonObject();
      }
    }
  }

  private static void require(boolean passed, String message) {
    if (!passed) throw new AssertionError(message);
  }
}
