package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/** Preserves Hex's state graph, including the component-selected Patchouli notebook. */
final class HexHeldModels {
  private static final String MODEL = "hexcasting:item/equipment/held/";
  private static final String OBJ = "hexcasting:models/item/equipment/hex/";
  private static final List<String> WOODS =
      List.of(
          "oak",
          "spruce",
          "birch",
          "jungle",
          "acacia",
          "dark_oak",
          "crimson",
          "warped",
          "mangrove",
          "cherry",
          "bamboo",
          "edified",
          "quenched",
          "mindsplice");
  private static final List<String> CARRIERS = List.of("cypher", "trinket", "artifact", "focus");
  private static final List<String> MATERIALS =
      List.of(
          "wood",
          "twig",
          "wrap",
          "gem",
          "metal",
          "rune",
          "paper",
          "paper_edge",
          "inside",
          "knot",
          "coating",
          "lens_edge",
          "cover",
          "ribbon",
          "cord",
          "glass",
          "liquid",
          "aqua",
          "pigment",
          "gem_shadow",
          "gem_flash",
          "shard",
          "shard_shadow",
          "shard_flash",
          "leaf",
          "polished_iron",
          "dark_iron",
          "glint",
          "leather",
          "leather_light",
          "leather_edge",
          "glass_flash",
          "lip");

