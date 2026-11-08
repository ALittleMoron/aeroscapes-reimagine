package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Working heads and square shafts in the native coral and shell palettes. */
final class AquaticToolModels {
  private static final String DIRECTORY = "aeroscapes_reimagine:models/item/aquatic_tools/";
  static final List<String> ITEMS =
      List.of(
          "coral_axe",
          "coral_blade",
          "coral_pickaxe",
          "coral_shovel",
          "seashell_axe",
          "seashell_hoe",
          "seashell_pickaxe",
          "seashell_shovel",
          "seashell_spear");

  private AquaticToolModels() {}

  static boolean supports(String id) {
    return id.startsWith("hybrid_aquatic:") && ITEMS.contains(id.substring(15));
  }

  static JsonObject model(String id, JsonObject original) {
    JsonObject model = new JsonObject();
    model.addProperty("loader", "neoforge:obj");
    model.addProperty("model", DIRECTORY + id.substring(15) + ".obj");
    model.addProperty("automatic_culling", false);
    model.addProperty("flip_v", false);
    model.addProperty("ambientocclusion", false);
    model.addProperty("render_type", "minecraft:cutout");
    model.add("textures", original.get("textures").deepCopy());
    model.getAsJsonObject("textures").addProperty("particle", "#layer0");
    JsonObject display =
        JsonParser.parseString(
                """
        {
          "thirdperson_righthand":{"rotation":[0,90,0],"translation":[0,1.25,1.5],"scale":[0.95,0.95,0.95]},
          "thirdperson_lefthand":{"rotation":[0,90,0],"translation":[0,1.25,1.5],"scale":[0.95,0.95,0.95]},
          "firstperson_righthand":{"rotation":[15,90,5],"translation":[1.25,2.5,0.5],"scale":[0.6,0.6,0.6]},
          "firstperson_lefthand":{"rotation":[15,90,5],"translation":[1.25,2.5,0.5],"scale":[0.6,0.6,0.6]}
        }
        """)
            .getAsJsonObject();
    for (var entry : display.entrySet()) {
      boolean left = entry.getKey().endsWith("lefthand");
      var rotation = entry.getValue().getAsJsonObject().getAsJsonArray("rotation");
      if (id.endsWith("_hoe")) rotation.set(1, new com.google.gson.JsonPrimitive(-180));
      else if (id.endsWith("_axe"))
        rotation.set(1, new com.google.gson.JsonPrimitive(left ? -90 : 90));
      else if (id.endsWith("_pickaxe"))
        rotation.set(1, new com.google.gson.JsonPrimitive(left ? 90 : -90));
      if (entry.getKey().startsWith("thirdperson")) {
        if (id.endsWith("_spear")) {
          rotation.set(0, new com.google.gson.JsonPrimitive(-36));
          rotation.set(1, new com.google.gson.JsonPrimitive(140));
        } else if (id.endsWith("_blade")) {
          rotation.set(0, new com.google.gson.JsonPrimitive(-30));
          rotation.set(1, new com.google.gson.JsonPrimitive(140));
        } else if (id.endsWith("_pickaxe")) rotation.set(0, new com.google.gson.JsonPrimitive(-25));
        else if (id.equals("hybrid_aquatic:coral_axe"))
          rotation.set(0, new com.google.gson.JsonPrimitive(-5));
      }
    }
    HeldItemModels.anchorHeld(
        display, new org.joml.Vector3f(8, 5, 8), new org.joml.Vector3f(0, -2, 2));
    model.add("display", display);
    return model;
  }

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    Path directory = assets.resolve("aeroscapes_reimagine/models/item/aquatic_tools");
    StringBuilder materials = new StringBuilder();
    for (String item : ITEMS) {
      boolean coral = item.startsWith("coral_");
      String family = coral ? "coral" : "shell";
      EquipmentObjMesh mesh = new EquipmentObjMesh();
      for (var entry : regions().entrySet()) {
        String key =
            (coral && !entry.getKey().equals("shaft") ? item : family) + "_" + entry.getKey();
        mesh.material(entry.getKey(), key);
        double[] uv = entry.getValue();
        mesh.region(
            key, (uv[0] + .15) / 16, (uv[1] + .15) / 16, (uv[2] - .15) / 16, (uv[3] - .15) / 16);
      }
      build(mesh, item);
      output.put(directory.resolve(item + ".obj"), mesh.obj().getBytes(StandardCharsets.UTF_8));
      if (coral) {
        output.put(
            assets.resolve(
                "aeroscapes_reimagine/textures/item/aquatic_tools/" + item + "_surface.png"),
            EquipmentModels.png(CoralToolMaterials.surfaceTexture(item)));
        for (String material :
            regions().keySet().stream().filter(key -> !key.equals("shaft")).sorted().toList())
          materials
              .append("newmtl ")
              .append(item)
              .append('_')
              .append(material)
              .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd aeroscapes_reimagine:item/aquatic_tools/")
              .append(item)
              .append("_surface\n\n");
      }
    }
    for (boolean coral : new boolean[] {true, false}) {
      String family = coral ? "coral" : "shell";
      output.put(
          assets.resolve(
              "aeroscapes_reimagine/textures/item/aquatic_tools/" + family + "_shaft.png"),
          EquipmentModels.png(shaftTexture(coral)));
      if (!coral)
        output.put(
            assets.resolve("aeroscapes_reimagine/textures/item/aquatic_tools/shell_surface.png"),
            EquipmentModels.png(ShellToolMaterials.surfaceTexture()));
      for (String material :
          regions().keySet().stream()
              .filter(key -> !coral || key.equals("shaft"))
              .sorted()
              .toList())
        materials
            .append("newmtl ")
            .append(family)
            .append('_')
            .append(material)
            .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd ")
            .append(
                material.equals("shaft")
                    ? "aeroscapes_reimagine:item/aquatic_tools/" + family + "_shaft"
                    : "aeroscapes_reimagine:item/aquatic_tools/" + family + "_surface")
            .append("\n\n");
    }
    output.put(
        directory.resolve("equipment.mtl"),
        (materials.toString().stripTrailing() + "\n").getBytes(StandardCharsets.UTF_8));
  }

  private static BufferedImage shaftTexture(boolean coral) throws IOException {
    BufferedImage source =
        NativeEquipmentModels.image(
            "hybrid_aquatic:item/" + (coral ? "coral_axe" : "seashell_axe"));
    BufferedImage texture = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
    int body = source.getRGB(4, 12), light = source.getRGB(3, 14), dark = source.getRGB(6, 12);
    for (int y = 0; y < 32; y++)
      for (int x = 0; x < 32; x++) {
        boolean highlight = (x % 2 == 0 && y >= 4 && y <= 9) || (x % 2 == 1 && y >= 17 && y <= 24);
        boolean recess = y >= 28 || (x % 2 == 1 && y >= 11 && y <= 14);
        texture.setRGB(x, y, highlight ? light : recess ? dark : body);
      }
    return texture;
  }

  private static Map<String, double[]> regions() {
    return Map.of(
        "shaft", new double[] {0, 0, 16, 16},
        "head", new double[] {0, 0, 8, 8},
        "edge", new double[] {8, 0, 12, 4},
        "rib", new double[] {12, 0, 16, 4},
        "shadow", new double[] {8, 4, 12, 8},
        "inner", new double[] {0, 8, 8, 16},
        "socket", new double[] {8, 8, 12, 12});
  }

  private static void build(EquipmentObjMesh mesh, String item) {
    boolean blade = item.endsWith("_blade"), spear = item.endsWith("_spear");
    double top = blade ? 18 : spear ? 22 : 17.8;
    mesh.group("shaft");
    shaft(mesh, top);
    if (item.startsWith("coral_")) {
      mesh.group("socket");
      double socket = blade ? 16.5 : 16;
      mesh.box("socket", 6.65, socket, 6.65, 9.35, socket + 2, 9.35);
    }
    switch (item) {
      case "seashell_axe" -> ShellToolMeshes.axe(mesh);
      case "seashell_hoe" -> ShellToolMeshes.hoe(mesh);
      case "seashell_pickaxe" -> ShellToolMeshes.pickaxe(mesh);
      case "seashell_shovel" -> ShellToolMeshes.shovel(mesh);
      case "seashell_spear" -> ShellToolMeshes.spear(mesh);
      case "coral_axe" -> CoralToolMeshes.axe(mesh);
      case "coral_pickaxe" -> CoralToolMeshes.pickaxe(mesh);
      case "coral_shovel" -> CoralToolMeshes.shovel(mesh);
      case "coral_blade" -> CoralToolMeshes.blade(mesh);
      default -> throw new IllegalArgumentException(item);
    }
  }

  private static void shaft(EquipmentObjMesh mesh, double top) {
    double[][] side = {{0, 0}, {2.0 / 32, 0}, {2.0 / 32, top / 32}, {0, top / 32}};
    double[][] cap = {{0, 0}, {2.0 / 32, 0}, {2.0 / 32, 2.0 / 32}, {0, 2.0 / 32}};
    P a = new P(7, 0, 7), b = new P(9, 0, 7), c = new P(9, top, 7), d = new P(7, top, 7);
    P e = new P(7, 0, 9), f = new P(9, 0, 9), g = new P(9, top, 9), h = new P(7, top, 9);
    mesh.quad("shaft", new P[] {a, b, c, d}, new P(0, 0, -1), side);
    mesh.quad("shaft", new P[] {e, f, g, h}, new P(0, 0, 1), side);
    mesh.quad("shaft", new P[] {a, e, h, d}, new P(-1, 0, 0), side);
    mesh.quad("shaft", new P[] {b, f, g, c}, new P(1, 0, 0), side);
    mesh.quad("shaft", new P[] {d, c, g, h}, new P(0, 1, 0), cap);
    mesh.quad("shaft", new P[] {a, b, f, e}, new P(0, -1, 0), cap);
  }
}
