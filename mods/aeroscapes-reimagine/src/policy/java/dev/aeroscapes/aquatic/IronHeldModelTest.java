package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import punchy.client.renderer.layer.VanillaFirstPersonItemLayer.ToolKind;
import punchy.config.PunchyTuningConfig;

/** Checks the pinned state graph and bakes each exported physical state with NeoForge. */
public final class IronHeldModelTest {
  private IronHeldModelTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    Path items = assets.resolve("irons_spellbooks/models/item");
    JsonObject previews = new JsonObject();
    var failures = new ArrayList<String>();
    var states = IronHeldModels.states();
    var profiles =
        read(assets.resolve(IronHeldModels.PUNCHY_PROFILE)).getAsJsonObject("itemSpecific");
    for (var entry : states.entrySet()) {
      String state = entry.getKey(), root = entry.getValue();
      var provider = HeldItemModels.nativeModel(IronHeldModels.MOD + state);
      var wrapper = read(items.resolve(state + ".json"));
      var views = wrapper.getAsJsonObject("perspectives");
      String held = "equipment/held/" + state;
      for (String hand : IronHeldMesh.HANDS) {
        var model = views.remove(hand).getAsJsonObject();
        require(
            model.get("parent").getAsString().equals(IronHeldModels.MOD + "item/" + held),
            state + " uses its corresponding geometry in " + hand);
      }
      if (provider.has("loader")) {
        require(provider.equals(wrapper), state + " retains all original presentation");
      } else {
        require(views.isEmpty(), state + " does not override other contexts");
        require(
            Objects.equals(provider.remove("overrides"), wrapper.get("overrides")),
            state + " retains its exact native thresholds and ordering");
        if (state.equals("furled_map_citadel") || state.equals("furled_map_ice_spider_den")) {
          String parent = provider.get("parent").getAsString().replace(":item/", ":");
          provider = HeldItemModels.nativeModel(parent);
        }
        require(provider.equals(wrapper.get("base")), state + " retains its original flat model");
      }
      var source = read(items.resolve(held + ".json"));
      List<VanillaEquipmentModelTest.Face> faces;
      try {
        faces =
            source.has("children")
                ? VanillaEquipmentModelTest.bakeComposite(source, assets)
                : VanillaEquipmentModelTest.bakeObj(source, assets);
        require(!faces.isEmpty(), state + " bakes visible geometry");
        VanillaEquipmentModelTest.noOverlaps(state, faces);
        checkGlow(root, faces);
      } catch (AssertionError failure) {
        failures.add(state + ": " + failure.getMessage());
        continue;
      }
      if (root.equals(IronHeldModels.COMPASS)) {
        String frame = IronHeldModels.texture(provider);
        require(
            faces.stream().anyMatch(face -> face.texture().equals(frame)),
            state + " uses the native compass face for that angle");
      }
      if (arguments.length > 1) {
        var preview = VanillaEquipmentModelTest.preview(source, faces);
        var quads = preview.getAsJsonArray("quads");
        for (int index = 0; index < faces.size(); index++)
          quads.get(index).getAsJsonObject().addProperty("alpha", faces.get(index).alpha());
        ToolKind kind =
            IronHeldModels.WEAPONS.contains(root)
                ? root.equals("autoloader_crossbow") ? ToolKind.CROSSBOW : ToolKind.SWORD
                : root.endsWith("tincture") || root.startsWith("tincture_")
                    ? ToolKind.FOOD
                    : ToolKind.INGREDIENT;
        var profile = profiles.getAsJsonObject(IronHeldModels.MOD + root);
        float offset =
            profile == null
                ? 0
                : new com.google.gson.Gson()
                    .fromJson(profile.get("armTransforms"), PunchyTuningConfig.SideSet.class)
                    .right
                    .toArm()
                    .offY;
        preview.add(
            "attachments", ConstructionPlacementTest.attachments(source, state, kind, offset));
        preview.addProperty("nativeSprite", IronHeldModels.texture(provider));
        previews.add(state, preview);
      }
    }
    if (arguments.length > 1) Files.writeString(Path.of(arguments[1]), previews.toString());
    require(failures.isEmpty(), String.join("\n", failures));
    System.out.println("Iron's Spells: " + states.size() + " native model states baked");
  }

  private static void checkGlow(String root, List<VanillaEquipmentModelTest.Face> faces) {
    var lit = faces.stream().filter(face -> face.light() != 0).toList();
    double minimumY =
        switch (root) {
          case "spellbreaker" -> 12.5;
          case "hither_thither_wand" -> 15.3;
          case "twilight_gale" -> -10.5;
          default -> Double.NaN;
        };
    if (Double.isNaN(minimumY)) {
      require(lit.isEmpty(), root + " retains ordinary lighting");
      return;
    }
    require(!lit.isEmpty() && lit.size() < faces.size(), root + " lights only selected surfaces");
    for (var face : lit) {
      int level = LightTexture.block(face.light());
      require(level >= 4 && level <= 7, root + " uses restrained minimum light");
      require(
          face.vertices().stream().allMatch(vertex -> vertex.y * 16 >= minimumY - .01),
          root + " confines the glow to the intended part");
    }
    if (root.equals("twilight_gale")) {
      var range =
          lit.stream()
              .flatMap(face -> face.vertices().stream())
              .mapToDouble(vertex -> vertex.y * 16)
              .summaryStatistics();
      require(
          range.getMin() < -7 && range.getMax() > 30 && range.getMax() < 32,
          root + " lights the wrap from pommel to collar, leaving the blade unlit");
    }
  }

  private static JsonObject read(Path path) throws IOException {
    return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
