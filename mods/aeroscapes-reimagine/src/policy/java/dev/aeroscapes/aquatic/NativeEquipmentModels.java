package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.atmosphere.EquipmentStyle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Native painted profiles with component-specific volume, inset faces and hollow outlines. */
final class NativeEquipmentModels {
  private NativeEquipmentModels() {}

  static Map<String, EquipmentStyle> designs() {
    Map<String, EquipmentStyle> designs = new LinkedHashMap<>();
    add(
        designs,
        EquipmentStyle.KNIFE,
        "farmersdelight:flint_knife",
        "farmersdelight:iron_knife",
        "farmersdelight:golden_knife",
        "farmersdelight:diamond_knife",
        "farmersdelight:netherite_knife",
        "wetland_whimsy:dagger");
    add(
        designs,
        EquipmentStyle.CORAL,
        "hybrid_aquatic:coral_axe",
        "hybrid_aquatic:coral_pickaxe",
        "hybrid_aquatic:coral_shovel",
        "hybrid_aquatic:coral_hoe",
        "hybrid_aquatic:coral_blade");
    add(
        designs,
        EquipmentStyle.SHELL,
        "hybrid_aquatic:seashell_axe",
        "hybrid_aquatic:seashell_pickaxe",
        "hybrid_aquatic:seashell_shovel",
        "hybrid_aquatic:seashell_hoe",
        "hybrid_aquatic:seashell_spear");
    add(designs, EquipmentStyle.FISH, "tide:swordfish", "tide:blazing_swordfish", "tide:sailfish");
    add(
        designs,
        EquipmentStyle.BRUSH,
        "betterarcheology:iron_brush",
        "betterarcheology:diamond_brush",
        "betterarcheology:netherite_brush",
        "sophisticatedstorage:paintbrush");
    add(designs, EquipmentStyle.HAMMER, "handcrafted:hammer", "quark:hammer");
    add(designs, EquipmentStyle.SPADE, "quark:trowel");
    add(designs, EquipmentStyle.SAW, "chipped:saw");
    add(designs, EquipmentStyle.CHISEL, "chipped:chisel");
    add(
        designs,
        EquipmentStyle.WRENCH,
        "supplementaries:wrench",
        "sophisticatedstorage:storage_tool",
        "toms_storage:inventory_configurator");
    add(designs, EquipmentStyle.BOOMERANG, "quark:pickarang", "quark:flamerang");
    add(designs, EquipmentStyle.DART, "galosphere:rope_dart");
    add(
        designs,
        EquipmentStyle.BOMB,
        "galosphere:palladium_bomb",
        "betterarcheology:bomb",
        "supplementaries:bomb",
        "supplementaries:bomb_blue",
        "supplementaries:bomb_spiky");
    add(designs, EquipmentStyle.SLUDGE, "wetland_whimsy:sludge_charge");
    add(
        designs,
        EquipmentStyle.COIL,
        "create_aeronautics_throwable_rope_connector:throwable_rope_connector",
        "simulated:rope_coupling");
    add(designs, EquipmentStyle.BOW, "tide:starlight_bow");
    add(designs, EquipmentStyle.SLINGSHOT, "supplementaries:slingshot");
    add(designs, EquipmentStyle.BELL, "galosphere:echo_bell");
    add(designs, EquipmentStyle.CAPTURE_NET, "naturalist:capture_net");
    add(designs, EquipmentStyle.GRAPPLE, "crittersandcompanions:grappling_hook");
    add(designs, EquipmentStyle.CLAW, "spawn:coastal_crab_claw");
    add(designs, EquipmentStyle.PAPER, "create:sand_paper", "create:red_sand_paper");
    add(designs, EquipmentStyle.BOOK, "chipped:alchemy_book");
    add(designs, EquipmentStyle.MULTIMETER, "chipped:multimeter");
    add(designs, EquipmentStyle.NEEDLES, "chipped:needles");
    add(designs, EquipmentStyle.WATERING_CAN, "chipped:watering_can");
    add(designs, EquipmentStyle.CLIPBOARD, "create:clipboard");
    add(designs, EquipmentStyle.SCHEMATIC, "create:schematic_and_quill");
    add(designs, EquipmentStyle.GLUE, "create:super_glue", "simulated:honey_glue");
    add(
        designs,
        EquipmentStyle.DIAL,
        "explorerscompass:explorerscompass",
        "naturescompass:naturescompass",
        "minecraft:clock",
        "minecraft:compass",
        "minecraft:recovery_compass",
        "legendarysurvivaloverhaul:thermometer");
    add(designs, EquipmentStyle.PROJECTOR, "exposure:interplanar_projector");
    add(designs, EquipmentStyle.GLOW_FLARE, "galosphere:glow_flare");
    add(designs, EquipmentStyle.SPECTRE_FLARE, "galosphere:spectre_flare");
    add(designs, EquipmentStyle.MONSTROMETER, "galosphere:monstrometer");
    add(designs, EquipmentStyle.TABLET, "galosphere:saltbound_tablet");
    add(designs, EquipmentStyle.SPYGLASS, "galosphere:spectre_bound_spyglass");
    add(designs, EquipmentStyle.HORN, "minecraft:goat_horn", "horseman:copper_horn");
    add(designs, EquipmentStyle.LEAD, "minecraft:lead");
    add(designs, EquipmentStyle.WHISTLE, "naturalist:whistle");
    add(designs, EquipmentStyle.ABACUS, "quark:abacus");
    add(designs, EquipmentStyle.QUILL, "quark:pathfinders_quill");
    add(designs, EquipmentStyle.ENDER_EYE, "sophisticatedcore:ender_linker");
    add(designs, EquipmentStyle.CASTING_NET, "spawn:casting_net");
    add(designs, EquipmentStyle.ALTIMETER, "supplementaries:altimeter_template");
    add(designs, EquipmentStyle.BUBBLE, "supplementaries:bubble_blower");
    add(designs, EquipmentStyle.KEY, "supplementaries:key");
    add(designs, EquipmentStyle.CLIMATE_GAUGE, "tide:climate_gauge");
    add(designs, EquipmentStyle.DEPTH_METER, "tide:depth_meter");
    add(designs, EquipmentStyle.WATCH, "tide:pocket_watch", "tide:enchanted_pocket_watch");
    add(designs, EquipmentStyle.FISH_FINDER, "tide:fish_finder");
    add(
        designs,
        EquipmentStyle.TERMINAL,
        "toms_storage:wireless_terminal",
        "toms_storage:adv_wireless_terminal");
    add(designs, EquipmentStyle.PAINT_KIT, "toms_storage:paint_kit");
    add(designs, EquipmentStyle.CRYSTAL, "waystones:warp_stone");
    return designs;
  }

