package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import punchy.client.renderer.layer.VanillaFirstPersonItemLayer.ToolKind;

/** Bakes each actual held state and preserves provider presentation outside the hands. */
public final class EngineeringContainerModelTest {
  private EngineeringContainerModelTest() {}

  public static void main(String[] arguments) throws IOException, ReflectiveOperationException {
    if (arguments.length < 1 || arguments.length > 2)
      throw new IllegalArgumentException("Expected repository and optional preview output");
    LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
    Path assets =
        Path.of(arguments[0])
            .resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    var roots = new ArrayList<>(EngineeringBucketModels.ROOTS);
    roots.addAll(SteamContainerModels.ROOTS);
    roots.add("createbigcannons:worm");
    JsonObject previews = new JsonObject();
    for (String id : roots) {
      String[] key = id.split(":", 2);
      JsonObject model = read(assets, key[0] + "/models/item/equipment/held/" + key[1] + ".json");
      if (!id.equals(EngineeringBucketModels.COPPER)) {
        JsonObject wrapper = read(assets, key[0] + "/models/item/" + key[1] + ".json");
        require(
            wrapper.get("base").equals(HeldItemModels.nativeModel(id)),
            "Native GUI, ground and fixed presentation: " + id);
        for (String perspective : List.of("firstperson", "thirdperson"))
          for (String hand : List.of("righthand", "lefthand"))
            require(
                wrapper
                    .getAsJsonObject("perspectives")
                    .getAsJsonObject(perspective + "_" + hand)
                    .get("parent")
                    .getAsString()
                    .equals(key[0] + ":item/equipment/held/" + key[1]),
                "Correct hand child: " + id);
      }
      var faces =
          model.get("loader").getAsString().equals("neoforge:composite")
              ? VanillaEquipmentModelTest.bakeComposite(model, assets)
              : VanillaEquipmentModelTest.bakeObj(model, assets);
      require(!faces.isEmpty(), "Visible baked volume: " + id);
      VanillaEquipmentModelTest.noOverlaps(id, faces);
      boolean bucket = EngineeringBucketModels.ROOTS.contains(id);
      var attachments =
          ConstructionPlacementTest.attachments(
              model, key[1], bucket ? ToolKind.BUCKET : ToolKind.INGREDIENT, bucket ? -.52F : 0);
      if (!id.equals("createbigcannons:worm"))
        FieldEquipmentModelTest.inNeutralFrame(id, faces, attachments);
      JsonObject preview = VanillaEquipmentModelTest.preview(model, faces);
      preview.add("attachments", attachments);
      previews.add(id, preview);
    }
    JsonObject fluid =
        read(assets, "fluidlogistics/models/item/equipment/held/copper_bucket_fluid.json");
    var fill = VanillaEquipmentModelTest.bakeObj(fluid, assets);
    require(!fill.isEmpty(), "Native-baked copper fluid template");
    VanillaEquipmentModelTest.noOverlaps("copper fluid template", fill);
    fluid.add("display", new JsonObject());
    previews.add("copper_fluid_template", VanillaEquipmentModelTest.preview(fluid, fill));
    var common = read(assets, "aeroscapes_reimagine/models/item/bucket_body.json");
    var commonFaces = VanillaEquipmentModelTest.bakeObj(common, assets);
    VanillaEquipmentModelTest.noOverlaps("common bucket", commonFaces);
    var commonAttachments =
        ConstructionPlacementTest.attachments(common, "bucket", ToolKind.BUCKET, -.52F);
    FieldEquipmentModelTest.inNeutralFrame("common bucket", commonFaces, commonAttachments);
    var commonPreview = VanillaEquipmentModelTest.preview(common, commonFaces);
    commonPreview.add("attachments", commonAttachments);
    previews.add("common_bucket_body", commonPreview);
    if (arguments.length == 2) Files.writeString(Path.of(arguments[1]), previews.toString());
    System.out.println(
        "Engineering containers: "
            + roots.size()
            + " roots, native fallbacks and hand frames baked");
  }

  private static JsonObject read(Path assets, String path) throws IOException {
    return JsonParser.parseString(Files.readString(assets.resolve(path))).getAsJsonObject();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
