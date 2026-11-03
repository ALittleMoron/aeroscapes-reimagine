package dev.aeroscapes.aquatic;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** Independent equipment meshes and pixel materials; provider display transforms are referenced. */
public final class EquipmentModels {
  private static final Map<String, Integer> METALS =
      Map.of(
          "copper", 0xC87950,
          "iron", 0xB8C2C3,
          "gold", 0xE2BB4C,
          "diamond", 0x4FC6BE,
          "netherite", 0x544C58);
  private static final String MATERIALS = "aeroscapes_reimagine:item/equipment/";

  private EquipmentModels() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length < 1 || arguments.length > 2) {
      throw new IllegalArgumentException(
          "Expected repository root, optionally followed by --check");
    }
    boolean check = arguments.length == 2 && arguments[1].equals("--check");
    if (arguments.length == 2 && !check) {
      throw new IllegalArgumentException("Unknown argument: " + arguments[1]);
    }
    Path repository = Path.of(arguments[0]);
    Map<Path, byte[]> resources = resources(repository);
    for (Map.Entry<Path, byte[]> resource : resources.entrySet()) {
      if (check) {
        if (!Files.isRegularFile(resource.getKey())
            || !Arrays.equals(Files.readAllBytes(resource.getKey()), resource.getValue())) {
          throw new IllegalStateException("Stale equipment resource: " + resource.getKey());
        }
      } else {
        Files.createDirectories(resource.getKey().getParent());
        Files.write(resource.getKey(), resource.getValue());
      }
    }
    System.out.println(
        "Equipment resources " + (check ? "verified: " : "generated: ") + resources.size());
  }

  private static Map<Path, byte[]> resources(Path repository) throws IOException {
    Map<Path, byte[]> output = new LinkedHashMap<>();
    Path assets = repository.resolve("pack/resourcepacks/aeroscapes-reimagine-atmosphere/assets");
    RodModels.addResources(repository, output);
    for (Map.Entry<String, Integer> metal : METALS.entrySet()) {
      String tier = metal.getKey();
      output.put(
          assets.resolve("aeroscapes_reimagine/textures/item/equipment/" + tier + ".png"),
          material(metal.getValue(), false));
      text(
          output,
          assets.resolve("yo_hooks/models/item/" + tier + "_grappling_hook.json"),
          hookModel(tier, true));
      text(
          output,
          assets.resolve("yo_hooks/models/item/" + tier + "_hook_head.json"),
          hookModel(tier, false));
    }
    for (boolean netherite : new boolean[] {false, true}) {
      text(
          output,
          assets.resolve(
              "yo_hooks/models/item/grappling_hook_handle"
                  + (netherite ? "_netherite" : "")
                  + ".json"),
          handleModel(netherite ? "netherite" : "iron"));
    }
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/equipment/grip.png"),
        material(0x654632, true));
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/equipment/rope.png"),
        material(0xB99A64, true));
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/equipment/lunchbox.png"),
        material(0x658776, false));
    text(
        output, assets.resolve("nutritionalbalance/models/item/lunchbox_3d.json"), lunchboxModel());
    return output;
  }

  static String rodDefinition(String name) {
    List<String> cases = new ArrayList<>();
    cases.add(
        "{\"when\": [\"gui\", \"ground\", \"fixed\"], \"model\": "
            + castModels("tide:item/" + name, "tide:item/" + name + "_cast")
            + "}");
    for (String context :
        List.of(
            "thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand")) {
      String suffix =
          (context.startsWith("firstperson") ? "_fp" : "")
              + (context.endsWith("lefthand") ? "_left" : "");
      cases.add(
          "{\"when\": [\""
              + context
              + "\"], \"model\": "
              + castModels(
                  "tide:item/equipment/" + name + "_held" + suffix,
                  "tide:item/equipment/" + name + "_cast_held" + suffix)
              + "}");
    }
    return "{\n  \"model\": {\n    \"type\": \"minecraft:select\",\n"
        + "    \"property\": \"minecraft:display_context\",\n    \"cases\": [\n      "
        + String.join(",\n      ", cases)
        + "\n    ],\n    \"fallback\": "
        + castModels(
            "tide:item/equipment/" + name + "_held", "tide:item/equipment/" + name + "_cast_held")
        + "\n  }\n}\n";
  }

  private static String castModels(String idle, String cast) {
    return "{\"type\": \"minecraft:condition\", \"property\": \"minecraft:fishing_rod/cast\", "
        + "\"on_true\": {\"type\": \"minecraft:model\", \"model\": \""
        + cast
        + "\"}, \"on_false\": {\"type\": \"minecraft:model\", \"model\": \""
        + idle
        + "\"}}";
  }

  private static String hookModel(String tier, boolean held) {
    List<String> cubes = new ArrayList<>();
    if (held) addHandle(cubes);
    cubes.add(box(7.1, 6.5, 7.1, 8.9, 15.5, 8.9, "metal"));
    cubes.add(box(3.5, 10.5, 7.1, 12.5, 12, 8.9, "metal"));
    cubes.add(box(7.1, 10.5, 3.5, 8.9, 12, 12.5, "metal"));
    cubes.add(box(3.5, 10.5, 7.1, 5, 15.5, 8.9, "metal"));
    cubes.add(box(11, 10.5, 7.1, 12.5, 15.5, 8.9, "metal"));
    cubes.add(box(7.1, 10.5, 3.5, 8.9, 15.5, 5, "metal"));
    cubes.add(box(7.1, 10.5, 11, 8.9, 15.5, 12.5, "metal"));
    cubes.add(box(4, 14.5, 7.1, 6, 16, 8.9, "metal"));
    cubes.add(box(10, 14.5, 7.1, 12, 16, 8.9, "metal"));
    cubes.add(box(7.1, 14.5, 4, 8.9, 16, 6, "metal"));
    cubes.add(box(7.1, 14.5, 10, 8.9, 16, 12, "metal"));
    String overrides =
        held
            ? ",\n  \"overrides\": [{\"predicate\": {\"yo_hooks:hook_active\": 1}, \"model\": "
                + "\"yo_hooks:item/grappling_hook_handle"
                + (tier.equals("netherite") ? "_netherite" : "")
                + "\"}]"
            : "";
    return mesh(tier, cubes, hookDisplay(held), overrides);
  }

  private static String handleModel(String tier) {
    List<String> cubes = new ArrayList<>();
    addHandle(cubes);
    cubes.add(box(6.5, 7, 7.1, 7.4, 9.5, 8.9, "metal"));
    cubes.add(box(8.6, 7, 7.1, 9.5, 9.5, 8.9, "metal"));
    cubes.add(box(6.5, 9, 7.1, 9.5, 9.8, 8.9, "metal"));
    return mesh(tier, cubes, hookDisplay(true), "");
  }

  private static void addHandle(List<String> cubes) {
    cubes.add(box(6.5, 0.5, 6.5, 9.5, 7.5, 9.5, "grip"));
    cubes.add(box(6.2, 0.5, 6.2, 9.8, 1.5, 9.8, "metal"));
    cubes.add(box(6.2, 6.5, 6.2, 9.8, 7.8, 9.8, "metal"));
    cubes.add(box(6.35, 2.3, 6.35, 9.65, 3.1, 9.65, "rope"));
    cubes.add(box(6.35, 4.5, 6.35, 9.65, 5.3, 9.65, "rope"));
  }

  private static String hookDisplay(boolean held) {
    String transforms =
        held
            ? "\"thirdperson_righthand\": {\"rotation\": [0, 90, 0], \"translation\": [0, 2, 0],"
                + " \"scale\": [0.8, 0.8, 0.8]},\"thirdperson_lefthand\": {\"rotation\": [0, -90,"
                + " 0], \"translation\": [0, 2, 0], \"scale\": [0.8, 0.8,"
                + " 0.8]},\"firstperson_righthand\": {\"rotation\": [0, -75, 20],"
                + " \"translation\": [1.5, 2, 0], \"scale\": [0.7, 0.7,"
                + " 0.7]},\"firstperson_lefthand\": {\"rotation\": [0, 75, -20], \"translation\":"
                + " [1.5, 2, 0], \"scale\": [0.7, 0.7, 0.7]},"
            : "";
    // The projectile adds 135 degrees and (-0.07, -0.055); keep its stem at the rope origin.
    String ground =
        held
            ? "{\"translation\": [0, 2, 0], \"scale\": [0.5, 0.5, 0.5]}"
            : "{\"rotation\": [0, 0, 45], \"translation\": [0.58967, 1.41033, 0], \"scale\": [0.5,"
                + " 0.5, 0.5]}";
    return "{"
        + transforms
        + "\"gui\": {\"rotation\": [22.5, -35, -20], \"scale\": [0.8, 0.8, 0.8]},"
        + "\"ground\": "
        + ground
        + ","
        + "\"fixed\": {\"rotation\": [0, 180, 0], \"scale\": [0.8, 0.8, 0.8]}}";
  }

  private static String lunchboxModel() {
    List<String> cubes = new ArrayList<>();
    cubes.add(box(2.5, 0.5, 5, 13.5, 1.3, 11, "box"));
    cubes.add(box(2.5, 1.3, 5, 3.5, 10.4, 11, "box"));
    cubes.add(box(12.5, 1.3, 5, 13.5, 10.4, 11, "box"));
    cubes.add(box(3.5, 9.7, 5, 12.5, 10.4, 11, "box"));
    // Native food previews sit at z=7.2/8.8; these inset panels leave both visible.
    cubes.add(box(3.5, 1.3, 7.5, 12.5, 9.7, 8.5, "box"));
    cubes.add(box(2.3, 9.6, 4.8, 13.7, 10.2, 5.5, "metal"));
    cubes.add(box(2.3, 9.6, 10.5, 13.7, 10.2, 11.2, "metal"));
    cubes.add(box(4.7, 10.4, 7.2, 5.7, 13.5, 8.8, "grip"));
    cubes.add(box(10.3, 10.4, 7.2, 11.3, 13.5, 8.8, "grip"));
    cubes.add(box(4.7, 12.5, 7.2, 11.3, 13.5, 8.8, "grip"));
    cubes.add(box(7, 9.7, 4.5, 9, 11, 5.5, "metal"));
    cubes.add(box(7, 9.7, 10.5, 9, 11, 11.5, "metal"));
    return mesh("iron", cubes, "{}", "");
  }

  private static String mesh(String metal, List<String> cubes, String display, String overrides) {
    return "{\n  \"parent\": \"minecraft:block/block\",\n  \"ambientocclusion\": false,\n"
        + "  \"textures\": {\"particle\": \""
        + MATERIALS
        + metal
        + "\", \"metal\": \""
        + MATERIALS
        + metal
        + "\", \"grip\": \""
        + MATERIALS
        + "grip\", \"rope\": \""
        + MATERIALS
        + "rope\", \"box\": \""
        + MATERIALS
        + "lunchbox\"},\n  \"elements\": [\n    "
        + String.join(",\n    ", cubes)
        + "\n  ],\n  \"display\": "
        + display
        + overrides
        + "\n}\n";
  }

  private static String box(
      double x1, double y1, double z1, double x2, double y2, double z2, String texture) {
    List<String> faces = new ArrayList<>();
    for (String direction : List.of("north", "east", "south", "west", "up", "down")) {
      // Native BlockElement projection keeps texel density proportional on thin faces.
      double[] uv =
          switch (direction) {
            case "north" -> new double[] {16 - x2, 16 - y2, 16 - x1, 16 - y1};
            case "east" -> new double[] {16 - z2, 16 - y2, 16 - z1, 16 - y1};
            case "south" -> new double[] {x1, 16 - y2, x2, 16 - y1};
            case "west" -> new double[] {z1, 16 - y2, z2, 16 - y1};
            case "up" -> new double[] {x1, z1, x2, z2};
            case "down" -> new double[] {x1, 16 - z2, x2, 16 - z1};
            default -> throw new IllegalStateException("Unknown cube face: " + direction);
          };
      faces.add(
          String.format(
              Locale.ROOT,
              "\"%s\": {\"uv\": [%.4f, %.4f, %.4f, %.4f], \"texture\": \"#%s\"}",
              direction,
              uv[0],
              uv[1],
              uv[2],
              uv[3],
              texture));
    }
    return String.format(
        Locale.ROOT,
        "{\"from\": [%s, %s, %s], \"to\": [%s, %s, %s], \"faces\": {%s}}",
        x1,
        y1,
        z1,
        x2,
        y2,
        z2,
        String.join(", ", faces));
  }

  private static byte[] material(int color, boolean grain) throws IOException {
    BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 16; y++) {
      for (int x = 0; x < 16; x++) {
        int variation = Math.floorMod(x * 11 + y * 7, 7) - 3;
        int shade = grain ? Math.floorMod(x + y / 5, 4) * 5 - 7 : (15 - x - y) / 2;
        image.setRGB(x, y, shaded(color, shade + variation));
      }
    }
    return png(image);
  }

  static int shaded(int color, int difference) {
    int red = Math.clamp((color >> 16 & 255) + difference, 0, 255);
    int green = Math.clamp((color >> 8 & 255) + difference, 0, 255);
    int blue = Math.clamp((color & 255) + difference, 0, 255);
    return 0xFF000000 | red << 16 | green << 8 | blue;
  }

  static byte[] png(BufferedImage image) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer is unavailable");
    return output.toByteArray();
  }

  static void text(Map<Path, byte[]> output, Path path, String content) {
    if (output.put(path, content.getBytes(StandardCharsets.UTF_8)) != null) {
      throw new IllegalStateException("Duplicate equipment resource: " + path);
    }
  }
}
