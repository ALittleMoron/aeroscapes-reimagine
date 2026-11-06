package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.VanillaEquipmentModelTest.Face;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Behavioral geometry contracts for the owner's second equipment review batch. */
public final class ReviewedEquipmentModelTest {
  private static final List<String> READABLE =
      List.of(
          "tide:climate_gauge",
          "tide:depth_meter",
          "tide:pocket_watch",
          "tide:enchanted_pocket_watch",
          "tide:fish_finder",
          "chipped:alchemy_book",
          "chipped:multimeter");

  private ReviewedEquipmentModelTest() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional preview");
    Path packs = Path.of(arguments[0]).resolve("pack/resourcepacks");
    Path assets = packs.resolve("aeroscapes-reimagine-refined-compat/assets");
    JsonObject preview = new JsonObject();
    List<String> ids = new ArrayList<>(READABLE);
    ids.addAll(
        List.of(
            "hybrid_aquatic:coral_hoe",
            "hybrid_aquatic:seashell_hoe",
            "tide:starlight_bow",
            "tide:starlight_bow_pulling_0",
            "tide:starlight_bow_pulling_1",
            "tide:starlight_bow_pulling_2",
            "naturalist:capture_net",
            "spawn:casting_net",
            "spawn:casting_net_full",
            "chipped:saw",
            "chipped:watering_can",
            "quark:abacus",
            "quark:pickarang",
            "quark:flamerang",
            "quark:trowel"));
    for (String id : ids) {
      JsonObject model = read(assets, id);
      List<Face> faces =
          model.has("loader")
              ? VanillaEquipmentModelTest.bakeObj(model, assets)
              : VanillaEquipmentModelTest.bakeCuboids(model);
      require(!faces.isEmpty(), "Visible baked geometry: " + id);
      for (String hand :
          List.of(
              "firstperson_righthand",
              "firstperson_lefthand",
              "thirdperson_righthand",
              "thirdperson_lefthand"))
        require(model.getAsJsonObject("display").has(hand), "Both held hands: " + id);
      if (READABLE.contains(id)) {
        VanillaEquipmentModelTest.noOverlaps(id, faces);
        for (boolean left : new boolean[] {false, true}) {
          JsonObject transform =
              model
                  .getAsJsonObject("display")
                  .getAsJsonObject("thirdperson_" + (left ? "lefthand" : "righthand"));
          JsonArray rotation = transform.getAsJsonArray("rotation");
          Matrix4f pose =
              new Matrix4f()
                  .rotateZ((float) Math.PI)
                  .rotateZ(radians(left ? 9 : -8.56324))
                  .rotateY(radians(left ? 4 : -6.46188))
                  .rotateX(radians(left ? -100 : -100.03983))
                  .rotateX(radians(-90))
                  .rotateY((float) Math.PI)
                  .rotateXYZ(
                      radians(rotation.get(0).getAsFloat()),
                      radians(rotation.get(1).getAsFloat() * (left ? -1 : 1)),
                      radians(rotation.get(2).getAsFloat() * (left ? -1 : 1)));
          Vector3f normal = pose.transformDirection(new Vector3f(0, 0, 1));
          require(
              normal.z > .9,
              "The instrument's South face is readable through the pinned Punchy hand frame: "
                  + id);
        }
      }
      JsonObject item = new JsonObject();
      item.add("textures", model.get("textures"));
      item.add("display", model.get("display"));
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
      item.add("quads", quads);
      preview.add(id, item);
    }
    JsonObject net = read(assets, "naturalist:capture_net");
    var handle = VanillaEquipmentModelTest.bakeObj(net, assets, "handle");
    var bag = VanillaEquipmentModelTest.bakeObj(net, assets, "mesh");
    require(
        extent(handle, 1, true) - extent(handle, 1, false) > 11
            && extent(bag, 2, true) - extent(bag, 2, false) > 6,
        "A long handle supports a deep mesh pouch");
    JsonObject idle = read(assets, "tide:starlight_bow"),
        drawn = read(assets, "tide:starlight_bow_pulling_2");
    require(
        VanillaEquipmentModelTest.bakeObj(idle, assets, "grip")
            .equals(VanillaEquipmentModelTest.bakeObj(drawn, assets, "grip")),
        "Bow grip stays fixed while drawing");
    var rest = VanillaEquipmentModelTest.bakeObj(idle, assets, "limbs");
    var flex = VanillaEquipmentModelTest.bakeObj(drawn, assets, "limbs");
    require(
        extent(rest, 1, true) - extent(flex, 1, true) > 2
            && extent(flex, 2, true) > extent(rest, 2, true) + .5,
        "Both bow limbs bend with the string");
    var string = VanillaEquipmentModelTest.bakeObj(drawn, assets, "string");
    var arrow = VanillaEquipmentModelTest.bakeObj(drawn, assets, "arrow");
    require(
        extent(string, 2, true) > 16 && extent(arrow, 2, true) - extent(arrow, 2, false) > 14,
        "Nock draws along positive Z and the arrow points away along negative Z");
    for (String tier : List.of("iron", "copper", "gold", "diamond", "netherite")) {
      String id = "yo_hooks:" + tier + "_hook_head";
      JsonObject wrapper =
          JsonParser.parseString(
                  Files.readString(
                      packs.resolve(
                          "aeroscapes-reimagine-atmosphere/assets/yo_hooks/models/item/"
                              + tier
                              + "_hook_head.json")))
              .getAsJsonObject();
      require(
          wrapper.getAsJsonObject("base").equals(HeldItemModels.nativeModel(id)),
          "Hook components retain their native generated flat icon: " + id);
      require(
          wrapper.getAsJsonObject("perspectives").size() == 4,
          "Only hands select the hook head sculpture");
    }
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), preview.toString());
    System.out.println(
        "Reviewed equipment: "
            + ids.size()
            + " baked states, seven readable displays, articulated bow, deep net and five flat hook component icons passed");
  }

  private static JsonObject read(Path assets, String id) throws IOException {
    return JsonParser.parseString(
            Files.readString(
                assets.resolve(id.replace(":", "/models/item/equipment/held/") + ".json")))
        .getAsJsonObject();
  }

  private static double extent(List<Face> faces, int axis, boolean high) {
    double value = high ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    for (Face face : faces)
      for (Vector3f point : face.vertices())
        value =
            high ? Math.max(value, point.get(axis) * 16) : Math.min(value, point.get(axis) * 16);
    return value;
  }

  private static float radians(double value) {
    return (float) Math.toRadians(value);
  }

  private static JsonArray vector(float... values) {
    JsonArray result = new JsonArray();
    for (float value : values) result.add(value);
    return result;
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
