package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.VanillaEquipmentModelTest.Face;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Native state graphs, actual baked faces and functional hand frames of utility equipment. */
public final class UtilityEquipmentModelTest {
  private static final List<String> ROOTS =
      List.of(
          "naturalist:capture_net",
          "spawn:casting_net",
          "chipped:watering_can",
          "create:clipboard",
          "create:schematic_and_quill",
          "create:super_glue",
          "simulated:honey_glue",
          "galosphere:echo_bell",
          "galosphere:glow_flare",
          "galosphere:monstrometer",
          "galosphere:rope_dart",
          "galosphere:saltbound_tablet",
          "galosphere:spectre_flare",
          "supplementaries:wrench",
          "toms_storage:wireless_terminal",
          "toms_storage:adv_wireless_terminal",
          "toms_storage:inventory_configurator",
          "toms_storage:paint_kit",
          "sophisticatedcore:ender_linker",
          "sophisticatedstorage:storage_tool",
          "explorerscompass:explorerscompass",
          "naturescompass:naturescompass",
          "legendarysurvivaloverhaul:thermometer",
          "horseman:copper_horn",
          "exposure:interplanar_projector",
          "waystones:warp_stone");

  private UtilityEquipmentModelTest() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional baked preview");
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    JsonObject preview = new JsonObject();
    int count = 0;
    for (String root : ROOTS) {
      Set<String> states = new LinkedHashSet<>();
      states(root, states);
      var style = NativeEquipmentModels.designs().get(root);
      for (String id : states) {
        JsonObject model = read(assets, id);
        List<Face> faces =
            model.has("loader")
                ? VanillaEquipmentModelTest.bakeObj(model, assets)
                : VanillaEquipmentModelTest.bakeCuboids(model);
        require(!faces.isEmpty(), "Visible utility model: " + id);
        VanillaEquipmentModelTest.noOverlaps(id, faces);
        if (EquipmentSculpture.readable(style)
            || style == dev.aeroscapes.atmosphere.EquipmentStyle.WRENCH
            || style == dev.aeroscapes.atmosphere.EquipmentStyle.GLUE) readable(id, model);
        if (UtilityEquipmentModels.supports(id)) skin(id);
        if (id.equals(root)) preview.add(id, preview(model, faces));
        count++;
      }
    }
    for (String id : List.of("spawn:casting_net", "spawn:casting_net_full")) {
      var net = read(assets, id);
      var threads = VanillaEquipmentModelTest.bakeObj(net, assets, "mesh");
      var weights = VanillaEquipmentModelTest.bakeObj(net, assets, "weights");
      for (Face face : weights)
        for (Vector3f point : face.vertices()) {
          double distance = Double.POSITIVE_INFINITY;
          for (Face cord : threads)
            for (Vector3f vertex : cord.vertices())
              distance = Math.min(distance, point.distance(vertex) * 16);
          require(distance < .6, "Every net weight is attached to a thread node: " + id);
        }
    }
    JsonObject storageTool = read(assets, "sophisticatedstorage:storage_tool");
    var wood = VanillaEquipmentModelTest.bakeObj(storageTool, assets, "wood_handle");
    for (String jaw : List.of("left_jaw", "right_jaw"))
      require(
          touches(VanillaEquipmentModelTest.bakeObj(storageTool, assets, jaw), wood),
          "Both dark tool jaws attach to the wooden neck: " + jaw);
    JsonObject watering = read(assets, "chipped:watering_can");
    var rim = VanillaEquipmentModelTest.bakeObj(watering, assets, "rim");
    require(!rim.isEmpty(), "A continuous annular watering-can rim exists");
    for (Face face : rim)
      for (Vector3f point : face.vertices())
        require(
            Math.hypot(point.x * 16 - 8, point.z * 16 - 8) <= 3.201,
            "The watering rim has no radial fins");
    connector(assets);
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), preview.toString());
    System.out.println(
        "Utility equipment: "
            + ROOTS.size()
            + " roots, "
            + count
            + " native states baked; source skins, readable hand faces, non-overlapping surfaces and connector placement passed");
  }

  private static boolean touches(List<Face> part, List<Face> base) {
    double distance = Double.POSITIVE_INFINITY;
    for (Face face : part)
      for (Vector3f point : face.vertices())
        for (Face target : base)
          for (int edge = 0; edge < 4; edge++) {
            Vector3f a = target.vertices().get(edge),
                b = target.vertices().get((edge + 1) % 4),
                direction = new Vector3f(b).sub(a);
            if (direction.lengthSquared() == 0) continue;
            float along =
                Math.clamp(
                    new Vector3f(point).sub(a).dot(direction) / direction.lengthSquared(), 0, 1);
            distance =
                Math.min(distance, point.distance(new Vector3f(a).fma(along, direction)) * 16);
          }
    return distance < .001;
  }

  @SuppressWarnings("deprecation") // Exercises the pinned provider's 1.21.1 item transforms.
  private static void connector(Path assets) throws IOException {
    JsonObject actual =
        JsonParser.parseString(
                Files.readString(assets.resolve("simulated/models/item/rope_connector.json")))
            .getAsJsonObject();
    JsonObject original = HeldItemModels.nativeModel("simulated:rope_connector");
    require(
        actual.get("parent").equals(original.get("parent")),
        "The connector retains its native mesh and non-hand presentation");
    String path =
        "/assets/" + original.get("parent").getAsString().replace(":", "/models/") + ".json";
    JsonObject source;
    try (var stream = UtilityEquipmentModelTest.class.getResourceAsStream(path)) {
      if (stream == null) throw new IOException("Missing native connector model: " + path);
      try (var reader =
          new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
        source = JsonParser.parseReader(reader).getAsJsonObject();
      }
    }
    JsonObject replacement = source.deepCopy();
    for (var hand : actual.getAsJsonObject("display").entrySet())
      replacement.getAsJsonObject("display").add(hand.getKey(), hand.getValue());
    var nativeModel =
        net.minecraft.client.renderer.block.model.BlockModel.fromString(source.toString());
    var held =
        net.minecraft.client.renderer.block.model.BlockModel.fromString(replacement.toString());
    double minimum =
        nativeModel.getElements().stream().mapToDouble(part -> part.from.y).min().orElseThrow();
    double maximum =
        nativeModel.getElements().stream().mapToDouble(part -> part.to.y).max().orElseThrow();
    for (var context :
        List.of(
            net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
      boolean left =
          context == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
              || context == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
      var reference = new com.mojang.blaze3d.vertex.PoseStack();
      var corrected = new com.mojang.blaze3d.vertex.PoseStack();
      nativeModel.getTransforms().getTransform(context).apply(left, reference);
      held.getTransforms().getTransform(context).apply(left, corrected);
      Vector3f target = reference.last().pose().transformPosition(new Vector3f());
      Vector3f center =
          corrected
              .last()
              .pose()
              .transformPosition(new Vector3f(0, (float) (((minimum + maximum) / 2 - 8) / 16), 0));
      require(
          center.distance(target) < .00001,
          "The low connector center reaches its native hand anchor in " + context);
    }
  }

  private static void states(String id, Set<String> states) throws IOException {
    if (!states.add(id)) return;
    var original = HeldItemModels.nativeModel(id);
    if (original.has("overrides"))
      for (var override : original.getAsJsonArray("overrides"))
        states(
            override.getAsJsonObject().get("model").getAsString().replace(":item/", ":"), states);
  }

  private static void skin(String id) throws IOException {
    String reference =
        NativeEquipmentModels.inherited(id).getAsJsonObject("textures").get("layer0").getAsString();
    if (id.contains("compass:")) reference = reference.replaceAll("_[0-9]{2}$", "_16");
    var image = NativeEquipmentModels.image(reference);
    double scale = image.getWidth() / 16.0;
    for (var entry : UtilityEquipmentModels.regions(id).entrySet()) {
      if (entry.getKey().equals("native")) continue;
      double[] region = entry.getValue();
      for (int y = (int) (region[1] * scale); y < region[3] * scale; y++)
        for (int x = (int) (region[0] * scale); x < region[2] * scale; x++)
          require(
              (image.getRGB(x, y) >>> 24) == 255,
              "A physical material cannot cut holes in its opaque shell: "
                  + id
                  + "/"
                  + entry.getKey());
    }
  }

  private static void readable(String id, JsonObject model) {
    for (boolean left : new boolean[] {false, true}) {
      JsonObject transform =
          model
              .getAsJsonObject("display")
              .getAsJsonObject("thirdperson_" + (left ? "lefthand" : "righthand"));
      JsonArray angles = transform.getAsJsonArray("rotation");
      Matrix4f pose =
          new Matrix4f()
              .rotateZ(radians(180))
              .rotateZ(radians(left ? 9 : -8.56324))
              .rotateY(radians(left ? 4 : -6.46188))
              .rotateX(radians(left ? -100 : -100.03983))
              .rotateX(radians(-90))
              .rotateY(radians(180))
              .rotateXYZ(
                  radians(angles.get(0).getAsDouble()),
                  radians(angles.get(1).getAsDouble() * (left ? -1 : 1)),
                  radians(angles.get(2).getAsDouble() * (left ? -1 : 1)));
      require(
          pose.transformDirection(new Vector3f(0, 0, 1)).z > .9,
          "The authored front faces the owner in both Punchy hand frames: " + id);
    }
  }

  private static JsonObject read(Path assets, String id) throws IOException {
    return JsonParser.parseString(
            Files.readString(
                assets.resolve(id.replace(":", "/models/item/equipment/held/") + ".json")))
        .getAsJsonObject();
  }

  private static JsonObject preview(JsonObject model, List<Face> faces) {
    JsonObject result = new JsonObject();
    result.add("textures", model.get("textures"));
    result.add("display", model.get("display"));
    JsonArray quads = new JsonArray();
    for (Face face : faces) {
      JsonObject quad = new JsonObject();
      quad.addProperty("texture", face.texture());
      JsonArray vertices = new JsonArray();
      for (Vector3f point : face.vertices())
        vertices.add(vector(point.x * 16, point.y * 16, point.z * 16));
      quad.add("vertices", vertices);
      quad.add("uv", face.uv());
      quad.add("normal", vector(face.normal().x, face.normal().y, face.normal().z));
      quads.add(quad);
    }
    result.add("quads", quads);
    return result;
  }

  private static JsonArray vector(float... values) {
    JsonArray result = new JsonArray();
    for (float value : values) result.add(value);
    return result;
  }

  private static float radians(double value) {
    return (float) Math.toRadians(value);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
