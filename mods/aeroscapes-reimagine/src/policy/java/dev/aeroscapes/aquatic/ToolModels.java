package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Held equipment geometry and the provider net's explicit orientation. */
final class ToolModels {
  private ToolModels() {}

  static void addResources(Path repository, Map<Path, byte[]> output) throws IOException {
    Path assets =
        repository.resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    VanillaEquipmentModels.addResources(assets, output);
    var generated = NativeEquipmentModels.generate();
    VanillaEquipmentMesh.addResources(assets, output, generated.models());
    ReviewedEquipmentModels.addResources(assets, output, generated.models());
    UtilityEquipmentModels.addResources(assets, output, generated.models());
    HeldItemModels.add(assets, output, generated.models());
    EquipmentModels.text(
        output,
        assets.resolve("simulated/models/item/rope_connector.json"),
        """
        {"parent":"simulated:block/rope_connector/item","display":{
          "thirdperson_righthand":{"rotation":[75,45,0],"translation":[0,3.033814,1.992222],"scale":[0.375,0.375,0.375]},
          "thirdperson_lefthand":{"rotation":[75,45,0],"translation":[0,3.033814,1.992222],"scale":[0.375,0.375,0.375]},
          "firstperson_righthand":{"rotation":[0,45,0],"translation":[0,2.2,0],"scale":[0.4,0.4,0.4]},
          "firstperson_lefthand":{"rotation":[0,225,0],"translation":[0,2.2,0],"scale":[0.4,0.4,0.4]}}}
        """);
    JsonObject atlas = new JsonObject();
    atlas.add("sources", generated.textures());
    EquipmentModels.text(output, assets.resolve("minecraft/atlases/blocks.json"), atlas.toString());
    EquipmentModels.text(
        output,
        assets.resolve("hapi/models/item/equipment/creature_net_held.json"),
        """
        {
          "parent": "hapi:item/creature_net",
          "display": {
            "thirdperson_righthand": {"rotation": [90, 0, 180], "translation": [0, 4, 0.5], "scale": [0.8, 0.8, 0.8]},
            "thirdperson_lefthand": {"rotation": [90, 0, 180], "translation": [0, 4, 0.5], "scale": [0.8, 0.8, 0.8]},
            "firstperson_righthand": {"rotation": [90, -15, 180], "translation": [1, 3, 0], "scale": [0.65, 0.65, 0.65]},
            "firstperson_lefthand": {"rotation": [90, 15, 180], "translation": [1, 3, 0], "scale": [0.65, 0.65, 0.65]}
          }
        }
        """);
    EquipmentModels.text(
        output,
        assets.resolve("hapi/modefite_items_override/creature_net.json"),
        """
        {
          "model": {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "cases": [{
              "when": ["firstperson_righthand", "firstperson_lefthand", "thirdperson_righthand", "thirdperson_lefthand"],
              "model": {"type": "minecraft:model", "model": "hapi:item/equipment/creature_net_held"}
            }],
            "fallback": {"type": "minecraft:model", "model": "hapi:item/creature_net"}
          }
        }
        """);
  }

  static Map<String, JsonObject> models() throws IOException {
    return NativeEquipmentModels.models();
  }
}
