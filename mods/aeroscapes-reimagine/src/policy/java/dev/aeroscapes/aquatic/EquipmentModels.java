package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.atmosphere.EquipmentStyle;
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
    artwork(repository, resources, !check);
    System.out.println(
        "Equipment resources " + (check ? "verified: " : "generated: ") + resources.size());
  }

  private static void artwork(Path repository, Map<Path, byte[]> resources, boolean write)
      throws IOException {
    for (var entry : resources.entrySet()) {
      if (!entry.getKey().endsWith("atlases/blocks.json")) continue;
      var atlas =
          JsonParser.parseString(new String(entry.getValue(), StandardCharsets.UTF_8))
              .getAsJsonObject();
      for (var element : atlas.getAsJsonArray("sources")) {
        var source = element.getAsJsonObject();
        if (!source.get("type").getAsString().equals("aeroscapes_reimagine:equipment_paint"))
          continue;
        String sprite = source.get("sprite").getAsString();
        String id = sprite.replace(":item/equipment/painted/", ":");
        var profile =
            EquipmentStyle.valueOf(source.get("profile").getAsString().toUpperCase(Locale.ROOT));
        byte[] pixels;
        try {
          pixels = EquipmentSculpture.texture(id, profile);
        } catch (IllegalArgumentException failure) {
          throw new IOException("Invalid equipment paint " + id, failure);
        }
        if (write) {
          Path preview =
              repository.resolve(
                  ".runtime/equipment-artwork/assets/"
                      + sprite.replace(":", "/textures/")
                      + ".png");
          Files.createDirectories(preview.getParent());
          Files.write(preview, pixels);
        }
      }
    }
  }

  private static Map<Path, byte[]> resources(Path repository) throws IOException {
    Map<Path, byte[]> output = new LinkedHashMap<>();
    Path assets = repository.resolve("pack/resourcepacks/aeroscapes-reimagine-atmosphere/assets");
    output.put(assets.resolve("yo_hooks/textures/entity/hook_rope.png"), chainTexture());
    try (var license = EquipmentModels.class.getResourceAsStream("/LICENSE-yo_hooks")) {
      if (license == null)
        throw new IOException("Missing Yo Hooks MIT attribution for its chain texture");
      output.put(assets.getParent().resolve("licenses/yo_hooks-MIT.txt"), license.readAllBytes());
    }
    RodModels.addResources(repository, output);
    ToolModels.addResources(repository, output);
    Map<String, JsonObject> hooks = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> metal : METALS.entrySet()) {
      String tier = metal.getKey();
      output.put(
          assets.resolve("aeroscapes_reimagine/textures/item/equipment/" + tier + ".png"),
          material(metal.getValue(), false));
      hooks.put(
          "yo_hooks:" + tier + "_grappling_hook",
          JsonParser.parseString(hookModel(tier, true)).getAsJsonObject());
      hooks.put(
          "yo_hooks:" + tier + "_hook_head",
          JsonParser.parseString(hookModel(tier, false)).getAsJsonObject());
    }
    for (boolean netherite : new boolean[] {false, true}) {
      hooks.put(
          "yo_hooks:grappling_hook_handle" + (netherite ? "_netherite" : ""),
          JsonParser.parseString(handleModel(netherite ? "netherite" : "iron")).getAsJsonObject());
    }
    HeldItemModels.add(assets, output, hooks);
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/equipment/grip.png"),
        material(0x654632, true));
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/equipment/rope.png"),
        material(0xB99A64, true));
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/equipment/chain.png"),
        material(0x283E51, false));
    text(
        output,
        assets.resolve("nutritionalbalance/models/item/lunchbox_3d.json"),
        HeldItemModels.nativeModel("nutritionalbalance:lunchbox_3d").toString());
    JsonObject lunchbox = HeldItemModels.nativeModel("nutritionalbalance:lunchbox");
    JsonObject lunchboxDisplay = lunchbox.getAsJsonObject("display");
    JsonObject heldDisplay =
        JsonParser.parseString(
                """
                {
                  "firstperson_righthand": {"rotation": [-14, 5, 5], "scale": [0.68, 0.68, 0.68]},
                  "firstperson_lefthand": {"rotation": [-14, 5, 5], "scale": [0.68, 0.68, 0.68]},
                  "thirdperson_righthand": {"rotation": [90, 0, 0], "scale": [0.72, 0.72, 0.72]},
                  "thirdperson_lefthand": {"rotation": [90, 0, 0], "scale": [0.72, 0.72, 0.72]}
                }
                """)
            .getAsJsonObject();
    // The native renderer draws the box and selected food under this outer pose.
    for (var hand : heldDisplay.entrySet()) {
      JsonObject transform = new JsonObject();
      transform.add(hand.getKey(), hand.getValue());
      if (hand.getKey().startsWith("firstperson"))
        HeldItemModels.anchorHeld(
            transform, new org.joml.Vector3f(8, 2, 8), new org.joml.Vector3f(0, 1.5f, -1.25f));
      else
        HeldItemModels.anchorHeld(
            transform, new org.joml.Vector3f(8, 14.275f, 8), new org.joml.Vector3f(0, -1, 2));
      lunchboxDisplay.add(hand.getKey(), hand.getValue());
    }
    text(
        output,
        assets.resolve("nutritionalbalance/models/item/lunchbox.json"),
        lunchbox.toString());
    text(
        output,
        assets.resolve("nutritionalbalance/models/item/equipment/held/lunchbox.json"),
        NativeEquipmentModels.sculpt("nutritionalbalance:lunchbox_3d", EquipmentStyle.LUNCHBOX)
            .toString());
    JsonObject atlas = new JsonObject();
    com.google.gson.JsonArray sprites = new com.google.gson.JsonArray();
    sprites.add(
        EquipmentSculpture.spriteSource("nutritionalbalance:lunchbox_3d", EquipmentStyle.LUNCHBOX));
    atlas.add("sources", sprites);
    text(output, assets.resolve("minecraft/atlases/blocks.json"), atlas.toString());
    return output;
  }

  private static byte[] chainTexture() throws IOException {
    BufferedImage original = NativeEquipmentModels.image("yo_hooks:entity/hook_rope");
    BufferedImage recolored =
        new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < original.getHeight(); y++)
      for (int x = 0; x < original.getWidth(); x++) {
        int color = original.getRGB(x, y);
        int rgb =
            switch (color & 0xFFFFFF) {
              case 0x252C3D -> 0x657481;
              case 0x3E4453 -> 0x9AA7B3;
              case 0x495065 -> 0xD8E0E6;
              default -> color & 0xFFFFFF;
            };
        recolored.setRGB(x, y, (color & 0xFF000000) | rgb);
      }
    return png(recolored);
  }

  static String rodDefinition(String namespace, String name) {
    List<String> cases = new ArrayList<>();
    cases.add(
        "{\"when\": [\"gui\", \"ground\", \"fixed\"], \"model\": "
            + castModels(namespace + ":item/" + name, namespace + ":item/" + name + "_cast")
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
                  namespace + ":item/equipment/" + name + "_held" + suffix,
                  namespace + ":item/equipment/" + name + "_cast_held" + suffix)
              + "}");
    }
    return "{\n  \"model\": {\n    \"type\": \"minecraft:select\",\n"
        + "    \"property\": \"minecraft:display_context\",\n    \"cases\": [\n      "
        + String.join(",\n      ", cases)
        + "\n    ],\n    \"fallback\": "
        + castModels(namespace + ":item/" + name, namespace + ":item/" + name + "_cast")
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
    cubes.add(box(7.45, 6.5, 7.45, 8.55, 13.5, 8.55, "metal"));
    for (int side : new int[] {-1, 1}) {
      double x1 = side < 0 ? 4.6 : 8, x2 = side < 0 ? 8 : 11.4;
      cubes.add(rotatedBox(x1, 10.2, 7.45, x2, 11, 8.55, "metal", "z", side * 22.5, 8, 10.6, 8));
      double x = side < 0 ? 4.8 : 10.4;
      cubes.add(
          rotatedBox(x, 11.5, 7.55, x + .8, 14, 8.45, "metal", "z", side * 22.5, x + .4, 11.5, 8));
      double z1 = side < 0 ? 4.6 : 8, z2 = side < 0 ? 8 : 11.4;
      cubes.add(rotatedBox(7.45, 10.2, z1, 8.55, 11, z2, "metal", "x", -side * 22.5, 8, 10.6, 8));
      double z = side < 0 ? 4.8 : 10.4;
      cubes.add(
          rotatedBox(7.55, 11.5, z, 8.45, 14, z + .8, "metal", "x", -side * 22.5, 8, 11.5, z + .4));
    }
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
    cubes.add(box(7, .5, 7, 9, 7.5, 9, "grip"));
    cubes.add(box(6.8, .5, 6.8, 9.2, 1.1, 9.2, "metal"));
    cubes.add(box(6.8, 6.8, 6.8, 9.2, 7.5, 9.2, "metal"));
    cubes.add(box(8.6, 8.5, 8.3, 10.4, 8.8, 8.65, "chain"));
    for (int ring = 0; ring < 2; ring++) {
      double y = 4.8 + ring * 1.6;
      if (ring == 0) {
        cubes.add(box(9, y, 9.3, 9.35, y + 3, 9.75, "chain"));
        cubes.add(box(10.85, y, 9.3, 11.2, y + 3, 9.75, "chain"));
        cubes.add(box(9, y, 9.3, 11.2, y + .35, 9.75, "chain"));
        cubes.add(box(9, y + 2.65, 9.3, 11.2, y + 3, 9.75, "chain"));
      } else {
        cubes.add(box(10.15, y, 8.4, 10.6, y + 3, 8.75, "chain"));
        cubes.add(box(10.15, y, 10.65, 10.6, y + 3, 11, "chain"));
        cubes.add(box(10.15, y, 8.4, 10.6, y + .35, 11, "chain"));
        cubes.add(box(10.15, y + 2.65, 8.4, 10.6, y + 3, 11, "chain"));
      }
    }
  }

  private static String rotatedBox(
      double x1,
      double y1,
      double z1,
      double x2,
      double y2,
      double z2,
      String texture,
      String axis,
      double angle,
      double ox,
      double oy,
      double oz) {
    JsonObject part =
        JsonParser.parseString(box(x1, y1, z1, x2, y2, z2, texture)).getAsJsonObject();
    JsonObject rotation = new JsonObject();
    rotation.addProperty("axis", axis);
    rotation.addProperty("angle", angle);
    rotation.add("origin", JsonParser.parseString("[" + ox + "," + oy + "," + oz + "]"));
    part.add("rotation", rotation);
    return part.toString();
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
        + (metal.equals("netherite") ? "netherite" : "grip")
        + "\", \"chain\": \""
        + MATERIALS
        + "chain\"},\n  \"elements\": [\n    "
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
