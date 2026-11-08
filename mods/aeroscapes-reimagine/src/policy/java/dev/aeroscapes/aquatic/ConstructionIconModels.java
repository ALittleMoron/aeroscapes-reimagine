package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** CreativeCore must see its layer model before ItemRenderer applies camera transforms. */
final class ConstructionIconModels {
  static final List<String> ITEMS = List.of("chisel", "glove");

  private ConstructionIconModels() {}

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    for (String item : ITEMS)
      EquipmentModels.text(
          output,
          assets.resolve("littletiles/models/item/" + item + ".json"),
          model(item).toString());
  }

  static JsonObject model(String item) throws IOException {
    JsonObject root = HeldItemModels.nativeModel("littletiles:" + item);
    JsonObject sprite = root.getAsJsonArray("layers").get(0).getAsJsonObject();
    JsonObject routedSprite = new JsonObject();
    routedSprite.addProperty("loader", "neoforge:separate_transforms");
    routedSprite.addProperty("gui_light", "front");
    routedSprite.add("base", sprite);
    JsonObject textures = sprite.getAsJsonObject("textures").deepCopy();
    textures.addProperty("particle", "#layer0");
    routedSprite.add("textures", textures);
    JsonObject hands = new JsonObject();
    for (String context :
        List.of(
            "firstperson_righthand", "firstperson_lefthand",
            "thirdperson_righthand", "thirdperson_lefthand")) {
      JsonObject held = new JsonObject();
      held.addProperty("parent", "littletiles:item/equipment/held/" + item);
      hands.add(context, held);
    }
    routedSprite.add("perspectives", hands);
    root.getAsJsonArray("layers").set(0, routedSprite);
    return root;
  }
}
