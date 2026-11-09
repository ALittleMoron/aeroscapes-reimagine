package dev.aeroscapes.aquatic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.VanillaEquipmentModelTest.Face;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import punchy.client.renderer.layer.VanillaFirstPersonItemLayer.ToolKind;
import punchy.config.PunchyTuningConfig;

/** Exercises the native model graph and baker for each field-equipment state. */
public final class FieldEquipmentModelTest {
  private static final List<String> HANDS =
      List.of(
          "firstperson_righthand", "firstperson_lefthand",
          "thirdperson_righthand", "thirdperson_lefthand");

  private FieldEquipmentModelTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional preview output");
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    JsonObject previews = arguments.length == 2 ? new JsonObject() : null;
    Set<String> states = new LinkedHashSet<>();
    JsonObject profiles =
        read(assets, "minecraft/punchy/compat/aeroscapes_field_equipment.json")
            .getAsJsonObject("itemSpecific");
    require(
        profiles.keySet().equals(Set.copyOf(FieldEquipmentModels.ROOTS)),
        "Only new item poses change");
    for (String id : FieldEquipmentModels.ROOTS) {
      var sides =
          new Gson()
              .fromJson(
                  profiles.getAsJsonObject(id).get("armTransforms"),
                  PunchyTuningConfig.SideSet.class);
      var right = sides.right.toArm();
      var left = sides.left.toArm();
      require(
          right.scale == 1 && left.scale == 1 && right.offY == left.offY,
          id + " lifts both native arms without resizing");
      verify(assets, id, states, previews, right.offY);
    }
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), previews.toString());
    System.out.println("Field equipment: " + states.size() + " native model states baked");
  }

  private static void verify(
      Path assets, String id, Set<String> states, JsonObject previews, float armOffsetY)
      throws IOException, ReflectiveOperationException {
    if (!states.add(id)) return;
    String[] key = id.split(":", 2);
    String root = key[0] + "/models/item/";
    String held = "equipment/held/" + key[1];
    JsonObject wrapper = read(assets, root + key[1] + ".json");
    JsonObject original = HeldItemModels.nativeModel(id);
    var overrides = original.remove("overrides");
    require(original.equals(wrapper.get("base")), id + " preserves non-hand presentation");
    require(
        java.util.Objects.equals(overrides, wrapper.get("overrides")),
        id + " preserves predicates");
    require(
        wrapper.get("loader").getAsString().equals("neoforge:separate_transforms"),
        id + " selects per display context");
    JsonObject hands = wrapper.getAsJsonObject("perspectives");
    require(hands.keySet().equals(Set.copyOf(HANDS)), id + " selects volume only in hands");
    for (String hand : HANDS)
      require(
          hands.getAsJsonObject(hand).get("parent").getAsString().equals(key[0] + ":item/" + held),
          id + " uses the correct held state in " + hand);

    JsonObject model = read(assets, root + held + ".json");
    require(
        model.getAsJsonObject("display").keySet().equals(Set.copyOf(HANDS)),
        id + " defines both hands in both perspectives");
    boolean composite = model.get("loader").getAsString().equals("neoforge:composite");
    List<Face> faces =
        composite
            ? VanillaEquipmentModelTest.bakeComposite(model, assets)
            : VanillaEquipmentModelTest.bakeObj(model, assets);
    require(!faces.isEmpty(), id + " has visible baked geometry");
    VanillaEquipmentModelTest.noOverlaps(id, faces);
    ToolKind kind =
        switch (id) {
          case "minecraft:bucket", "minecraft:water_bucket", "minecraft:lava_bucket" ->
              ToolKind.BUCKET;
          case "brewinandchewin:mead", "brewinandchewin:kombucha" -> ToolKind.FOOD;
          case "comforts:sleeping_bag_red",
              "hybrid_aquatic:bell_buoy",
              "hybrid_aquatic:message_in_a_bottle" ->
              ToolKind.BLOCK;
          default -> ToolKind.INGREDIENT;
        };
    JsonArray attachments = ConstructionPlacementTest.attachments(model, key[1], kind, armOffsetY);
    inNeutralFrame(id, faces, attachments);
    if (previews != null) {
      JsonObject preview = VanillaEquipmentModelTest.preview(model, faces);
      preview.add("attachments", attachments);
      previews.add(id, preview);
    }
    if (overrides != null) {
      for (var variant : overrides.getAsJsonArray()) {
        String target = variant.getAsJsonObject().get("model").getAsString();
        if (!target.contains(":")) target = "minecraft:" + target;
        verify(assets, target.replace(":item/", ":"), states, previews, armOffsetY);
      }
    }
  }

  private static void inNeutralFrame(String id, List<Face> faces, JsonArray attachments) {
    double vertical = Math.tan(Math.toRadians(35)), horizontal = vertical * 16 / 9;
    for (var attachment : attachments) {
      JsonArray rows = attachment.getAsJsonObject().getAsJsonArray("item");
      var matrix = new org.joml.Matrix4f();
      for (int row = 0; row < 4; row++)
        for (int column = 0; column < 4; column++)
          matrix.set(column, row, rows.get(row).getAsJsonArray().get(column).getAsFloat());
      for (Face face : faces)
        for (var vertex : face.vertices()) {
          var point = matrix.transformPosition(new org.joml.Vector3f(vertex).sub(.5f, .5f, .5f));
          require(
              point.z < 0
                  && Math.abs(point.x) < -point.z * horizontal
                  && Math.abs(point.y) < -point.z * vertical,
              id + " fits both recorded neutral Punchy camera frames");
        }
    }
  }

  private static JsonObject read(Path assets, String path) throws IOException {
    return JsonParser.parseString(Files.readString(assets.resolve(path))).getAsJsonObject();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