  private static void add(
      Map<String, EquipmentStyle> designs, EquipmentStyle profile, String... items) {
    for (String item : items) {
      if (designs.put(item, profile) != null)
        throw new IllegalArgumentException("Duplicate item " + item);
    }
  }

  static Map<String, JsonObject> models() throws IOException {
    return generate().models();
  }

  static Generation generate() throws IOException {
    Map<String, JsonObject> models = new LinkedHashMap<>();
    JsonArray textures = new JsonArray();
    for (var entry : designs().entrySet()) {
      if (!VanillaEquipmentModels.supports(entry.getKey())
          && !UtilityEquipmentModels.supports(entry.getKey()))
        textures.add(EquipmentSculpture.spriteSource(entry.getKey(), entry.getValue()));
      addState(models, entry.getKey(), entry.getValue(), entry.getKey());
    }
    textures.add(
        EquipmentSculpture.spriteSource("spawn:coastal_crab_claw_empty", EquipmentStyle.CLAW));
    JsonObject bubble = new JsonObject();
    bubble.addProperty("type", "minecraft:single");
    bubble.addProperty("resource", "spawn:entity/coastal_crab/bubble");
    textures.add(bubble);
    return new Generation(models, textures);
  }

  private static void addState(
      Map<String, JsonObject> models, String id, EquipmentStyle profile, String root)
      throws IOException {
    if (models.containsKey(id)) return;
    JsonObject original = HeldItemModels.nativeModel(id);
    models.put(id, sculpt(id, profile));
    if (!VanillaEquipmentModels.supports(id) && !UtilityEquipmentModels.supports(id))
      models
          .get(id)
          .getAsJsonObject("textures")
          .addProperty(
              "surface",
              (id.endsWith("coastal_crab_claw_empty") ? id : root)
                  .replace(":", ":item/equipment/painted/"));
    if (!original.has("overrides")) return;
    models.get(id).add("overrides", original.get("overrides").deepCopy());
    for (var entry : original.getAsJsonArray("overrides")) {
      String target = entry.getAsJsonObject().get("model").getAsString();
      if (!target.contains(":")) target = "minecraft:" + target;
      addState(models, target.replace(":item/", ":"), profile, root);
    }
  }

  record Generation(Map<String, JsonObject> models, JsonArray textures) {}

  static JsonObject sculpt(String id, EquipmentStyle profile) throws IOException {
    if (UtilityEquipmentModels.supports(id))
      return UtilityEquipmentModels.model(id, profile, inherited(id));
    if (ReviewedEquipmentModels.supports(id))
      return ReviewedEquipmentModels.model(id, profile, inherited(id));
    if (VanillaEquipmentModels.supports(id)) return VanillaEquipmentModels.model(id, inherited(id));
    return EquipmentSculpture.model(id, profile, inherited(id));
  }

  static JsonObject inherited(String id) throws IOException {
    JsonObject original = HeldItemModels.nativeModel(id);
    JsonObject merged = new JsonObject();
    if (original.has("parent")) {
      String parent = original.get("parent").getAsString();
      if (!parent.contains(":")) parent = "minecraft:" + parent;
      if (!parent.startsWith("minecraft:builtin/")) {
        if (!parent.contains(":item/"))
          throw new IOException("Unexpected equipment parent " + parent);
        merged = inherited(parent.replace(":item/", ":"));
      }
    }
    for (String field : List.of("textures", "display")) {
      if (!merged.has(field)) merged.add(field, new JsonObject());
      if (original.has(field))
        for (var property : original.getAsJsonObject(field).entrySet())
          merged.getAsJsonObject(field).add(property.getKey(), property.getValue().deepCopy());
    }
    return merged;
  }

  static BufferedImage image(String texture) throws IOException {
    if (!texture.contains(":")) texture = "minecraft:" + texture;
    String path = "/assets/" + texture.replace(":", "/textures/") + ".png";
    try (var stream = NativeEquipmentModels.class.getResourceAsStream(path)) {
      if (stream == null) throw new IOException("Missing native equipment texture " + texture);
      BufferedImage image = ImageIO.read(stream);
      if (image == null) throw new IOException("Invalid native equipment texture " + texture);
      return image;
    }
  }
}
