package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/** Held equipment geometry and the provider net's explicit orientation. */
final class ToolModels {
  private static final String ROPE_CONNECTOR =
      """
        {"parent":"simulated:block/rope_connector/item","display":{
          "thirdperson_righthand":{"rotation":[75,45,0],"translation":[0,3.033814,1.992222],"scale":[0.375,0.375,0.375]},
          "thirdperson_lefthand":{"rotation":[75,45,0],"translation":[0,3.033814,1.992222],"scale":[0.375,0.375,0.375]},
          "firstperson_righthand":{"rotation":[0,45,0],"translation":[0,2.2,0],"scale":[0.4,0.4,0.4]},
          "firstperson_lefthand":{"rotation":[0,225,0],"translation":[0,2.2,0],"scale":[0.4,0.4,0.4]}}}
        """;

  private ToolModels() {}

  static JsonObject ropeConnectorDisplay() {
    return JsonParser.parseString(ROPE_CONNECTOR)
        .getAsJsonObject()
        .getAsJsonObject("display")
        .deepCopy();
  }

  static void addResources(Path repository, Map<Path, byte[]> output) throws IOException {
    Path assets =
        repository.resolve("pack/resourcepacks/aeroscapes-reimagine-refined-compat/assets");
    VanillaEquipmentModels.addResources(assets, output);
    var generated = NativeEquipmentModels.generate();
    VanillaEquipmentMesh.addResources(assets, output, generated.models());
    ReviewedEquipmentModels.addResources(assets, output, generated.models());
    UtilityEquipmentModels.addResources(assets, output, generated.models());
    CreateEquipmentModels.addResources(assets, output, generated.models());
    GalosphereEquipmentModels.addResources(assets, output, generated.models());
    KnifeEquipmentModels.addResources(assets, output);
    AquaticToolModels.addResources(assets, output);
    ConstructionEquipmentModels.addResources(assets, output);
    DieselEquipmentModels.addResources(assets, output);
    HeldItemModels.add(assets, output, generated.models());
    FieldEquipmentModels.addResources(assets, output);
    IronHeldModels.addResources(assets, output);
    TheurgyHeldModels.addResources(assets, output);
    brushScale(assets, output);
    ArchaeologyBrushModels.addPalettes(assets, output, generated.textures());
    EquipmentModels.text(
        output, assets.resolve("simulated/models/item/rope_connector.json"), ROPE_CONNECTOR);
    JsonObject atlas = new JsonObject();
    atlas.add("sources", generated.textures());
    EquipmentModels.text(output, assets.resolve("minecraft/atlases/blocks.json"), atlas.toString());
    JsonObject net =
        JsonParser.parseString(
                """
        {
          "parent": "hapi:item/creature_net",
          "display": {
            "thirdperson_righthand": {"rotation": [65, 0, 180], "translation": [0, 0.4, 1.6], "scale": [0.8, 0.8, 0.8]},
            "thirdperson_lefthand": {"rotation": [65, 0, 180], "translation": [0, 0.4, 1.6], "scale": [0.8, 0.8, 0.8]},
            "firstperson_righthand": {"rotation": [90, -15, 180], "scale": [0.65, 0.65, 0.65]},
            "firstperson_lefthand": {"rotation": [90, -15, 180], "scale": [0.65, 0.65, 0.65]}
          }
        }
        """)
            .getAsJsonObject();
    HeldItemModels.anchorHeld(
        net.getAsJsonObject("display"), new Vector3f(8, 8.5f, 11), new Vector3f(0, -2, 2));
    EquipmentModels.text(
        output,
        assets.resolve("hapi/models/item/equipment/creature_net_held.json"),
        net.toString());
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

  private static void brushScale(Path assets, Map<Path, byte[]> output) {
    // Refined Tools 3.0 supplies the mesh; these children change only the four hand slots.
    for (int state = -1; state < 3; state++) {
      String name = "held_brush" + (state < 0 ? "" : "_" + state);
      JsonObject model = new JsonObject();
      model.addProperty("parent", "minecraft:item/" + name);
      JsonObject display = new JsonObject();
      for (String hand : List.of("righthand", "lefthand")) {
        float yaw =
            state == 0
                ? (hand.equals("righthand") ? -162 : 162)
                : state == 2 ? (hand.equals("righthand") ? 162 : -162) : -180;
        JsonObject transform =
            JsonParser.parseString("{\"rotation\":[-90," + yaw + ",0],\"scale\":[0.5,0.5,0.5]}")
                .getAsJsonObject();
        display.add("thirdperson_" + hand, transform);
        display.add("firstperson_" + hand, transform.deepCopy());
      }
      HeldItemModels.anchorHeld(display, new Vector3f(8, 1, 13));
      JsonObject firstPerson = new JsonObject();
      for (String hand : List.of("righthand", "lefthand"))
        firstPerson.add("firstperson_" + hand, display.get("firstperson_" + hand));
      HeldItemModels.anchorHeld(firstPerson, new Vector3f(8, 1, 15.5f));
      model.add("display", display);
      EquipmentModels.text(
          output,
          assets.resolve("minecraft/models/item/equipment/" + name + ".json"),
          model.toString());
    }
    EquipmentModels.text(
        output,
        assets.resolve("minecraft/modefite_items_override/brush.json"),
        """
        {"model":{"type":"minecraft:select","property":"minecraft:display_context",
          "cases":[{"when":["gui","fixed","ground"],"model":{"type":"minecraft:model","model":"minecraft:item/brush"}}],
          "fallback":{"type":"minecraft:range_dispatch","property":"minecraft:use_cycle","scale":0.1,"period":10.0,
            "entries":[
              {"threshold":0.25,"model":{"type":"minecraft:model","model":"minecraft:item/equipment/held_brush_0"}},
              {"threshold":0.5,"model":{"type":"minecraft:model","model":"minecraft:item/equipment/held_brush_1"}},
              {"threshold":0.75,"model":{"type":"minecraft:model","model":"minecraft:item/equipment/held_brush_2"}}],
            "fallback":{"type":"minecraft:model","model":"minecraft:item/equipment/held_brush"}}}}
        """);
  }

  static Map<String, JsonObject> models() throws IOException {
    return NativeEquipmentModels.models();
  }
}
