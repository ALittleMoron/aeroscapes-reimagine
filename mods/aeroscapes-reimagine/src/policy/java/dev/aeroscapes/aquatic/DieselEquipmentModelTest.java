package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.VanillaEquipmentModelTest.Face;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.joml.Vector3f;

/** Exercises every native Diesel tool state through the actual NeoForge OBJ baker. */
public final class DieselEquipmentModelTest {
  private DieselEquipmentModelTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional baked preview");
    net.neoforged.fml.loading.LoadingModList.of(
        List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
    net.minecraft.SharedConstants.tryDetectVersion();
    net.minecraft.server.Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    JsonObject preview = new JsonObject();
    var arms =
        new com.google.gson.Gson()
            .fromJson(
                read(assets.resolve("minecraft/punchy/compat/aeroscapes_diesel_equipment.json"))
                    .getAsJsonObject("itemSpecific")
                    .getAsJsonObject("createdieselgenerators:track_layers_bag")
                    .get("armTransforms"),
                punchy.config.PunchyTuningConfig.SideSet.class);
    var rightArm = arms.right.toArm();
    var leftArm = arms.left.toArm();
    require(
        rightArm.scale == 1 && leftArm.scale == 1 && rightArm.offY == leftArm.offY,
        "The carry pose lifts both arms without changing the bag's size");
    for (String item : DieselEquipmentModels.items()) {
      String id = "createdieselgenerators:" + item;
      JsonObject model =
          read(
              assets.resolve(
                  "createdieselgenerators/models/item/equipment/held/" + item + ".json"));
      List<Face> faces = VanillaEquipmentModelTest.bakeObj(model, assets);
      require(!faces.isEmpty(), "Every native state has actual baked geometry: " + id);
      for (Face face : faces) {
        require(
            Float.isFinite(face.normal().x)
                && Float.isFinite(face.normal().y)
                && Float.isFinite(face.normal().z),
            "Finite surface normals: " + id);
        for (Vector3f vertex : face.vertices())
          require(
              Float.isFinite(vertex.x) && Float.isFinite(vertex.y) && Float.isFinite(vertex.z),
              "Finite baked vertices: " + id);
      }
      JsonObject wrapper =
          read(assets.resolve("createdieselgenerators/models/item/" + item + ".json"));
      JsonObject nativeModel = HeldItemModels.nativeModel(id);
      require(
          wrapper.getAsJsonObject("base").equals(withoutOverrides(nativeModel)),
          "GUI, ground and fixed retain the complete native model: " + id);
      require(
          java.util.Objects.equals(wrapper.get("overrides"), nativeModel.get("overrides")),
          "Exact native state predicates and targets: " + id);
      for (String hand :
          List.of(
              "firstperson_righthand",
              "firstperson_lefthand",
              "thirdperson_righthand",
              "thirdperson_lefthand"))
        require(
            wrapper
                .getAsJsonObject("perspectives")
                .getAsJsonObject(hand)
                .get("parent")
                .getAsString()
                .equals("createdieselgenerators:item/equipment/held/" + item),
            "All four hand contexts select the physical state: " + id);
      String name = item.replace('/', '_');
      Path material =
          assets.resolve("aeroscapes_reimagine/textures/item/diesel_equipment/" + name + ".png");
      BufferedImage image = ImageIO.read(material.toFile());
      require(
          image != null && image.getWidth() == 64 && image.getHeight() % 32 == 0,
          "Valid complete material frames: " + id);
      for (int y = 0; y < image.getHeight(); y++)
        for (int x = 0; x < image.getWidth(); x++)
          require(
              (image.getRGB(x, y) >>> 24) == 255
                  || item.contains("/glass")
                      && (x < 16 && y % 32 < 16 || x >= 32 && x < 48 && y % 32 >= 16)
                      && (image.getRGB(x, y) >>> 24) == 96,
              "Opaque structure or intentional translucent glass panels: " + id);
      if (image.getHeight() > 32) {
        JsonObject animation = read(Path.of(material + ".mcmeta")).getAsJsonObject("animation");
        require(
            animation.get("width").getAsInt() == 64 && animation.get("height").getAsInt() == 32,
            "Animated face and flame use physical material-frame dimensions: " + id);
        int[] first = image.getRGB(0, 0, 64, 32, null, 0, 64);
        boolean changes = false;
        for (int frame = 1; frame < image.getHeight() / 32; frame++)
          changes |=
              !java.util.Arrays.equals(first, image.getRGB(0, frame * 32, 64, 32, null, 0, 64));
        require(changes, "Native animated face/flame frames remain visibly different: " + id);
      }
      JsonObject rendered = preview(model, faces);
      rendered.add(
          "attachments",
          ConstructionPlacementTest.attachments(
              model,
              "diesel_" + item,
              punchy.client.renderer.layer.VanillaFirstPersonItemLayer.ToolKind.INGREDIENT,
              item.startsWith("track_layers_bag") ? rightArm.offY : 0));
      if (item.startsWith("oil_scanner"))
        for (var attachment : rendered.getAsJsonArray("attachments")) {
          JsonArray rows = attachment.getAsJsonObject().getAsJsonArray("item");
          require(
              rows.get(2).getAsJsonArray().get(2).getAsDouble() > .6,
              "The live gauge face points toward the holder in both actual hand frames: " + id);
        }
      if (item.startsWith("track_layers_bag")) {
        FieldEquipmentModelTest.inNeutralFrame(id, faces, rendered.getAsJsonArray("attachments"));
        bagGrip(faces, rendered.getAsJsonArray("attachments"));
      }
      preview.add(id, rendered);
    }
    nativeSkins(preview);
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), preview.toString());
    System.out.println("Diesel equipment native states and baked meshes passed: " + preview.size());
  }

  private static void bagGrip(List<Face> faces, JsonArray attachments) {
    for (var entry : attachments) {
      var attachment = entry.getAsJsonObject();
      var item = matrix(attachment.getAsJsonArray("item"));
      var skin = matrix(attachment.getAsJsonArray("skin"));
      var relative = skin.invert().mul(item);
      var contact = relative.transformPosition(new Vector3f(0, 6.55f / 16, 0));
      boolean inside = false;
      for (var bounds : attachment.getAsJsonArray("nativeArmCubes")) {
        var from = bounds.getAsJsonObject().getAsJsonArray("from");
        var to = bounds.getAsJsonObject().getAsJsonArray("to");
        inside |=
            contact.x * 16 >= from.get(0).getAsFloat()
                && contact.x * 16 <= to.get(0).getAsFloat()
                && contact.y * 16 >= Math.max(8, from.get(1).getAsFloat())
                && contact.y * 16 <= to.get(1).getAsFloat()
                && contact.z * 16 >= from.get(2).getAsFloat()
                && contact.z * 16 <= to.get(2).getAsFloat();
      }
      require(inside, "The native fist encloses the bag's carry handle: " + contact);
      // Body, rim and rail contents end below the upper carry loop at y=13.8.
      for (Face face : faces) {
        if (face.vertices().stream().anyMatch(vertex -> vertex.y * 16 > 13.81f)) continue;
        for (var vertex : face.vertices()) {
          var body = relative.transformPosition(new Vector3f(vertex).sub(.5f, .5f, .5f));
          require(body.z > 2.1f / 16, "The bag body and contents clear the entire native fist");
        }
      }
    }
  }

  private static org.joml.Matrix4f matrix(JsonArray rows) {
    var matrix = new org.joml.Matrix4f();
    for (int row = 0; row < 4; row++)
      for (int column = 0; column < 4; column++)
        matrix.set(column, row, rows.get(row).getAsJsonArray().get(column).getAsFloat());
    return matrix.determineProperties();
  }

  private static void nativeSkins(JsonObject preview) throws IOException {
    try (var stream =
        DieselEquipmentModelTest.class.getResourceAsStream(
            "/assets/createdieselgenerators/lighter_skins.json")) {
      if (stream == null) throw new IOException("Missing pinned native lighter skin registry");
      JsonArray skins =
          JsonParser.parseString(
                  new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
              .getAsJsonArray();
      for (var skin : skins)
        for (String suffix : List.of("", "_open", "_ignited")) {
          String id =
              "createdieselgenerators:lighter/"
                  + skin.getAsJsonObject().get("id").getAsString()
                  + suffix;
          require(
              preview.has(id),
              "Every provider-registered named skin/state has a baked held child: " + id);
        }
    }
  }

  private static JsonObject withoutOverrides(JsonObject model) {
    JsonObject copy = model.deepCopy();
    copy.remove("overrides");
    return copy;
  }

  private static JsonObject read(Path path) throws IOException {
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  private static JsonObject preview(JsonObject model, List<Face> faces) {
    JsonObject result = new JsonObject();
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
      quads.add(quad);
    }
    result.add("quads", quads);
    return result;
  }

  private static JsonArray vector(float x, float y, float z) {
    JsonArray array = new JsonArray();
    array.add(x);
    array.add(y);
    array.add(z);
    return array;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