  private HexHeldModels() {}

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    Path provider = assets.resolve("hexcasting");
    Path models = provider.resolve("models/item");
    Path objects = models.resolve("equipment/hex");
    var states = new LinkedHashMap<String, String>();
    var roots =
        new ArrayList<>(
            List.of(
                "jeweler_hammer",
                "abacus",
                "lens",
                "thought_knot",
                "battery",
                "spellbook",
                "slate",
                "scroll",
                "scroll_medium",
                "scroll_small",
                "patchouli_book",
                "lore_fragment",
                "uuid_colorizer",
                "default_colorizer"));
    roots.addAll(CARRIERS);
    WOODS.forEach(wood -> roots.add("staff/" + wood));
    for (String dye :
        "white orange magenta light_blue yellow lime pink gray light_gray cyan purple blue brown green red black"
            .split(" ")) roots.add("dye_colorizer_" + dye);
    for (String flag :
        "agender aroace aromantic asexual bisexual demiboy demigirl gay genderfluid genderqueer intersex lesbian nonbinary pansexual plural transgender"
            .split(" ")) roots.add("pride_colorizer_" + flag);
    for (String root : roots) collect(states, root, root);
    Set<String> replaced = new HashSet<>();
    states.keySet().forEach(state -> replaced.add("hexcasting:" + state));
    var materialLibrary = new StringBuilder();
    for (String material : MATERIALS) {
      materialLibrary
          .append("newmtl ")
          .append(material)
          .append("\nKd 1 1 1 ")
          .append(
              switch (material) {
                case "glass" -> ".27";
                case "glass_flash" -> ".56";
                case "lip" -> ".69";
                default -> "1";
              })
          .append('\n');
      if (material.equals("rune")) materialLibrary.append("neoforge_TintIndex 1\n");
      materialLibrary.append("map_Kd #").append(material).append("\n\n");
    }
    EquipmentModels.text(output, objects.resolve("equipment.mtl"), materialLibrary.toString());
    var materialsByShape = new HashMap<String, Set<String>>();
    var grips = new HashMap<String, EquipmentObjMesh.P>();
    for (var entry : states.entrySet()) {
      String state = entry.getKey(), root = entry.getValue(), id = "hexcasting:" + state;
      var nativeModel = HeldItemModels.nativeModel(id);
      if (!nativeModel.has("textures"))
        continue; // Native selector-only roots already route to our leaves.
      var layers = nativeModel.getAsJsonObject("textures");
      String sprite = layers.get("layer0").getAsString();
      String shape = state.replace('/', '_');
      var parts = state.substring(Math.min(root.length(), state.length())).split("_");
      int form = parts.length > 1 && parts[1].matches("[0-7]") ? Integer.parseInt(parts[1]) : 0;
      boolean filled = layers.has("layer1");
      boolean glass = root.equals("battery");
      if (root.contains("colorizer")) shape = "pigment";
      if (root.startsWith("scroll")) shape = root;
      var physical = new HexHeldMesh();
      if (!materialsByShape.containsKey(shape)) {
        if (root.startsWith("staff/")) physical.staff(state.substring(6));
        else if (CARRIERS.contains(root)) {
          BufferedImage overlay = filled ? image(layers.get("layer1").getAsString()) : null;
          grips.put(
              shape,
              new HexCarriers(physical)
                  .build(form, root, state.endsWith("sealed"), image(sprite), overlay));
        } else if (root.equals("spellbook"))
          HexStudyTools.book(physical, form, filled, state.endsWith("sealed"));
        else if (glass) {
          String[] phial = state.split("_");
          int size = List.of("small", "medium", "large", "larger", "largest").indexOf(phial[1]);
          new HexPhials(physical).build(size, Integer.parseInt(phial[2]));
          grips.put(shape, HexPocketGrip.contact(physical.glass));
        } else HexStudyTools.add(physical, state, root);
        String body = compact(physical.mesh.obj());
        var materials = new HashSet<String>();
        body.lines()
            .filter(line -> line.startsWith("usemtl "))
            .forEach(line -> materials.add(line.substring(7)));
        EquipmentModels.text(output, objects.resolve(shape + ".obj"), body);
        if (glass) {
          materials.addAll(List.of("glass", "glass_flash", "lip"));
          EquipmentModels.text(
              output, objects.resolve(shape + "_glass.obj"), compact(physical.glass.obj()));
        }
        materialsByShape.put(shape, materials);
      }
      var textures =
          textures(sprite, root, state, layers, materialsByShape.get(shape), provider, output);
      var solid = model(shape, textures, root.equals("lens") ? "translucent" : "cutout");
      if (glass) {
        var children = new JsonObject();
        children.add("contents", solid);
        children.add("glass", model(shape + "_glass", textures, "translucent"));
        solid = new JsonObject();
        solid.addProperty("loader", "neoforge:composite");
        solid.add("children", children);
        var order = new JsonArray();
        order.add("contents");
        order.add("glass");
        solid.add("item_render_order", order);
        solid.add("textures", textures);
      }
      solid.add(
          "display",
          grips.containsKey(shape)
              ? HexPocketGrip.display(grips.get(shape), glass)
              : display(root));
      EquipmentModels.text(
          output, models.resolve("equipment/held/" + state + ".json"), solid + "\n");
      EquipmentModels.text(
          output,
          models.resolve(state + ".json"),
          HeldItemModels.wrapper(id, MODEL + state, replaced) + "\n");
    }
  }

  private static JsonObject display(String root) throws IOException {
    if (root.startsWith("staff/")) return HexStaves.display();
    if (root.equals("jeweler_hammer")) return HexStudyTools.hammerDisplay();
    if (root.equals("abacus")
        || root.equals("slate")
        || root.equals("thought_knot")
        || root.equals("lens")
        || root.startsWith("scroll")
        || root.contains("colorizer")) return HexStudyTools.instrumentDisplay(root);
    if (root.equals("spellbook") || root.equals("patchouli_book") || root.equals("lore_fragment"))
      return HexStudyTools.bookDisplay();
    return IronHeldMesh.display(HexHeldMesh.point(0, 3.42, 0), .9, -13, 18, -7);
  }

  private static void collect(Map<String, String> states, String root, String state)
      throws IOException {
    if (states.putIfAbsent(state, root) != null) return;
    var model = HeldItemModels.nativeModel("hexcasting:" + state);
    if (model.has("overrides"))
      for (var entry : model.getAsJsonArray("overrides")) {
        String child = entry.getAsJsonObject().get("model").getAsString();
        if (!child.startsWith("hexcasting:item/"))
          throw new IOException("Unexpected Hex state " + child);
        collect(states, root, child.substring("hexcasting:item/".length()));
      }
  }

  private static JsonObject model(String name, JsonObject textures, String type) {
    var model = new JsonObject();
    model.addProperty("loader", "neoforge:obj");
    model.addProperty("model", OBJ + name + ".obj");
    model.addProperty("ambientocclusion", false);
    model.addProperty("automatic_culling", false);
    model.addProperty("render_type", "minecraft:" + type);
    model.add("textures", textures);
    return model;
  }

  private static JsonObject textures(
      String sprite,
      String root,
      String state,
      JsonObject layers,
      Set<String> materials,
      Path provider,
      Map<Path, byte[]> output)
      throws IOException {
    if (root.startsWith("staff/"))
      return HexStaves.textures(sprite, state.substring(6), materials, provider, output);
    if (root.equals("slate")) return HexStudyMaterials.slate(state, sprite, provider, output);
    if (root.equals("lens") || root.startsWith("scroll"))
      return HexStudyMaterials.textures(state, sprite, materials, provider, output);
    if (root.equals("lore_fragment")) {
      var textures = new JsonObject();
      textures.addProperty("particle", sprite);
      for (String material : materials) textures.addProperty(material, sprite);
      return textures;
    }
    var colors = new LinkedHashMap<String, Integer>();
    colors.put("wood", 0xa26b41);
    colors.put("twig", 0xa26b41);
    colors.put("wrap", 0xa45137);
    colors.put("gem", CARRIERS.contains(root) ? 0xa67bdd : 0xbe8be1);
    colors.put("gem_shadow", 0x674592);
    colors.put("gem_flash", 0xf2dbde);
    colors.put("leather", 0xac673c);
    colors.put("leather_light", 0xe7a65b);
    colors.put("leather_edge", 0x744023);
    colors.put("polished_iron", 0xd8d8d8);
    colors.put("dark_iron", 0x5e5e5e);
    colors.put(
        "metal",
        switch (root) {
          case "trinket", "jeweler_hammer" -> 0xb9b7bd;
          case "artifact" -> 0xf8ba29;
          case "cypher" -> 0xc76538;
          case "spellbook" -> 0xf5b747;
          default -> 0xb26b42;
        });
    colors.put("cover", root.equals("spellbook") ? 0x982537 : 0x503754);
    colors.put("ribbon", 0xa274ce);
    colors.put("paper", state.contains("ancient") ? 0xe6bb75 : 0xf5ebe0);
    colors.put("cord", 0xe4ae5e);
    colors.put("aqua", 0x43cde3);
    colors.put("glass", 0x9fbbce);
    colors.put("glass_flash", 0xe1f3f5);
    colors.put("lip", 0x41536a);
    colors.put(
        "liquid",
        state.startsWith("phial_larger") || state.startsWith("phial_largest")
            ? 0x925bca
            : 0xb57fe6);
    var textures = new JsonObject();
    textures.addProperty("particle", sprite);
    if (materials.contains("pigment")) textures.addProperty("pigment", sprite);
    if (materials.contains("glint"))
      textures.addProperty("glint", layers.get("layer1").getAsString());
    var source = image(sprite);
    // The body, mouth and cord keep their empty-glass palette as the contents rise.
    var glassSource =
        root.equals("battery") ? image(sprite.substring(0, sprite.length() - 1) + "0") : source;
    for (var field : colors.entrySet())
      if (materials.contains(field.getKey()))
        textures.addProperty(
            field.getKey(),
            field(
                nearest(
                    root.equals("battery") && !Set.of("aqua", "liquid").contains(field.getKey())
                        ? glassSource
                        : source,
                    field.getValue()),
                provider,
                output));
    if (!materials.contains("rune")) return textures;
    if (root.equals("focus") && !state.endsWith("sealed")) {
      textures.addProperty("rune", layers.get("layer1").getAsString());
      return textures;
    }
    if (layers.has("layer1")
        && (root.equals("focus") || root.equals("spellbook") || root.equals("thought_knot"))) {
      textures.addProperty("rune", field(0xfff5f0ff, provider, output));
    } else if (layers.has("layer1")) {
      var overlay = image(layers.get("layer1").getAsString());
      textures.addProperty("rune", field(nearest(overlay, 0xd2a0fa), provider, output));
    } else textures.addProperty("rune", textures.get("gem").getAsString());
    return textures;
  }

  private static BufferedImage image(String id) throws IOException {
    String path = "/assets/" + id.replace(":", "/textures/") + ".png";
    try (var input = HexHeldModels.class.getResourceAsStream(path)) {
      if (input == null) throw new IOException("Missing Hex item sprite " + id);
      return ImageIO.read(input);
    }
  }

  private static int nearest(BufferedImage image, int target) {
    int best = Integer.MAX_VALUE, color = target | 0xff000000;
    for (int y = 0; y < Math.min(image.getWidth(), image.getHeight()); y++)
      for (int x = 0; x < image.getWidth(); x++) {
        int pixel = image.getRGB(x, y);
        if ((pixel >>> 24) < 128) continue;
        int r = (pixel >> 16 & 255) - (target >> 16 & 255);
        int g = (pixel >> 8 & 255) - (target >> 8 & 255);
        int b = (pixel & 255) - (target & 255);
        int error = r * r + g * g + b * b;
        if (error < best) {
          best = error;
          color = pixel | 0xff000000;
        }
      }
    return color;
  }

  private static String field(int color, Path provider, Map<Path, byte[]> output)
      throws IOException {
    String id = "item/equipment/hex/" + Integer.toHexString(color).substring(2);
    Path path = provider.resolve("textures/" + id + ".png");
    if (!output.containsKey(path)) {
      var image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
      var random = new java.util.SplittableRandom(color);
      for (int y = 0; y < 8; y++)
        for (int x = 0; x < 8; x++)
          image.setRGB(x, y, EquipmentModels.shaded(color, random.nextInt(-6, 7)));
      output.put(path, EquipmentModels.png(image));
    }
    return "hexcasting:" + id;
  }

  /** OBJ supports independent position, UV and normal indices, shared by adjacent faces. */
  private static String compact(String source) {
    var tables = new LinkedHashMap<String, Map<String, Integer>>();
    var indices = new HashMap<String, List<Integer>>();
    for (String type : List.of("v", "vt", "vn")) {
      tables.put(type, new LinkedHashMap<>());
      indices.put(type, new ArrayList<>());
    }
    var faces = new StringBuilder();
    String material = "";
    for (String line : source.lines().toList()) {
      String[] pair = line.split(" ", 2);
      if (tables.containsKey(pair[0])) {
        var values = tables.get(pair[0]);
        indices.get(pair[0]).add(values.computeIfAbsent(pair[1], ignored -> values.size() + 1));
      } else if (pair[0].equals("usemtl") && !material.equals(pair[1])) {
        material = pair[1];
        faces.append(line).append('\n');
      } else if (pair[0].equals("f")) {
        faces.append("f");
        for (String group : pair[1].split(" ")) {
          int index = Integer.parseInt(group.substring(0, group.indexOf('/'))) - 1;
          faces
              .append(' ')
              .append(indices.get("v").get(index))
              .append('/')
              .append(indices.get("vt").get(index))
              .append('/')
              .append(indices.get("vn").get(index));
        }
        faces.append('\n');
      }
    }
    var obj = new StringBuilder("mtllib equipment.mtl\n");
    tables.forEach(
        (type, values) ->
            values
                .keySet()
                .forEach(line -> obj.append(type).append(' ').append(line).append('\n')));
    return obj.append(faces).toString();
  }
}
