package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

/** Checks the provider's complete useful farming-item state graph through the native OBJ baker. */
public final class FarmingEquipmentModelTest {
  private static final Set<String> HANDS =
      Set.of(
          "firstperson_righthand", "firstperson_lefthand",
          "thirdperson_righthand", "thirdperson_lefthand");

  private FarmingEquipmentModelTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional baked preview output");
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    Set<String> states = new LinkedHashSet<>();
    for (String root : FarmingEquipmentModels.ROOTS) collect(root, states);
    require(
        states.equals(new LinkedHashSet<>(FarmingEquipmentModels.STATES)),
        "Every reachable native farming model state has authored volume");
    JsonObject previews = new JsonObject();
    for (String state : states) {
      String[] id = state.split(":", 2);
      JsonObject wrapper = read(assets, id[0] + "/models/item/" + id[1] + ".json");
      JsonObject nativeModel = HeldItemModels.nativeModel(state);
      var overrides = nativeModel.remove("overrides");
      require(
          nativeModel.equals(wrapper.get("base")),
          state + " retains native GUI, ground and fixed models");
      require(
          java.util.Objects.equals(overrides, wrapper.get("overrides")),
          state + " retains native state predicates");
      require(
          wrapper.get("loader").getAsString().equals("neoforge:separate_transforms"),
          state + " selects separate hand presentation");
      require(
          wrapper.getAsJsonObject("perspectives").keySet().equals(HANDS),
          state + " replaces only all four hand contexts");
      String held = id[0] + ":item/equipment/held/" + id[1];
      for (String hand : HANDS)
        require(
            wrapper
                .getAsJsonObject("perspectives")
                .getAsJsonObject(hand)
                .get("parent")
                .getAsString()
                .equals(held),
            state + " resolves its own held state");
      JsonObject model = read(assets, id[0] + "/models/item/equipment/held/" + id[1] + ".json");
      require(
          model.getAsJsonObject("display").keySet().equals(HANDS),
          state + " defines both hand poses");
      var faces = VanillaEquipmentModelTest.bakeObj(model, assets);
      require(!faces.isEmpty(), state + " produces actual native-baked geometry");
      VanillaEquipmentModelTest.noOverlaps(state, faces);
      for (var face : faces) {
        require(
            Float.isFinite(face.normal().x + face.normal().y + face.normal().z),
            state + " has finite normals");
        for (var coordinates : face.uv())
          for (var value : coordinates.getAsJsonArray())
            require(
                value.getAsDouble() >= 0 && value.getAsDouble() <= 16,
                state + " uses atlas-safe material regions");
      }
      JsonObject preview = VanillaEquipmentModelTest.preview(model, faces);
      JsonArray attachments =
          ConstructionPlacementTest.attachments(model, id[1], ToolKind.INGREDIENT, 0);
      if (state.equals("animalhusbandry:brush")) {
        float tipHeight = Float.POSITIVE_INFINITY;
        for (var face : faces)
          for (var vertex : face.vertices()) tipHeight = Math.min(tipHeight, vertex.y);
        var workingNormal = new org.joml.Vector3f();
        for (var face : faces) {
          boolean tip = true;
          for (var vertex : face.vertices()) tip &= Math.abs(vertex.y - tipHeight) < .000001;
          if (tip) workingNormal.add(face.normal());
        }
        require(workingNormal.lengthSquared() > 0, "The baked bristle tips have working surfaces");
        workingNormal.normalize();
        for (var element : attachments) {
          JsonObject attachment = element.getAsJsonObject();
          JsonArray rows = attachment.getAsJsonArray("item");
          var matrix = new org.joml.Matrix4f();
          for (int row = 0; row < 4; row++)
            for (int column = 0; column < 4; column++)
              matrix.set(column, row, rows.get(row).getAsJsonArray().get(column).getAsFloat());
          var bristles =
              matrix.transformDirection(new org.joml.Vector3f(workingNormal)).normalize();
          boolean left = attachment.get("context").getAsString().endsWith("LEFT_HAND");
          require(
              left ? bristles.x > .9 : bristles.x < -.9,
              "The grooming bristles point inward through both complete Punchy attachments");
        }
      }
      preview.add("attachments", attachments);
      previews.add(state, preview);
    }
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), previews.toString());
    System.out.println(
        "Farming equipment: 13 registered useful handhelds / 16 native states; provider predicates, non-hand models, four hand contexts and native OBJ baking passed");
  }

  private static void collect(String state, Set<String> states) throws IOException {
    if (!states.add(state)) return;
    JsonObject model = HeldItemModels.nativeModel(state);
    if (!model.has("overrides")) return;
    for (var entry : model.getAsJsonArray("overrides"))
      collect(entry.getAsJsonObject().get("model").getAsString().replace(":item/", ":"), states);
  }

  private static JsonObject read(Path assets, String path) throws IOException {
    return JsonParser.parseString(Files.readString(assets.resolve(path))).getAsJsonObject();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
