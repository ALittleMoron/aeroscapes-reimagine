package dev.aeroscapes.sky;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Owned biome anatomy on the pinned combat rig, with installed block-material surfaces. */
public final class SkyWhaleArtwork {
  public static final List<String> VARIANTS =
      List.of(
          "stone",
          "moss",
          "rooted",
          "fungal",
          "granite",
          "chalk",
          "dripstone",
          "basalt",
          "lava",
          "sandstone",
          "reef",
          "snow",
          "cloud");
  private static final String SOURCE = "assets/skywhales/";
  private static final String SOURCE_SHA512 =
      "ebecabc1ddb97acce6742ea62730e59dcd878fb68997086c766df7b50cba790216b22fac4b675ae7e1251af4092fde9f1bccb302b302489c3ec51584c09e9342";
  private static final String OWNED = "assets/aeroscapes_reimagine/";
  private static final Set<String> ORGANIC = Set.of("moss", "rooted", "fungal", "reef");
  private static final List<String> FACES = List.of("north", "south", "east", "west", "up", "down");

  private SkyWhaleArtwork() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 1) throw new IllegalArgumentException("Expected repository root");
    Path resources =
        Path.of(arguments[0])
            .toAbsolutePath()
            .normalize()
            .resolve("mods/aeroscapes-reimagine/src/main/resources");
    NativeArt original = nativeArt();
    var json = new GsonBuilder().setPrettyPrinting().create();
    JsonObject layouts = new JsonObject();
    for (String variant : VARIANTS) {
      Art art = art(original.geometry(), variant);
      Path model = resources.resolve(OWNED + "geo/whales/" + variant + ".geo.json");
      Files.createDirectories(model.getParent());
      Files.writeString(model, json.toJson(art.geometry()) + "\n");
      layouts.add(variant, art.layout());
    }
    Files.writeString(
        resources.resolve(OWNED + "whales/material_layout.json"), json.toJson(layouts) + "\n");
    System.out.println(
        "Generated thirteen biome whale anatomies and explicit installed-material layouts");
  }

  static NativeArt nativeArt() throws IOException {
    var resource = SkyWhaleArtwork.class.getResource("/" + SOURCE + "geo/sky_whale.geo.json");
    if (resource == null || !(resource.openConnection() instanceof JarURLConnection connection))
      throw new IOException("Missing pinned Sky Whales artwork artifact on the policy classpath");
    connection.setUseCaches(false);
    try (var archive = connection.getJarFile()) {
      try {
        byte[] bytes = Files.readAllBytes(Path.of(archive.getName()));
        String actual =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(bytes));
        if (!SOURCE_SHA512.equals(actual))
          throw new IOException("Re-audit whale artwork against the changed Sky Whales artifact");
      } catch (NoSuchAlgorithmException failure) {
        throw new IOException("SHA-512 is unavailable", failure);
      }
      JsonObject geometry =
          JsonParser.parseString(
                  new String(
                      read(archive, SOURCE + "geo/sky_whale.geo.json"), StandardCharsets.UTF_8))
              .getAsJsonObject();
      BufferedImage image =
          ImageIO.read(
              new ByteArrayInputStream(read(archive, SOURCE + "textures/entity/sky_whale.png")));
      if (image == null || image.getWidth() != 1024 || image.getHeight() != 1024)
        throw new IOException("Changed native whale texture dimensions");
      return new NativeArt(geometry, image);
    }
  }

  private static byte[] read(ZipFile archive, String path) throws IOException {
    var entry = archive.getEntry(path);
    if (entry == null) throw new IOException("Missing pinned whale artwork: " + path);
    try (var input = archive.getInputStream(entry)) {
      return input.readAllBytes();
    }
  }

  public record Art(JsonObject geometry, JsonObject layout) {}

  public static Art art(JsonObject original, String variant) {
    requireVariant(variant);
    Rig rig = new Rig(original, variant);
    JsonObject palette = materials(variant);
    extraMaterials(palette, variant);
    rig.patchSlot = palette.getAsJsonArray("slots").size() - 1;
    anatomy(rig, variant);
    creature(rig);
    ecology(rig, variant);
    if (variant.equals("lava")) coolArmor(rig);
    resolveOverlaps(rig);
    separateBones(rig);
    resolveOverlaps(rig);
    rig.eyes();
    JsonObject layout = materials(variant);
    layout.addProperty("schema", 2);
    extraMaterials(layout, variant);
    JsonArray surfaces = new JsonArray();
    layout.add("decorations", surfaces);
    layout.add("eyes", rig.eyes);
    List<FaceRegion> regions = new ArrayList<>();
    for (JsonObject bone : rig.bones.values()) {
      JsonArray cubes = bone.getAsJsonArray("cubes");
      for (int index = 0; index < cubes.size(); index++) {
        JsonObject cube = cubes.get(index).getAsJsonObject();
        JsonObject surface = new JsonObject();
        surface.addProperty("bone", bone.get("name").getAsString());
        surface.addProperty("cube", index);
        if (cube.has("_role")) surface.add("role", cube.remove("_role"));
        else surface.add("slot", cube.remove("_slot"));
        if (cube.has("_sampling")) surface.add("sampling", cube.remove("_sampling"));
        if (cube.has("_faces")) surface.add("faces", cube.remove("_faces"));
        if (cube.has("_attachment")) surface.add("attachment", cube.remove("_attachment"));
        surfaces.add(surface);
        JsonObject uv = new JsonObject();
        int[] size = integers(cube.getAsJsonArray("size"));
        for (String face : FACES) {
          int width = face.equals("east") || face.equals("west") ? size[2] : size[0];
          int height = face.equals("up") || face.equals("down") ? size[2] : size[1];
          if (width == 0 || height == 0) continue;
          JsonObject mapping = new JsonObject();
          mapping.add("uv", numbers(0, 0));
          mapping.add("uv_size", numbers(width, height));
          uv.add(face, mapping);
          regions.add(new FaceRegion(mapping, width, height));
        }
        cube.add("uv", uv);
      }
    }
    regions.sort(
        Comparator.comparingInt(FaceRegion::height)
            .reversed()
            .thenComparing(Comparator.comparingInt(FaceRegion::width).reversed()));
    int[] skyline = new int[1024];
    for (FaceRegion region : regions) {
      int selectedX = -1;
      int selectedY = Integer.MAX_VALUE;
      for (int x = 0; x + region.width() <= 1024; x++) {
        int y = 0;
        for (int column = x; column < Math.min(1024, x + region.width() + 1); column++)
          y = Math.max(y, skyline[column]);
        if (y < selectedY) {
          selectedX = x;
          selectedY = y;
        }
      }
      if (selectedX < 0 || selectedY + region.height() > 1024)
        throw new IllegalStateException("Whale atlas exhausted for " + variant);
      region.mapping().add("uv", numbers(selectedX, selectedY));
      for (int column = selectedX;
          column < Math.min(1024, selectedX + region.width() + 1);
          column++) skyline[column] = selectedY + region.height() + 1;
    }
    JsonArray clearance = new JsonArray();
    Bounds modelBounds = new Bounds();
    for (JsonObject bone : rig.bones.values()) {
      Bounds bounds = boneBounds(rig, bone);
      if (bounds.empty()) continue;
      clearance.add(bounds.json());
      modelBounds.include(bounds);
    }
    layout.add("spawn_clearance", clearance);
    layout.add("model_bounds", modelBounds.json());
    JsonObject description = rig.model.getAsJsonObject("description");
    description.addProperty(
        "visible_bounds_width",
        Math.ceil(
            Math.max(
                    Math.max(Math.abs(modelBounds.minimum[0]), Math.abs(modelBounds.maximum[0])),
                    Math.max(Math.abs(modelBounds.minimum[2]), Math.abs(modelBounds.maximum[2])))
                / 8.0));
    description.addProperty(
        "visible_bounds_height",
        Math.ceil((modelBounds.maximum[1] - modelBounds.minimum[1]) / 16.0));
    description.add(
        "visible_bounds_offset",
        numbers(0, (modelBounds.maximum[1] + modelBounds.minimum[1]) / 32.0, 0));
    return new Art(rig.geometry, layout);
  }

  private record FaceRegion(JsonObject mapping, int width, int height) {}

  public static WhaleTextureComposer.Pixels pixels(BufferedImage image) {
    return new WhaleTextureComposer.Pixels(
        image.getWidth(),
        image.getHeight(),
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
  }

  public static BufferedImage image(WhaleTextureComposer.Pixels pixels) {
    BufferedImage image =
        new BufferedImage(pixels.width(), pixels.height(), BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, pixels.width(), pixels.height(), pixels.argb(), 0, pixels.width());
    return image;
  }

  private static JsonObject materials(String variant) {
    requireVariant(variant);
    String stone = "minecraft:block/stone";
    String moss = "minecraft:block/moss_block";
    String log = "minecraft:block/oak_log";
    String basalt = "minecraft:block/basalt_side";
    JsonObject skin;
    JsonObject coat;
    JsonObject edge;
    JsonObject growth;
    JsonObject[] slots;
    switch (variant) {
      case "moss" -> {
        skin = surface(stone);
        coat = surface(moss);
        edge = surface("minecraft:block/dirt");
        growth = coat;
        slots =
            new JsonObject[] {
              skin, coat, surface(log, "minecraft:block/oak_log_top", log), coat, skin, skin
            };
      }
      case "rooted" -> {
        skin = surface("minecraft:block/rooted_dirt");
        coat = surface(moss);
        edge = skin;
        growth = surface(log);
        slots =
            new JsonObject[] {
              skin, coat, surface(log, "minecraft:block/oak_log_top", log), coat, skin, skin
            };
      }
      case "fungal" -> {
        skin = surface(stone);
        coat =
            surface(
                "minecraft:block/mycelium_side",
                "minecraft:block/mycelium_top",
                "minecraft:block/dirt");
        edge = surface("minecraft:block/dirt");
        growth = surface("minecraft:block/mushroom_stem");
        slots =
            new JsonObject[] {
              skin,
              surface("minecraft:block/brown_mushroom_block"),
              surface("minecraft:block/red_mushroom_block"),
              growth,
              skin,
              skin
            };
      }
      case "granite" -> {
        skin = surface("minecraft:block/granite");
        coat = skin;
        edge = skin;
        growth = skin;
        slots = repeated(skin);
      }
      case "chalk" -> {
        skin = surface("natures_spirit:block/white_chalk");
        coat = skin;
        edge = skin;
        growth = skin;
        slots = repeated(skin);
      }
      case "dripstone" -> {
        skin = surface("minecraft:block/dripstone_block");
        coat = skin;
        edge = skin;
        growth = skin;
        slots = repeated(skin);
        slots[1] = surface("minecraft:block/pointed_dripstone_down_middle");
      }
      case "basalt" -> {
        skin = surface(basalt, "minecraft:block/basalt_top", "minecraft:block/basalt_top");
        coat = skin;
        edge = skin;
        growth = skin;
        slots = repeated(skin);
        slots[1] = surface("minecraft:block/dripstone_block");
      }
      case "lava" -> {
        skin = surface("minecraft:block/magma");
        coat = surface(basalt, "minecraft:block/basalt_top", "minecraft:block/basalt_top");
        edge = surface("minecraft:block/blackstone");
        growth = coat;
        slots = repeated(coat);
        slots[1] = skin;
      }
      case "sandstone" -> {
        skin =
            surface(
                "minecraft:block/sandstone",
                "minecraft:block/sandstone_top",
                "minecraft:block/sandstone_bottom");
        coat = skin;
        edge = skin;
        growth = skin;
        slots = repeated(skin);
      }
      case "reef" -> {
        skin = surface("minecraft:block/prismarine");
        coat = skin;
        edge = skin;
        growth = surface("minecraft:block/tube_coral_block");
        slots = repeated(skin);
        slots[1] = surface("minecraft:block/brain_coral_block");
        slots[2] = surface("minecraft:block/fire_coral_block");
        slots[3] = growth;
      }
      case "snow" -> {
        skin = surface("minecraft:block/packed_ice");
        coat = surface("minecraft:block/snow");
        edge = coat;
        growth = skin;
        slots = repeated(skin);
        slots[1] = coat;
        slots[4] = surface("minecraft:block/ice");
      }
      case "cloud" -> {
        skin = surface("minecraft:block/powder_snow");
        coat = surface("minecraft:block/calcite");
        edge = skin;
        growth = skin;
        slots = repeated(skin);
        slots[1] = coat;
      }
      case "stone" -> {
        skin = surface(stone);
        coat = skin;
        edge = skin;
        growth = skin;
        slots = repeated(skin);
      }
      default -> throw new IllegalArgumentException("Unknown whale variety " + variant);
    }
    edge = belly(variant, edge);
    JsonObject result = new JsonObject();
    result.addProperty("variety", variant);
    result.addProperty("retain_vines", ORGANIC.contains(variant));
    result.add("skin", skin);
    result.add("coat", coat);
    result.add("edge", edge);
    result.add("growth", growth);
    JsonArray materials = new JsonArray();
    for (JsonObject slot : slots) materials.add(slot);
    switch (variant) {
      case "stone" -> {
        materials.add(surface("minecraft:block/andesite"));
        materials.add(surface("minecraft:block/cobblestone"));
      }
      case "moss" -> {
        materials.add(surface("minecraft:block/azalea_leaves"));
        materials.add(surface("minecraft:block/rooted_dirt"));
      }
      case "basalt" -> {
        materials.add(surface("minecraft:block/smooth_basalt"));
        materials.add(surface("minecraft:block/blackstone"));
      }
      case "reef" -> {
        materials.add(surface("minecraft:block/bubble_coral_block"));
        materials.add(surface("minecraft:block/horn_coral_block"));
      }
      case "rooted" -> materials.add(surface("minecraft:block/azalea_leaves"));
      case "fungal" -> {
        materials.add(
            surface(
                "minecraft:block/brown_mushroom_block",
                "minecraft:block/brown_mushroom_block",
                "minecraft:block/mushroom_block_inside"));
        materials.add(
            surface(
                "minecraft:block/red_mushroom_block",
                "minecraft:block/red_mushroom_block",
                "minecraft:block/mushroom_block_inside"));
      }
      case "granite" -> {
        materials.add(surface("minecraft:block/diorite"));
        materials.add(surface("minecraft:block/calcite"));
      }
      case "chalk" -> materials.add(surface("minecraft:block/calcite"));
      case "lava" -> {
        materials.add(surface("minecraft:block/blackstone"));
        materials.add(surface("minecraft:block/smooth_basalt"));
      }
      case "sandstone" -> materials.add(surface("minecraft:block/sandstone_bottom"));
      default -> {
        // These forms use only their original block-material slots.
      }
    }
    result.add("slots", materials);
    return result;
  }

  /**
   * Lighter belly and jaw material that gives each form a mouth line and ventral reading. Every
   * whale material is a block that generates naturally in the pack's world, never a crafted or
   * processed one such as smooth stone, polished stone, wool or concrete powder.
   */
  private static JsonObject belly(String variant, JsonObject current) {
    return switch (variant) {
      case "stone" -> surface("minecraft:block/calcite");
      case "moss" -> surface("minecraft:block/calcite");
      case "rooted" -> surface("minecraft:block/white_terracotta");
      case "fungal" -> surface("minecraft:block/mushroom_stem");
      case "granite" -> surface("minecraft:block/calcite");
      case "chalk" -> surface("minecraft:block/calcite");
      case "dripstone" -> surface("minecraft:block/tuff");
      case "basalt" -> surface("minecraft:block/smooth_basalt");
      case "sandstone" -> surface("minecraft:block/sandstone_top");
      case "reef" -> surface("minecraft:block/sand");
      case "cloud" -> surface("minecraft:block/clay");
      default -> current;
    };
  }

  /** Related material for irregular flank patches, appended as each form's last slot. */
  private static JsonObject patch(String variant) {
    return surface(
        switch (variant) {
          case "stone" -> "minecraft:block/andesite";
          case "moss" -> "minecraft:block/mossy_cobblestone";
          case "rooted" -> "minecraft:block/coarse_dirt";
          case "fungal" -> "minecraft:block/mycelium_top";
          case "granite" -> "minecraft:block/diorite";
          case "chalk" -> "minecraft:block/tuff";
          case "dripstone" -> "minecraft:block/tuff";
          case "basalt" -> "minecraft:block/smooth_basalt";
          case "lava" -> "minecraft:block/blackstone";
          case "sandstone" -> "minecraft:block/sand";
          case "reef" -> "minecraft:block/clay";
          case "snow" -> "minecraft:block/blue_ice";
          case "cloud" -> "minecraft:block/diorite";
          default -> throw new IllegalArgumentException("Unknown whale variety " + variant);
        });
  }

  private static JsonObject[] repeated(JsonObject material) {
    return new JsonObject[] {material, material, material, material, material, material};
  }

  private static JsonObject surface(String texture) {
    return surface(texture, texture, texture);
  }

  private static JsonObject surface(String side, String top, String bottom) {
    JsonObject surface = new JsonObject();
    surface.addProperty("side", side);
    surface.addProperty("top", top);
    surface.addProperty("bottom", bottom);
    return surface;
  }

  private static final class Rig {
    final JsonObject geometry;
    final JsonObject model;
    final Map<String, JsonObject> bones = new LinkedHashMap<>();
    final JsonArray eyes = new JsonArray();
    final String variant;
    int patchSlot;
    int eyeHalfWidth;
    int eyeLeftHalfWidth;
    int eyeRightHalfWidth;
    int eyeY;
    int eyeZ;

    Rig(JsonObject original, String variant) {
      this.variant = variant;
      geometry = original.deepCopy();
      model = geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
      JsonArray definitions = new JsonArray();
      for (var value : model.getAsJsonArray("bones")) {
        JsonObject bone = value.getAsJsonObject();
        if (bone.get("name").getAsString().equals("bone")) continue;
        bone.add("cubes", new JsonArray());
        definitions.add(bone);
        bones.put(bone.get("name").getAsString(), bone);
      }
      model.add("bones", definitions);
      addBone("flipper_tip_left", "pectoral_fin_left", -80, 42, -37);
      addBone("flipper_tip_right", "pectoral_fin_right", 80, 42, -37);
      addBone("flora_left", "body", -27, 110, -22);
      addBone("flora_right", "body", 27, 110, -22);
      addBone("eyelid_left", "head", -40, 70, -190);
      addBone("eyelid_right", "head", 40, 70, -190);
    }

    void addBone(String name, String parent, int x, int y, int z) {
      JsonObject bone = new JsonObject();
      bone.addProperty("name", name);
      bone.addProperty("parent", parent);
      bone.add("pivot", numbers(x, y, z));
      bone.add("cubes", new JsonArray());
      bones.put(name, bone);
      model.getAsJsonArray("bones").add(bone);
    }

    JsonArray cubes(String bone) {
      return bones.get(bone).getAsJsonArray("cubes");
    }

    void eyes() {
      for (int side : new int[] {-1, 1}) {
        int halfWidth = side < 0 ? eyeLeftHalfWidth : eyeRightHalfWidth;
        if (halfWidth == 0) halfWidth = eyeHalfWidth;
        int x = side < 0 ? -halfWidth - 1 : halfWidth;
        solid(this, "head", x, eyeY, eyeZ, 1, 10, 16, "skin");
        int index = cubes("head").size() - 1;
        JsonObject anchor = new JsonObject();
        anchor.addProperty("bone", "head");
        anchor.addProperty("cube", index);
        anchor.addProperty("face", side < 0 ? "east" : "west");
        anchor.addProperty("u", 4);
        anchor.addProperty("v", 2);
        anchor.addProperty("mirror", side > 0);
        eyes.add(anchor);
        String name = side < 0 ? "eyelid_left" : "eyelid_right";
        bones.get(name).add("pivot", numbers(x, eyeY + 8, eyeZ + 8));
        solid(this, name, x + (side < 0 ? -1 : 1), eyeY + 2, eyeZ + 4, 1, 6, 8, "skin");
      }
    }
  }

  private static void anatomy(Rig rig, String variant) {
    switch (variant) {
      case "stone" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-238, 22, 25, 44, 83},
              {-216, 40, 39, 33, 101},
              {-176, 48, 47, 30, 113},
              {-128, 61, 48, 32, 116}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 49, 51, 17, 121}, {-20, 47, 47, 12, 115}, {27, 53, 36, 23, 101}});
        jaw(
            rig,
            new int[][] {{-224, 37, 24, 22, 34}, {-187, 61, 35, 9, 33}, {-126, 67, 36, 8, 33}});
        fins(rig, 60, 28, 11, 9);
        tail(rig, 25, 56, 14, 9);
        rig.eyeHalfWidth = 39;
        rig.eyeY = 57;
        rig.eyeZ = -208;
      }
      case "fungal" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-235, 27, 22, 39, 75},
              {-208, 43, 38, 22, 97},
              {-165, 53, 43, 13, 111},
              {-112, 45, 39, 29, 118}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 42, 40, 28, 119}, {-27, 49, 34, 29, 109}, {22, 58, 25, 35, 90}});
        jaw(
            rig,
            new int[][] {{-219, 45, 21, 21, 33}, {-174, 58, 29, 5, 32}, {-116, 57, 30, 15, 33}});
        fungalFins(rig);
        tail(rig, 18, 64, 11, -13);
        rig.eyeHalfWidth = 38;
        rig.eyeY = 49;
        rig.eyeZ = -198;
      }
      case "snow" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-237, 30, 29, 47, 89},
              {-207, 43, 45, 32, 115},
              {-164, 50, 52, 25, 135},
              {-114, 47, 49, 30, 141}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 47, 53, 13, 142}, {-22, 45, 46, 13, 119}, {23, 57, 32, 28, 100}});
        jaw(
            rig,
            new int[][] {{-220, 43, 25, 25, 35}, {-177, 58, 36, 11, 34}, {-119, 60, 35, 13, 34}});
        snowFins(rig);
        tail(rig, 22, 68, 16, -4);
        snowFlukes(rig);
        rig.eyeHalfWidth = 45;
        rig.eyeY = 62;
        rig.eyeZ = -197;
      }
      case "moss" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-239, 32, 31, 37, 73},
              {-207, 45, 49, 27, 89},
              {-162, 51, 56, 25, 101},
              {-111, 44, 55, 28, 105}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 48, 58, 22, 106}, {-21, 48, 51, 22, 101}, {27, 53, 36, 30, 89}});
        jaw(
            rig,
            new int[][] {{-224, 46, 29, 24, 34}, {-178, 59, 41, 15, 34}, {-119, 60, 39, 18, 34}});
        fins(rig, 42, 39, 10, 2);
        tail(rig, 24, 57, 11, -2);
        rig.eyeHalfWidth = 49;
        rig.eyeY = 49;
        rig.eyeZ = -197;
      }
      case "rooted" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-242, 30, 24, 42, 81},
              {-212, 47, 40, 29, 107},
              {-165, 51, 45, 22, 124},
              {-114, 47, 48, 26, 131}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 48, 51, 17, 133}, {-21, 47, 45, 17, 116}, {26, 54, 31, 27, 98}});
        jaw(
            rig,
            new int[][] {{-226, 44, 22, 24, 35}, {-182, 62, 33, 11, 34}, {-120, 61, 34, 14, 34}});
        fins(rig, 55, 31, 10, 13);
        tail(rig, 22, 66, 13, 7);
        rig.eyeHalfWidth = 40;
        rig.eyeY = 55;
        rig.eyeZ = -202;
      }
      case "granite" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-221, 26, 31, 45, 99},
              {-195, 37, 46, 29, 123},
              {-158, 48, 56, 22, 135},
              {-110, 43, 56, 25, 138}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 51, 57, 7, 138}, {-18, 48, 49, 12, 121}, {30, 50, 33, 29, 100}});
        jaw(
            rig,
            new int[][] {{-209, 38, 28, 28, 36}, {-171, 53, 42, 9, 35}, {-118, 59, 42, 11, 35}});
        fins(rig, 53, 36, 16, 0);
        tail(rig, 28, 61, 19, -2);
        rig.eyeHalfWidth = 46;
        rig.eyeY = 62;
        rig.eyeZ = -184;
      }
      case "chalk" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-243, 29, 19, 49, 93},
              {-214, 48, 29, 32, 123},
              {-166, 53, 35, 24, 139},
              {-113, 46, 36, 29, 143}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 43, 38, 14, 143}, {-26, 47, 33, 18, 123}, {21, 59, 25, 32, 103}});
        jaw(
            rig,
            new int[][] {{-227, 46, 18, 25, 35}, {-181, 61, 26, 11, 34}, {-120, 61, 27, 17, 34}});
        fins(rig, 47, 28, 10, -13);
        tail(rig, 17, 52, 12, -8);
        rig.eyeHalfWidth = 29;
        rig.eyeY = 61;
        rig.eyeZ = -203;
      }
      case "dripstone" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-258, 34, 16, 51, 78},
              {-224, 50, 25, 38, 94},
              {-174, 57, 32, 31, 107},
              {-117, 50, 33, 35, 111}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 48, 34, 24, 113}, {-21, 48, 29, 29, 104}, {27, 53, 20, 37, 90}});
        jaw(
            rig,
            new int[][] {{-241, 49, 16, 28, 36}, {-192, 65, 23, 15, 35}, {-127, 68, 24, 20, 35}});
        fins(rig, 44, 25, 8, 14);
        tail(rig, 16, 49, 10, 10);
        rig.eyeHalfWidth = 25;
        rig.eyeY = 56;
        rig.eyeZ = -213;
      }
      case "basalt" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-224, 25, 25, 47, 96},
              {-199, 40, 37, 28, 123},
              {-159, 49, 44, 18, 140},
              {-110, 43, 48, 23, 146}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 45, 51, 6, 149}, {-24, 49, 43, 12, 128}, {25, 55, 28, 29, 102}});
        jaw(
            rig,
            new int[][] {{-212, 42, 24, 26, 36}, {-170, 55, 34, 6, 35}, {-115, 56, 33, 10, 35}});
        fins(rig, 59, 23, 15, 17);
        tail(rig, 24, 55, 16, 4);
        rig.eyeHalfWidth = 37;
        rig.eyeY = 57;
        rig.eyeZ = -198;
      }
      case "lava" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-233, 29, 31, 46, 85},
              {-204, 42, 48, 31, 101},
              {-162, 51, 57, 25, 111},
              {-111, 44, 57, 29, 113}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 50, 59, 15, 117}, {-19, 46, 52, 16, 106}, {27, 53, 36, 28, 94}});
        jaw(
            rig,
            new int[][] {{-218, 45, 28, 26, 36}, {-173, 57, 42, 11, 35}, {-116, 57, 42, 14, 35}});
        fins(rig, 52, 37, 16, 10);
        tail(rig, 29, 64, 19, 4);
        rig.eyeHalfWidth = 48;
        rig.eyeY = 55;
        rig.eyeZ = -194;
      }
      case "sandstone" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-246, 31, 31, 44, 69},
              {-215, 49, 51, 31, 82},
              {-166, 54, 63, 27, 93},
              {-112, 45, 62, 29, 97}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 49, 63, 20, 98}, {-20, 48, 55, 22, 93}, {28, 52, 37, 32, 85}});
        jaw(
            rig,
            new int[][] {{-230, 50, 29, 26, 35}, {-180, 61, 43, 16, 34}, {-119, 60, 44, 19, 34}});
        fins(rig, 58, 41, 9, 17);
        tail(rig, 25, 74, 10, 13);
        rig.eyeHalfWidth = 51;
        rig.eyeY = 47;
        rig.eyeZ = -205;
      }
      case "reef" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-249, 34, 16, 50, 77},
              {-215, 48, 27, 35, 95},
              {-167, 54, 35, 28, 112},
              {-113, 46, 36, 33, 121}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 45, 39, 23, 124}, {-24, 48, 32, 29, 110}, {24, 56, 21, 37, 92}});
        jaw(
            rig,
            new int[][] {{-231, 48, 16, 28, 36}, {-183, 62, 25, 16, 35}, {-121, 62, 26, 21, 35}});
        fins(rig, 115, 26, 8, 24);
        tail(rig, 15, 65, 10, 9);
        rig.eyeHalfWidth = 27;
        rig.eyeY = 56;
        rig.eyeZ = -205;
      }
      case "cloud" -> {
        sections(
            rig,
            "head",
            new int[][] {
              {-255, 35, 17, 56, 83},
              {-220, 52, 29, 45, 104},
              {-168, 55, 37, 39, 116},
              {-113, 46, 36, 43, 121}
            });
        sections(
            rig,
            "body",
            new int[][] {{-69, 47, 38, 33, 123}, {-22, 48, 31, 40, 111}, {26, 54, 22, 45, 97}});
        jaw(
            rig,
            new int[][] {{-236, 51, 16, 34, 41}, {-185, 64, 25, 25, 41}, {-121, 62, 25, 30, 41}});
        fins(rig, 79, 20, 7, 25);
        tail(rig, 15, 75, 9, 16);
        rig.eyeHalfWidth = 29;
        rig.eyeY = 62;
        rig.eyeZ = -210;
      }
      default -> throw new IllegalArgumentException("Unknown whale " + variant);
    }
  }

  /** Creature reading shared by every form: throat grooves and flank breakup. */
  private static void creature(Rig rig) {
    JsonArray headSupport = rig.cubes("head").deepCopy();
    JsonArray bodySupport = rig.cubes("body").deepCopy();
    for (var value : rig.cubes("lower_jaw").deepCopy()) {
      JsonObject cube = value.getAsJsonObject();
      int[] origin = integers(cube.getAsJsonArray("origin"));
      int[] size = integers(cube.getAsJsonArray("size"));
      if (size[1] > 6 || size[2] <= 8) continue;
      for (int x = origin[0] + 3; x + 3 <= origin[0] + size[0] - 3; x += 9)
        roleInlay(rig, "lower_jaw", x, origin[1], origin[2] + 3, 3, 2, size[2] - 6, "skin");
    }
    // Each form gets its own irregular scatter, so patches never repeat between variants.
    java.util.Random random = new java.util.Random(rig.variant.hashCode() * 31L + 7);
    int count = 3 + random.nextInt(4);
    int[][] patches = new int[count][];
    for (int index = 0; index < count; index++)
      patches[index] =
          new int[] {
            -200 + random.nextInt(230),
            50 + random.nextInt(50),
            14 + random.nextInt(30),
            6 + random.nextInt(14)
          };
    for (int side : new int[] {-1, 1})
      for (int index = 0; index < patches.length; index++) {
        if (random.nextInt(4) == 0) continue;
        int[] patch = patches[(index + (side > 0 ? 1 : 0)) % patches.length];
        String bone = covers(headSupport, 0, patch[0] + patch[2] / 2) ? "head" : "body";
        JsonArray support = bone.equals("head") ? headSupport : bodySupport;
        // A patch stays within one moving part so it never cuts through its neighbour.
        if (!covers(support, 0, patch[0]) || !covers(support, 0, patch[0] + patch[2] - 1)) continue;
        int half = halfWidth(support, patch[1], patch[0] + patch[2] / 2);
        if (half > 4)
          inlay(
              rig,
              bone,
              side < 0 ? -half : half - 2,
              patch[1],
              patch[0],
              2,
              patch[3],
              patch[2],
              rig.patchSlot);
      }
  }

  private static boolean covers(JsonArray anatomy, int x, int z) {
    for (var value : anatomy) {
      int[] origin = integers(value.getAsJsonObject().getAsJsonArray("origin"));
      int[] size = integers(value.getAsJsonObject().getAsJsonArray("size"));
      if (x >= origin[0] && x < origin[0] + size[0] && z >= origin[2] && z < origin[2] + size[2])
        return true;
    }
    return false;
  }

  private static int halfWidth(JsonArray anatomy, int y, int z) {
    int widest = 0;
    for (var value : anatomy) {
      JsonObject cube = value.getAsJsonObject();
      int[] origin = integers(cube.getAsJsonArray("origin"));
      int[] size = integers(cube.getAsJsonArray("size"));
      if (y >= origin[1] && y < origin[1] + size[1] && z >= origin[2] && z < origin[2] + size[2])
        widest = Math.max(widest, Math.max(-origin[0], origin[0] + size[0]));
    }
    return widest;
  }

  private static JsonObject roleInlay(
      Rig rig, String bone, int x, int y, int z, int width, int height, int depth, String role) {
    carve(rig, bone, x, y, z, x + width, y + height, z + depth);
    return solid(rig, bone, x, y, z, width, height, depth, role);
  }

  private static void sections(Rig rig, String bone, int[][] sections) {
    for (int[] section : sections)
      roundedSection(rig, bone, section[0], section[1], section[2], section[3], section[4]);
  }

  private static void roundedSection(
      Rig rig, String bone, int z, int length, int radius, int bottom, int top) {
    int height = top - bottom;
    int[] levels = {
      bottom,
      bottom + height / 8,
      bottom + height / 3,
      bottom + height * 3 / 4,
      top - height / 10,
      top
    };
    int[] widths = {radius * 3 / 5, radius * 4 / 5, radius, radius * 4 / 5, radius * 3 / 5};
    for (int index = 0; index < widths.length; index++) {
      // The lower two levels form a lighter belly; lava keeps its armor with a glowing seam.
      String role = index == 4 ? "coat" : index <= 1 ? "edge" : "skin";
      if (rig.variant.equals("lava")) role = index == 0 ? "skin" : "coat";
      solid(
          rig,
          bone,
          -widths[index],
          levels[index],
          z,
          widths[index] * 2,
          levels[index + 1] - levels[index],
          length,
          role);
    }
  }

  private static void jaw(Rig rig, int[][] sections) {
    for (int[] section : sections) {
      int radius = section[2];
      int bottom = section[3];
      int top = section[4];
      solid(
          rig,
          "lower_jaw",
          -radius,
          bottom + 5,
          section[0],
          radius * 2,
          top - bottom - 5,
          section[1],
          "edge");
      solid(
          rig,
          "lower_jaw",
          -radius + 7,
          bottom,
          section[0] + 2,
          radius * 2 - 14,
          5,
          section[1] - 2,
          "skin");
    }
  }

  private static void fins(Rig rig, int reach, int depth, int thickness, int sweep) {
    // Pectoral fins large enough to read from below at encounter distance.
    reach = Math.max(reach, 80);
    depth = Math.max(depth, 36);
    thickness += 2;
    for (int side : new int[] {-1, 1}) {
      String bone = side < 0 ? "pectoral_fin_left" : "pectoral_fin_right";
      String tip = side < 0 ? "flipper_tip_left" : "flipper_tip_right";
      int inner = 38;
      int middle = inner + reach * 2 / 3;
      int x = side < 0 ? -middle : inner;
      solid(rig, bone, x, 39, -53, middle - inner, thickness, depth, "skin");
      int outer = inner + reach;
      solid(
          rig,
          tip,
          side < 0 ? -outer : middle - 1,
          39,
          -53 + sweep,
          outer - middle + 1,
          Math.max(4, thickness - 2),
          Math.max(13, depth - 9),
          "coat");
      rig.bones.get(tip).add("pivot", numbers(side * middle, 42, -45 + sweep));
    }
  }

  private static void tail(Rig rig, int stalkRadius, int flukeRadius, int thickness, int sweep) {
    // Broad, thick flukes instead of a thin rod.
    flukeRadius = Math.max(flukeRadius, 80);
    thickness += 4;
    solid(rig, "tail", -stalkRadius, 32, 78, stalkRadius * 2, 49, 34, "skin");
    solid(rig, "tail", -stalkRadius + 6, 37, 111, (stalkRadius - 6) * 2, 35, 27, "skin");
    for (int side : new int[] {-1, 1}) {
      int width = flukeRadius - 6;
      JsonObject cube =
          solid(
              rig,
              "tail_flukes",
              side < 0 ? -flukeRadius : 6,
              43,
              135 + sweep,
              width,
              thickness,
              31,
              "coat");
      cube.add("pivot", numbers(side * 6, 50, 139));
      cube.add("rotation", numbers(0, side * -12, 0));
      solid(
          rig,
          "tail_flukes",
          side < 0 ? -flukeRadius + 7 : 12,
          46,
          164 + sweep,
          width - 13,
          Math.max(5, thickness - 6),
          15,
          "skin");
    }
    solid(rig, "tail_flukes", -7, 44, 135, 14, thickness - 1, 17, "skin");
  }

  private static void fungalFins(Rig rig) {
    for (int side : new int[] {-1, 1}) {
      String bone = side < 0 ? "pectoral_fin_left" : "pectoral_fin_right";
      String tip = side < 0 ? "flipper_tip_left" : "flipper_tip_right";
      finSegment(rig, bone, side, 38, 60, 38, 11, -53, 28);
      finSegment(rig, bone, side, 59, 77, 35, 12, -46, 23);
      finSegment(rig, tip, side, 76, 93, 32, 12, -38, 18);
      finSegment(rig, tip, side, 92, 101, 30, 9, -29, 12);
      rig.bones.get(tip).add("pivot", numbers(side * 76, 31, -37));
    }
  }

  private static void snowFins(Rig rig) {
    for (int side : new int[] {-1, 1}) {
      String bone = side < 0 ? "pectoral_fin_left" : "pectoral_fin_right";
      String tip = side < 0 ? "flipper_tip_left" : "flipper_tip_right";
      finSegment(rig, bone, side, 38, 69, 38, 13, -56, 45);
      finSegment(rig, bone, side, 68, 96, 34, 13, -50, 38);
      finSegment(rig, tip, side, 95, 119, 33, 11, -43, 25);
      finSegment(rig, tip, side, 118, 129, 35, 7, -37, 14);
      rig.bones.get(tip).add("pivot", numbers(side * 95, 39, -37));
    }
  }

  private static void finSegment(
      Rig rig, String bone, int side, int inner, int outer, int y, int height, int z, int depth) {
    solid(rig, bone, side < 0 ? -outer : inner, y, z, outer - inner, height, depth, "skin");
  }

  private static void snowFlukes(Rig rig) {
    rig.cubes("tail_flukes").asList().clear();
    solid(rig, "tail_flukes", -10, 43, 135, 20, 13, 20, "skin");
    for (int side : new int[] {-1, 1}) {
      solid(rig, "tail_flukes", side < 0 ? -67 : 9, 43, 143, 58, 13, 23, "coat");
      solid(rig, "tail_flukes", side < 0 ? -77 : 26, 45, 164, 51, 10, 17, "skin");
      solid(rig, "tail_flukes", side < 0 ? -67 : 36, 46, 180, 31, 7, 10, "coat");
    }
  }

  private static void sideInlay(
      Rig rig, String bone, int side, int radius, int y, int z, int height, int depth, int slot) {
    inlay(rig, bone, side < 0 ? -radius : radius - 2, y, z, 2, height, depth, slot);
  }

  private static void ecology(Rig rig, String variant) {
    switch (variant) {
      case "stone" -> stone(rig);
      case "fungal" -> fungal(rig);
      case "snow" -> snow(rig);
      case "moss" -> moss(rig);
      case "rooted" -> rooted(rig);
      case "granite" -> granite(rig);
      case "chalk" -> chalk(rig);
      case "dripstone" -> dripstone(rig);
      case "basalt" -> basalt(rig);
      case "lava" -> lava(rig);
      case "sandstone" -> sandstone(rig);
      case "reef" -> reef(rig);
      case "cloud" -> cloud(rig);
      default -> throw new IllegalArgumentException("Unknown whale " + variant);
    }
  }

  private static void stone(Rig rig) {
    carve(rig, "head", -60, 54, -158, -23, 78, -117);
    carve(rig, "head", -60, 70, -151, -26, 94, -125);
    carve(rig, "head", -60, 89, -143, -30, 101, -131);
    block(rig, "head", -25, 56, -155, 3, 28, 33, 11);
    block(rig, "head", -29, 81, -147, 4, 10, 18, 6);
    block(rig, "head", -34, 54, -155, 12, 5, 32, 7);
    rock(rig, "head", -17, 111, -161, 33, 34, 37, 6);
    rock(rig, "head", 11, 106, -113, 32, 27, 33, 7);
    rock(rig, "body", -18, 116, -46, 43, 42, 39, 6);
    rock(rig, "body", 19, 103, 14, 25, 30, 37, 7);
    for (int side : new int[] {-1, 1}) {
      sideInlay(rig, "head", side, 39, 56, -210, 15, 22, 8);
      sideInlay(rig, "head", side, 39, 65, -192, 14, 15, 8);
      sideInlay(rig, "head", side, 47, 69, -176, 17, 22, 8);
      sideInlay(rig, "head", side, 47, 79, -157, 10, 15, 8);
      sideInlay(rig, "head", side, 48, 61, -111, 18, 25, 9);
      sideInlay(rig, "head", side, 48, 72, -89, 15, 21, 9);
      sideInlay(rig, "body", side, 51, 72, -68, 17, 23, 9);
      sideInlay(rig, "body", side, 51, 59, -48, 18, 28, 10);
      sideInlay(rig, "body", side, 47, 48, -20, 18, 23, 10);
      sideInlay(rig, "body", side, 47, 47, 0, 11, 21, 10);
      sideInlay(rig, "head", side, 38, 45, -105, 12, 22, 9);
      sideInlay(rig, "body", side, 40, 30, -57, 19, 19, 10);
    }
    sprite(rig, "flora_left", -34, 109, -98, 16, 16, 12, 90);
    sprite(rig, "flora_right", 31, 111, -23, 16, 16, 12, 90);
  }

  private static void fungal(Rig rig) {
    mushroom(rig, "head", -9, 108, -143, 76, 65, 54, 7);
    mushroom(rig, "body", 12, 114, -42, 55, 82, 47, 6);
    mushroom(rig, "head", 19, 96, -191, 42, 48, 37, 7);
    mushroom(rig, "body", -16, 101, 14, 36, 53, 31, 6);
    mushroom(rig, "head", -29, 94, -108, 28, 36, 27, 6);
    shelf(rig, "head", -39, 75, -160, 30, 27, 7, -1);
    shelf(rig, "head", 37, 66, -116, 28, 31, 6, 1);
    shelf(rig, "body", -34, 60, -38, 38, 31, 6, -1);
    shelf(rig, "body", 27, 55, 34, 26, 23, 7, 1);
    mushroom(rig, "flora_left", -29, 115, -62, 16, 24, 15, 7);
    mushroom(rig, "flora_right", 28, 110, -9, 20, 28, 17, 6);
  }

  private static void snow(Rig rig) {
    rock(rig, "head", -12, 128, -139, 68, 32, 61, 1);
    rock(rig, "head", 17, 112, -193, 38, 21, 41, 1);
    rock(rig, "body", -6, 132, -51, 57, 27, 45, 1);
    rock(rig, "head", -35, 130, -103, 27, 68, 31, 6);
    rock(rig, "head", 34, 140, -100, 19, 50, 25, 6);
    block(rig, "head", -36, 187, -117, 37, 18, 29, 6);
    block(rig, "head", -6, 182, -112, 35, 17, 23, 6);
    block(rig, "head", -23, 179, -115, 12, 11, 26, 6);
    block(rig, "head", 20, 169, -111, 12, 17, 22, 6);
    rock(rig, "head", -12, 201, -104, 43, 15, 28, 1);
    rock(rig, "head", 24, 189, -98, 23, 12, 21, 1);
    crystal(rig, "body", 26, 114, -36, 15, 43, 6, 12);
    crystal(rig, "body", -29, 111, -16, 11, 29, 6, -14);
    conifer(rig, "body", -13, 116, 6, 60, 7, 8);
    conifer(rig, "head", 16, 111, -169, 39, 7, 8);
    for (int index = 0; index < 4; index++)
      stalactite(
          rig,
          "body",
          -24 + index * 16,
          16,
          -29 + index * 10,
          6 + index % 2 * 3,
          25 + index * 6,
          0);
  }

  private static void moss(Rig rig) {
    rock(rig, "head", -16, 97, -151, 54, 18, 55, 1);
    rock(rig, "head", 23, 88, -193, 39, 13, 37, 1);
    rock(rig, "body", -21, 99, -44, 51, 17, 47, 1);
    rock(rig, "body", 20, 91, 15, 39, 16, 39, 1);
    carve(rig, "head", -18, 100, -145, 11, 119, -119);
    block(rig, "head", -18, 100, -145, 29, 1, 26, 9);
    carve(rig, "body", 8, 99, -56, 32, 116, -36);
    block(rig, "body", 8, 99, -56, 24, 1, 20, 9);
    for (int index = 0; index < 7; index++) {
      flower(
          rig,
          index % 2 == 0 ? "head" : "body",
          -34 + index * 11,
          index % 2 == 0 ? 106 : 111,
          index % 2 == 0 ? -174 + index * 7 : -55 + index * 6,
          index % 2 == 0 ? 8 : 10);
    }
    sprite(rig, "flora_left", -41, 104, -29, 17, 23, 11, 45);
    sprite(rig, "flora_left", -41, 104, -29, 17, 23, 11, 135);
    sprite(rig, "flora_right", 37, 103, -41, 15, 20, 11, 45);
    sprite(rig, "flora_right", 37, 103, -41, 15, 20, 11, 135);
  }

  private static void rooted(Rig rig) {
    int firstGround = groundHeight(rig, -17, -140, 200);
    int secondGround = groundHeight(rig, 18, -40, 200);
    int saplingGround = groundHeight(rig, 12, -192, 200);
    int secondFlowerGround = groundHeight(rig, -30, -117, 200);
    tree(rig, "head", -17, firstGround, -140, 64, 6);
    tree(rig, "body", 18, secondGround, -40, 75, 12);
    tree(rig, "head", 12, saplingGround, -192, 42, 6);
    rootedBranch(rig, "head", -17, firstGround + 1, -140, -32, firstGround - 4, -124, 6);
    rootedBranch(rig, "head", -17, firstGround + 1, -140, 0, firstGround - 2, -131, 5);
    rootedBranch(rig, "body", 18, secondGround + 1, -40, 35, secondGround - 9, -29, 6);
    rootedBranch(rig, "body", 18, secondGround + 1, -40, 1, secondGround - 2, -24, 5);
    rootedBranch(rig, "head", 12, saplingGround + 1, -192, 24, saplingGround - 3, -181, 4);
    inlay(rig, "head", -4, 122, -164, 24, 6, 24, 1);
    hollowLog(rig, "head", 8, 128, -160);
    flower(rig, "head", -28, firstGround, -160, 8);
    flower(rig, "head", -30, secondFlowerGround, -117, 9);
    flower(rig, "head", -3, saplingGround, -198, 9);
    flower(rig, "body", -18, secondGround, -56, 8);
  }

  private static void granite(Rig rig) {
    carve(rig, "head", -51, 106, -145, 45, 142, -58);
    rock(rig, "head", -3, 106, -102, 96, 72, 87, 0);
    carve(rig, "body", -32, 123, -81, 47, 145, -16);
    rock(rig, "body", 7, 123, -49, 79, 45, 65, 0);
    carve(rig, "head", -67, 77, -133, -21, 145, -77);
    carve(rig, "head", 20, 98, -122, 66, 160, -81);
    carve(rig, "head", -23, 82, -126, 24, 180, -82);
    carve(rig, "body", -18, 121, -66, 17, 183, -44);
    inlay(rig, "body", -22, 121, -66, 4, 26, 22, 8);
    inlay(rig, "body", 17, 121, -66, 4, 26, 22, 8);
    inlay(rig, "body", -18, 117, -66, 35, 4, 22, 8);
    inlay(rig, "head", -46, 76, -129, 89, 9, 50, 7);
    inlay(rig, "head", -38, 85, -123, 72, 4, 39, 8);
    inlay(rig, "head", -45, 86, -133, 79, 43, 9, 7);
    inlay(rig, "head", -40, 89, -82, 74, 38, 8, 7);
    inlay(rig, "head", -30, 129, -130, 53, 12, 8, 7);
    inlay(rig, "head", -24, 127, -87, 42, 11, 8, 7);
    crystal(rig, "head", -30, 88, -114, 16, 38, 8, -31);
    crystal(rig, "head", -18, 88, -94, 13, 33, 8, 24);
    crystal(rig, "head", 4, 89, -110, 18, 48, 8, 13);
    crystal(rig, "head", 24, 90, -100, 15, 34, 8, 39);
    crystal(rig, "head", 17, 106, -124, 12, 27, 8, -53);
    crystal(rig, "body", -7, 121, -52, 15, 39, 8, -15);
    // Amethyst lining on the inner walls so the open geode reads purple from above.
    inlay(rig, "head", -27, 92, -126, 4, 34, 44, 8);
    inlay(rig, "head", 24, 92, -126, 4, 34, 44, 8);
    inlay(rig, "head", -23, 92, -130, 47, 34, 4, 8);
    inlay(rig, "head", -23, 92, -82, 47, 34, 4, 8);
    crystal(rig, "head", -12, 90, -118, 12, 30, 8, -20);
    crystal(rig, "head", 12, 90, -90, 12, 28, 8, 28);
  }

  private static void chalk(Rig rig) {
    carve(rig, "head", -43, 61, -153, 43, 91, -114);
    carve(rig, "head", -40, 81, -146, 40, 106, -121);
    carve(rig, "head", -39, 56, -147, 39, 69, -125);
    inlay(rig, "body", -29, 130, -57, 53, 48, 45, 0);
    inlay(rig, "body", -20, 174, -49, 35, 18, 31, 0);
    carve(rig, "body", -40, 139, -49, 40, 157, -23);
    carve(rig, "body", -40, 154, -43, 40, 165, -28);
    carve(rig, "body", -32, 170, -58, -9, 195, -44);
    carve(rig, "body", 15, 160, -30, 36, 191, -9);
    carve(rig, "head", -39, 98, -164, -30, 143, -157);
    carve(rig, "head", -39, 115, -158, -27, 145, -151);
    carve(rig, "head", 28, 87, -111, 39, 128, -103);
    carve(rig, "body", -41, 90, -62, -30, 144, -55);
    carve(rig, "body", -41, 119, -54, -26, 146, -46);
    inlay(rig, "head", -36, 69, -108, 2, 7, 18, 7);
    inlay(rig, "head", -36, 72, -93, 2, 9, 8, 7);
    inlay(rig, "head", -36, 78, -89, 2, 6, 12, 7);
    inlay(rig, "body", 31, 52, 1, 2, 5, 13, 7);
    inlay(rig, "head", -23, 133, -156, 31, 8, 18, 6);
    inlay(rig, "body", 8, 135, -48, 22, 6, 19, 6);
    inlay(rig, "head", 12, 139, -120, 14, 3, 12, 6);
    inlay(rig, "head", 21, 140, -113, 7, 2, 8, 6);
    inlay(rig, "body", -17, 142, -59, 12, 3, 9, 6);
    inlay(rig, "head", -22, 141, -105, 25, 3, 25, 8);
    inlay(rig, "body", -12, 121, 0, 20, 3, 18, 8);
    flower(rig, "head", -17, 144, -100, 9);
    flower(rig, "head", -5, 144, -89, 9);
    flower(rig, "body", -6, 124, 9, 9);
  }

  private static void dripstone(Rig rig) {
    carve(rig, "head", -40, 58, -164, -13, 87, -123);
    carve(rig, "head", 12, 62, -123, 40, 91, -84);
    carve(rig, "body", -41, 52, -47, -14, 87, -9);
    JsonArray bodySupport = rig.cubes("body").deepCopy();
    for (int index = 0; index < 11; index++) {
      String bone = index < 6 ? "lower_jaw" : "body";
      int z = index < 6 ? -205 + index * 23 : -60 + (index - 6) * 24;
      int width = 12 + index % 3 * 5;
      int height = 37 + index % 4 * 15;
      int x = index % 2 == 0 ? -17 : 16;
      int ceiling = index < 6 ? 32 : Math.max(26, bellyHeight(bodySupport, x, z));
      dripstoneFormation(rig, bone, x, ceiling, z, width, height);
      if (index % 3 == 0)
        dripstoneFormation(
            rig,
            bone,
            index % 2 == 0 ? 9 : -8,
            index < 6
                ? 29
                : Math.max(26, bellyHeight(bodySupport, index % 2 == 0 ? 9 : -8, z + 10)),
            z + 10,
            10,
            height - 15);
    }
    rock(rig, "head", -10, 104, -147, 37, 42, 37, 0);
    carve(rig, "body", -6, 106, -52, 25, 124, -21);
    rock(rig, "body", 9, 106, -37, 31, 37, 31, 0);
  }

  private static void dripstoneFormation(
      Rig rig, String bone, int x, int ceiling, int z, int width, int height) {
    carve(
        rig,
        bone,
        x - width / 2,
        ceiling - height / 2,
        z - width / 2,
        x - width / 2 + width,
        ceiling + 2,
        z - width / 2 + width);
    stalactite(rig, bone, x, ceiling, z, width, height, 0).addProperty("_attachment", "stalactite");
  }

  private static int bellyHeight(JsonArray anatomy, int x, int z) {
    int lowest = Integer.MAX_VALUE;
    for (var value : anatomy) {
      JsonObject cube = value.getAsJsonObject();
      int[] origin = integers(cube.getAsJsonArray("origin"));
      int[] size = integers(cube.getAsJsonArray("size"));
      if (x >= origin[0] && x < origin[0] + size[0] && z >= origin[2] && z < origin[2] + size[2])
        lowest = Math.min(lowest, origin[1]);
    }
    if (lowest == Integer.MAX_VALUE) throw new IllegalArgumentException("Missing belly support");
    return lowest;
  }

  private static void basalt(Rig rig) {
    for (int group = 0; group < 2; group++) {
      String bone = group == 0 ? "head" : "body";
      int centerZ = group == 0 ? -139 : -36;
      int floor = group == 0 ? 133 : 143;
      for (int row = 0; row < 3; row++)
        for (int column = 0; column < 3; column++) {
          int width = 18 + (row + column) % 2 * 2;
          int height = 25 + ((row * 7 + column * 11 + group * 5) % 5) * 9;
          basaltColumn(
              rig, bone, -18 + column * 18, floor - 5, centerZ - 18 + row * 18, width, height);
        }
    }
    for (int side : new int[] {-1, 1}) {
      for (int index = 0; index < 7; index++) {
        String bone = index < 4 ? "head" : "body";
        int z = index < 4 ? -182 + index * 29 : -57 + (index - 4) * 29;
        int radius = index < 4 ? 42 : 48;
        int top = 108 + (index % 3) * 9;
        int bottom = 28 - (index % 4) * 15;
        block(rig, bone, side * radius - 9, bottom, z, 18, top - bottom, 20, 0);
        block(rig, bone, side * radius - 6, bottom - 8, z + 3, 12, 12, 14, 6);
      }
    }
    for (int index = 0; index < 6; index++) {
      int bottom = 2 - (index % 3) * 7;
      block(rig, "body", -31 + index * 11, bottom, -49 + (index % 2) * 22, 14, 58 - bottom, 17, 0);
      block(rig, "body", -29 + index * 11, bottom - 8, -47 + (index % 2) * 22, 10, 12, 13, 6);
    }
  }

  private static void lava(Rig rig) {
    for (int side : new int[] {-1, 1}) {
      sideInlay(rig, "head", side, 48, 58, -201, 10, 27, 1);
      sideInlay(rig, "head", side, 48, 65, -177, 15, 14, 1);
      sideInlay(rig, "head", side, 57, 67, -162, 10, 33, 1);
      sideInlay(rig, "head", side, 57, 57, -132, 14, 20, 1);
      sideInlay(rig, "head", side, 57, 61, -111, 10, 43, 1);
      sideInlay(rig, "body", side, 59, 56, -68, 13, 28, 1);
      sideInlay(rig, "body", side, 59, 67, -43, 10, 23, 1);
      sideInlay(rig, "body", side, 52, 56, -19, 9, 32, 1);
    }
    // Lava seams on the belly, visible to players below.
    JsonArray lavaHead = rig.cubes("head").deepCopy();
    JsonArray lavaBody = rig.cubes("body").deepCopy();
    int[][] seams = {{-190, -8}, {-160, 10}, {-128, -12}, {-96, 6}, {-60, -6}, {-30, 12}, {0, -4}};
    for (int[] seam : seams) {
      JsonArray support = covers(lavaHead, seam[1], seam[0]) ? lavaHead : lavaBody;
      if (!covers(support, seam[1], seam[0])) continue;
      String bone = support == lavaHead ? "head" : "body";
      inlay(rig, bone, seam[1] - 3, bellyHeight(support, seam[1], seam[0]), seam[0], 6, 2, 24, 8);
    }
    volcano(rig, "head", -4, 107, -139);
    inlay(rig, "head", -3, 110, -104, 8, 3, 36, 8);
    inlay(rig, "body", 1, 114, -67, 8, 3, 29, 8);
    inlay(rig, "body", -4, 114, -40, 9, 3, 22, 8);
    block(rig, "body", 21, 105, 7, 13, 12, 16, 6);
    block(rig, "body", 24, 116, 11, 7, 2, 8, 1);
  }

  private static void volcano(Rig rig, String bone, int x, int y, int z) {
    inlay(rig, bone, x - 42, y - 3, z - 50, 84, 18, 100, 6);
    inlay(rig, bone, x - 34, y + 14, z - 41, 68, 21, 82, 6);
    inlay(rig, bone, x - 26, y + 33, z - 31, 52, 17, 62, 7);
    carve(rig, bone, x - 22, y + 23, z - 23, x + 22, y + 61, z + 23);
    carve(rig, bone, x - 46, y + 29, z - 26, x - 13, y + 60, z + 20);
    carve(rig, bone, x - 34, y + 41, z + 8, x + 1, y + 62, z + 35);
    inlay(rig, bone, x - 22, y + 23, z - 23, 44, 3, 46, 8);
    inlay(rig, bone, x - 35, y + 27, z - 22, 14, 5, 30, 6);
    inlay(rig, bone, x - 37, y + 31, z - 25, 10, 8, 8, 7);
  }

  private static void sandstone(Rig rig) {
    rock(rig, "head", -17, 89, -154, 73, 26, 63, 0);
    rock(rig, "head", 24, 84, -198, 45, 19, 39, 6);
    rock(rig, "body", -15, 93, -38, 64, 29, 57, 0);
    rock(rig, "body", 25, 86, 14, 43, 16, 41, 6);
    block(rig, "head", -2, 103, -169, 7, 7, 70, 7);
    for (int index = 0; index < 5; index++) {
      int z = -165 + index * 14;
      int height = 22 - index % 2 * 4;
      rootBranch(rig, "head", 0, 104, z, -21, height + 104, z + 3, 5, 7);
      rootBranch(rig, "head", 0, 104, z, 20, height + 105, z + 3, 5, 7);
    }
    // Exposed desert fossil: a row of bone ribs on the left flank.
    JsonArray flank = rig.cubes("body").deepCopy();
    for (int index = 0; index < 6; index++) {
      int z = -62 + index * 13;
      int half = halfWidth(flank, 60, z);
      if (half > 4) inlay(rig, "body", -half, 46 + index % 2 * 4, z, 2, 32 - index % 3 * 5, 5, 7);
    }
    cactus(rig, "body", -25, 107, -46, 37);
    cactus(rig, "head", 31, 94, -116, 29);
    for (int index = 0; index < 4; index++)
      flower(
          rig,
          index < 2 ? "head" : "body",
          -30 + index * 19,
          index < 2 ? 102 : 102,
          index < 2 ? -202 + index * 69 : -36 + index * 13,
          9);
  }

  private static void reef(Rig rig) {
    branchingCoral(rig, "head", -15, 108, -145, 42, 2);
    branchingCoral(rig, "head", 20, 91, -196, 29, 1);
    branchingCoral(rig, "body", -21, 115, -44, 49, 6);
    branchingCoral(rig, "body", 21, 103, 10, 34, 7);
    tableCoral(rig, "head", 27, 98, -118, 41, 33, 3);
    tableCoral(rig, "body", 8, 121, -25, 46, 35, 2);
    fanCoral(rig, "head", -26, 95, -179, 39, 39, 1);
    fanCoral(rig, "body", -29, 103, -10, 42, 44, 7);
    for (int index = 0; index < 6; index++) {
      String bone = index < 3 ? "head" : "body";
      int z = index < 3 ? -192 + index * 38 : -54 + (index - 3) * 34;
      int x = index % 2 == 0 ? -23 : 23;
      for (int piece = 0; piece < 3; piece++)
        sprite(
            rig, bone, x + piece * 3, 32 - piece * 16, z + piece * 3, 16, 18, 8, 12 + piece * 16);
    }
    flower(rig, "flora_left", -26, 112, -51, 9);
    flower(rig, "flora_right", 29, 107, -14, 10);
  }

  private static void cloud(Rig rig) {
    for (String bone :
        List.of(
            "head",
            "body",
            "lower_jaw",
            "tail",
            "tail_flukes",
            "pectoral_fin_left",
            "pectoral_fin_right",
            "flipper_tip_left",
            "flipper_tip_right")) rig.cubes(bone).asList().clear();
    cloudPuff(rig, "head", -4, 51, -207, 76, 64, 91);
    cloudPuff(rig, "head", -3, 62, -246, 49, 42, 45);
    cloudPuff(rig, "head", 18, 30, -181, 60, 50, 77);
    cloudPuff(rig, "head", -12, 76, -143, 81, 61, 103);
    cloudPuff(rig, "head", 6, 49, -94, 73, 68, 67);
    cloudPuff(rig, "head", 17, 106, -150, 47, 40, 49);
    cloudPuff(rig, "body", -6, 38, -36, 79, 86, 111);
    cloudPuff(rig, "body", 17, 74, -26, 65, 65, 79);
    cloudPuff(rig, "body", -17, 20, -14, 55, 48, 75);
    cloudPuff(rig, "body", 6, 43, 37, 59, 56, 67);
    cloudPuff(rig, "body", -20, 104, -48, 45, 42, 51);
    cloudPuff(rig, "tail", -3, 48, 97, 37, 35, 55);
    cloudPuff(rig, "tail", 3, 51, 132, 29, 27, 37);
    cloudPuff(rig, "lower_jaw", -2, 37, -203, 49, 13, 79);
    cloudPuff(rig, "lower_jaw", 3, 29, -150, 55, 16, 95);
    for (int side : new int[] {-1, 1}) {
      String bone = side < 0 ? "pectoral_fin_left" : "pectoral_fin_right";
      String tip = side < 0 ? "flipper_tip_left" : "flipper_tip_right";
      cloudPuff(rig, bone, side * 49, 42, -42, 41, 14, 31);
      cloudPuff(rig, bone, side * 77, 47, -24, 29, 11, 27);
      cloudPuff(rig, tip, side * 99, 53, -4, 25, 9, 25);
      cloudPuff(rig, tip, side * 117, 59, 15, 17, 6, 23);
      cloudPuff(rig, tip, side * 126, 64, 32, 11, 4, 19);
      rig.bones.get(tip).add("pivot", numbers(side * 83, 52, -26));
      cloudPuff(rig, "tail_flukes", side * 35, 46, 151, 65, 19, 45);
      cloudPuff(rig, "tail_flukes", side * 66, 49, 180, 37, 12, 41);
      cloudPuff(rig, "tail_flukes", side * 86, 52, 210, 23, 7, 35);
      // Bridging puffs so fin and fluke chains read as one connected form.
      cloudPuff(rig, bone, side * 63, 44, -33, 30, 12, 26);
      cloudPuff(rig, tip, side * 108, 56, 5, 20, 8, 22);
      cloudPuff(rig, "tail_flukes", side * 50, 47, 165, 46, 15, 40);
      cloudPuff(rig, "tail_flukes", side * 76, 50, 195, 28, 9, 36);
    }
    cloudPuff(rig, "tail", 0, 49, 115, 31, 30, 42);
    // Grey rain-cloud underside, matching the lighter-belly reading of the other forms.
    for (String bone : List.of("head", "body", "lower_jaw", "tail"))
      for (var value : rig.cubes(bone)) {
        JsonObject cube = value.getAsJsonObject();
        int[] origin = integers(cube.getAsJsonArray("origin"));
        if ((origin[1] <= 32 || bone.equals("lower_jaw")) && cube.has("_slot")) {
          cube.remove("_slot");
          cube.addProperty("_role", "edge");
        }
      }
    rig.eyeLeftHalfWidth = 42;
    rig.eyeRightHalfWidth = 34;
    rig.eyeY = 75;
    rig.eyeZ = -234;
  }

  private static void coolArmor(Rig rig) {
    for (JsonObject bone : rig.bones.values()) {
      for (var value : bone.getAsJsonArray("cubes")) {
        JsonObject cube = value.getAsJsonObject();
        if (cube.has("_role")) cube.addProperty("_role", "coat");
      }
    }
  }

  private static void cloudPuff(
      Rig rig, String bone, int x, int y, int z, int width, int height, int depth) {
    block(
        rig,
        bone,
        x - width / 2,
        y + height / 4,
        z - depth * 2 / 5,
        width,
        height / 2,
        depth * 4 / 5,
        0);
    block(
        rig,
        bone,
        x - width * 2 / 5,
        y,
        z - depth * 3 / 10,
        width * 4 / 5,
        height,
        depth * 3 / 5,
        0);
    block(
        rig,
        bone,
        x - width * 3 / 10,
        y + height / 8,
        z - depth / 2,
        width * 3 / 5,
        height * 3 / 4,
        depth,
        0);
  }

  private static void flower(Rig rig, String bone, int x, int y, int z, int slot) {
    int ground = groundHeight(rig, x, z, y + 6);
    sprite(rig, bone, x, ground, z, 16, 16, slot, 45);
    sprite(rig, bone, x, ground, z, 16, 16, slot, 135);
  }

  private static int groundHeight(Rig rig, int x, int z, int ceiling) {
    int highest = Integer.MIN_VALUE;
    for (String bone : List.of("head", "body"))
      for (var value : rig.cubes(bone)) {
        JsonObject cube = value.getAsJsonObject();
        if (cube.has("_sampling") || cube.has("rotation")) continue;
        int[] origin = integers(cube.getAsJsonArray("origin"));
        int[] size = integers(cube.getAsJsonArray("size"));
        int top = origin[1] + size[1];
        if (x >= origin[0]
            && x <= origin[0] + size[0]
            && z >= origin[2]
            && z <= origin[2] + size[2]
            && top <= ceiling) highest = Math.max(highest, top);
      }
    if (highest == Integer.MIN_VALUE)
      throw new IllegalArgumentException("Unattached flora " + rig.variant + ":" + x + ":" + z);
    return highest;
  }

  private static void tree(Rig rig, String bone, int x, int y, int z, int height, int leaves) {
    inlay(rig, bone, x - 5, y - 3, z - 5, 10, height, 10, 2);
    rootedBranch(rig, bone, x, y + height * 2 / 3, z, x - 19, y + height - 7, z - 11, 7);
    rootedBranch(rig, bone, x, y + height * 2 / 3, z, x + 21, y + height - 3, z + 10, 7);
    treeCanopy(rig, bone, x - 11, y + height - 9, z - 7, 49, 28, 43, leaves);
    treeCanopy(rig, bone, x + 17, y + height - 2, z + 9, 43, 27, 39, leaves);
    treeCanopy(rig, bone, x, y + height + 14, z, 39, 20, 37, leaves);
  }

  private static void rootedBranch(
      Rig rig, String bone, int x0, int y0, int z0, int x1, int y1, int z1, int width) {
    int distance = Math.max(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)), Math.abs(z1 - z0));
    int steps = Math.max(1, distance / Math.max(3, width - 2));
    for (int step = 0; step <= steps; step++) {
      double fraction = (double) step / steps;
      int x = (int) Math.round(x0 + (x1 - x0) * fraction);
      int y = (int) Math.round(y0 + (y1 - y0) * fraction);
      int z = (int) Math.round(z0 + (z1 - z0) * fraction);
      JsonObject root =
          inlay(rig, bone, x - width / 2, y - width / 2, z - width / 2, width, width, width, 2);
      JsonObject faces = new JsonObject();
      for (String face : FACES) {
        JsonObject material = new JsonObject();
        material.addProperty("role", "growth");
        faces.add(face, material);
      }
      root.add("_faces", faces);
    }
  }

  private static void treeCanopy(
      Rig rig, String bone, int x, int y, int z, int width, int height, int depth, int slot) {
    inlay(rig, bone, x - width / 2, y, z - depth / 2, width, height / 2, depth, slot);
    inlay(
        rig,
        bone,
        x - width * 2 / 5,
        y + height / 2,
        z - depth * 2 / 5,
        width * 4 / 5,
        height / 3,
        depth * 4 / 5,
        slot);
    inlay(
        rig,
        bone,
        x - width / 4,
        y + height * 5 / 6,
        z - depth / 4,
        width / 2,
        Math.max(1, height / 6),
        depth / 2,
        slot);
  }

  private static void rootBranch(
      Rig rig, String bone, int x0, int y0, int z0, int x1, int y1, int z1, int width, int slot) {
    int distance = Math.max(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)), Math.abs(z1 - z0));
    int steps = Math.max(1, distance / Math.max(3, width - 2));
    for (int step = 0; step <= steps; step++) {
      double fraction = (double) step / steps;
      int x = (int) Math.round(x0 + (x1 - x0) * fraction),
          y = (int) Math.round(y0 + (y1 - y0) * fraction),
          z = (int) Math.round(z0 + (z1 - z0) * fraction);
      block(rig, bone, x - width / 2, y - width / 2, z - width / 2, width, width, width, slot);
    }
  }

  private static void hollowLog(Rig rig, String bone, int x, int y, int z) {
    int[][] walls = {{-8, 0, 16, 2}, {-8, 14, 16, 2}, {-8, 2, 2, 12}, {6, 2, 2, 12}};
    String[] inside = {"up", "down", "west", "east"};
    for (int index = 0; index < walls.length; index++) {
      int[] wall = walls[index];
      JsonObject cube =
          solid(rig, bone, x + wall[0], y + wall[1], z, wall[2], wall[3], 16, "growth");
      JsonObject faces = new JsonObject();
      for (String face : List.of("north", "south")) {
        JsonObject material = new JsonObject();
        material.addProperty("slot", 7);
        faces.add(face, material);
      }
      JsonObject inner = new JsonObject();
      inner.addProperty("slot", 11);
      faces.add(inside[index], inner);
      cube.add("_faces", faces);
    }
  }

  private static void basaltColumn(
      Rig rig, String bone, int x, int y, int z, int width, int height) {
    block(rig, bone, x - width / 2, y - 2, z - width / 2, width, height, width, 0);
    block(
        rig,
        bone,
        x - width / 2 + 2,
        y + height - 2,
        z - width / 2 + 2,
        width - 4,
        5,
        width - 4,
        6);
  }

  private static void cactus(Rig rig, String bone, int x, int y, int z, int height) {
    block(rig, bone, x - 5, y, z - 5, 10, height, 10, 8);
    block(rig, bone, x + 4, y + height / 3, z - 4, 13, 7, 8, 8);
    block(rig, bone, x + 11, y + height / 3, z - 4, 7, height / 3 + 7, 8, 8);
    block(rig, bone, x - 15, y + height / 2, z - 4, 11, 7, 8, 8);
    block(rig, bone, x - 15, y + height / 2, z - 4, 7, height / 3, 8, 8);
  }

  private static void branchingCoral(
      Rig rig, String bone, int x, int y, int z, int height, int slot) {
    block(rig, bone, x - 4, y - 3, z - 4, 8, height * 2 / 3, 8, slot);
    int[][] directions = {
      {-16, height * 2 / 3, -7}, {15, height * 3 / 4, 5}, {-7, height, 15}, {9, height + 4, -13}
    };
    for (int[] direction : directions) {
      rootBranch(
          rig,
          bone,
          x,
          y + height / 3,
          z,
          x + direction[0],
          y + direction[1] - 9,
          z + direction[2],
          5,
          slot);
      block(
          rig,
          bone,
          x + direction[0] - 3,
          y + direction[1] - 10,
          z + direction[2] - 3,
          6,
          15,
          6,
          slot);
      block(
          rig,
          bone,
          x + direction[0] - 8,
          y + direction[1] - 4,
          z + direction[2] - 3,
          16,
          5,
          6,
          slot);
    }
  }

  private static void tableCoral(
      Rig rig, String bone, int x, int y, int z, int width, int depth, int slot) {
    block(rig, bone, x - 4, y - 2, z - 4, 8, 19, 8, slot);
    block(rig, bone, x - width / 2, y + 16, z - depth / 3, width, 5, depth * 2 / 3, slot);
    block(rig, bone, x - width / 3, y + 16, z - depth / 2, width * 2 / 3, 5, depth, slot);
    block(rig, bone, x - width / 3, y + 21, z - depth / 3, width * 2 / 3, 3, depth * 2 / 3, slot);
  }

  private static void fanCoral(
      Rig rig, String bone, int x, int y, int z, int width, int height, int slot) {
    block(rig, bone, x - 3, y - 2, z - 3, 6, height / 3 + 3, 6, slot);
    for (int index = -2; index <= 2; index++) {
      int endX = x + index * width / 5;
      int endY = y + height - Math.abs(index) * height / 8;
      rootBranch(rig, bone, x, y + height / 4, z, endX, endY, z + index * 2, 4, slot);
      block(rig, bone, endX - 2, endY - 4, z + index * 2 - 2, 4, 12, 4, slot);
    }
    block(rig, bone, x - width / 3, y + height * 2 / 3, z - 2, width * 2 / 3, 4, 4, slot);
  }

  private static void rock(
      Rig rig, String bone, int x, int y, int z, int width, int height, int depth, int slot) {
    block(rig, bone, x - width / 2, y, z - depth / 2, width, Math.max(1, height / 2), depth, slot);
    block(
        rig,
        bone,
        x - width * 2 / 5,
        y + height / 2,
        z - depth * 2 / 5,
        width * 4 / 5,
        Math.max(1, height / 3),
        depth * 4 / 5,
        slot);
    block(
        rig,
        bone,
        x - width / 4,
        y + height * 5 / 6,
        z - depth / 4,
        width / 2,
        Math.max(1, height / 6),
        depth / 2,
        slot);
  }

  private static void mushroom(
      Rig rig, String bone, int x, int y, int z, int width, int stemHeight, int depth, int cap) {
    int stemWidth = Math.max(6, width / 6);
    block(
        rig,
        bone,
        x - stemWidth / 2,
        y - 3,
        z - stemWidth / 2,
        stemWidth,
        stemHeight + 7,
        stemWidth,
        3);
    block(
        rig,
        bone,
        x - stemWidth / 2 - 2,
        y - 1,
        z - stemWidth / 2 - 2,
        stemWidth + 4,
        8,
        stemWidth + 4,
        3);
    int base = y + stemHeight;
    block(rig, bone, x - width / 2 + 6, base - 4, z - depth / 2 + 5, width - 12, 5, depth - 10, 8);
    block(rig, bone, x - width / 2, base, z - depth / 3, width, 9, depth * 2 / 3, cap);
    block(rig, bone, x - width / 3, base, z - depth / 2, width * 2 / 3, 9, depth, cap);
    block(
        rig,
        bone,
        x - width * 2 / 5,
        base + 9,
        z - depth * 2 / 5,
        width * 4 / 5,
        9,
        depth * 4 / 5,
        cap);
    block(rig, bone, x - width / 4, base + 18, z - depth / 4, width / 2, 7, depth / 2, cap);
    for (int side : new int[] {-1, 1}) {
      for (int index = 0; index < 3; index++)
        block(
            rig,
            bone,
            x + side * (stemWidth / 2 + 3 + index * 5) - 1,
            base - 7 + index,
            z - depth / 3 + 3,
            2,
            3,
            depth * 2 / 3 - 6,
            8);
    }
  }

  private static void shelf(
      Rig rig, String bone, int x, int y, int z, int width, int depth, int slot, int side) {
    int center = x + side * width / 3;
    block(rig, bone, center - width / 2, y, z - depth / 2, width, 4, depth, 8);
    block(
        rig, bone, center - width / 2 - 2, y + 4, z - depth / 2 + 2, width + 4, 5, depth - 4, slot);
    block(
        rig, bone, center - width / 3, y + 9, z - depth / 3, width * 2 / 3, 4, depth * 2 / 3, slot);
  }

  private static void conifer(
      Rig rig, String bone, int x, int y, int z, int height, int log, int leaves) {
    block(rig, bone, x - 4, y - 2, z - 4, 8, height, 8, log);
    for (int tier = 0; tier < 4; tier++) {
      int width = (height * (4 - tier) / 8) + 4;
      int level = y + height / 4 + tier * height / 6;
      block(rig, bone, x - width, level, z - width, width * 2, 5, width * 2, leaves);
      block(rig, bone, x - width + 2, level + 5, z - width + 2, width * 2 - 4, 3, width * 2 - 4, 1);
    }
  }

  private static void crystal(
      Rig rig, String bone, int x, int y, int z, int width, int height, int slot, int tilt) {
    JsonObject core =
        block(rig, bone, x - width / 2, y - 2, z - width / 2, width, height * 2 / 3, width, slot);
    core.add("pivot", numbers(x, y, z));
    core.add("rotation", numbers(0, 16, tilt));
    JsonObject tip =
        block(
            rig,
            bone,
            x - width / 3,
            y + height * 2 / 3 - 3,
            z - width / 3,
            width * 2 / 3,
            height / 3,
            width * 2 / 3,
            slot);
    tip.add("pivot", numbers(x, y, z));
    tip.add("rotation", numbers(0, 16, tilt));
  }

  private static JsonObject stalactite(
      Rig rig, String bone, int x, int y, int z, int width, int height, int slot) {
    JsonObject root =
        block(
            rig,
            bone,
            x - width / 2,
            y - height / 2,
            z - width / 2,
            width,
            height / 2 + 2,
            width,
            slot);
    block(
        rig,
        bone,
        x - width / 3,
        y - height * 4 / 5,
        z - width / 3,
        Math.max(2, width * 2 / 3),
        height / 3 + 1,
        Math.max(2, width * 2 / 3),
        slot);
    block(rig, bone, x - 1, y - height, z - 1, 2, height / 4 + 1, 2, slot);
    return root;
  }

  /**
   * Where two cubes on one bone share an exposed face plane, the later cube owns the shared volume.
   * Only those pairs are split, so the visible union and atlas size stay nearly unchanged while no
   * two coincident faces can flicker between materials.
   */
  private static void resolveOverlaps(Rig rig) {
    for (String bone : List.copyOf(rig.bones.keySet())) {
      JsonArray resolved = new JsonArray();
      for (var value : rig.cubes(bone)) {
        JsonObject cube = value.getAsJsonObject();
        int[] low = integers(cube.getAsJsonArray("origin"));
        int[] size = integers(cube.getAsJsonArray("size"));
        int[] high = {low[0] + size[0], low[1] + size[1], low[2] + size[2]};
        if (!cube.has("rotation") && size[0] > 0 && size[1] > 0 && size[2] > 0) {
          JsonArray kept = new JsonArray();
          for (var earlierValue : resolved) {
            JsonObject earlier = earlierValue.getAsJsonObject();
            int[] a = integers(earlier.getAsJsonArray("origin"));
            int[] extent = integers(earlier.getAsJsonArray("size"));
            int[] b = {a[0] + extent[0], a[1] + extent[1], a[2] + extent[2]};
            if (earlier.has("rotation") || !coplanar(a, b, low, high)) {
              kept.add(earlier);
              continue;
            }
            int[] lo = {Math.max(a[0], low[0]), Math.max(a[1], low[1]), Math.max(a[2], low[2])};
            int[] hi = {Math.min(b[0], high[0]), Math.min(b[1], high[1]), Math.min(b[2], high[2])};
            fragment(kept, earlier, a[0], a[1], a[2], lo[0], b[1], b[2]);
            fragment(kept, earlier, hi[0], a[1], a[2], b[0], b[1], b[2]);
            fragment(kept, earlier, lo[0], a[1], a[2], hi[0], lo[1], b[2]);
            fragment(kept, earlier, lo[0], hi[1], a[2], hi[0], b[1], b[2]);
            fragment(kept, earlier, lo[0], lo[1], a[2], hi[0], hi[1], lo[2]);
            fragment(kept, earlier, lo[0], lo[1], hi[2], hi[0], hi[1], b[2]);
          }
          resolved = kept;
        }
        resolved.add(cube);
      }
      rig.bones.get(bone).add("cubes", resolved);
    }
  }

  /**
   * Cubes on different bones move independently, so they cannot share volume by carving. Where
   * their faces coincide at rest, the smaller cube's face is set back by one texel.
   */
  private static void separateBones(Rig rig) {
    List<JsonObject> cubes = new ArrayList<>();
    List<String> owners = new ArrayList<>();
    for (var entry : rig.bones.entrySet())
      for (var value : entry.getValue().getAsJsonArray("cubes")) {
        JsonObject cube = value.getAsJsonObject();
        int[] size = integers(cube.getAsJsonArray("size"));
        if (cube.has("rotation") || size[0] == 0 || size[1] == 0 || size[2] == 0) continue;
        cubes.add(cube);
        owners.add(entry.getKey());
      }
    java.util.Set<JsonObject> removed =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    java.util.Set<JsonObject> fixed =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    // Attachments and the anatomy cubes that carry them keep their exact contact.
    for (int root = 0; root < cubes.size(); root++) {
      if (!cubes.get(root).has("_attachment")) continue;
      fixed.add(cubes.get(root));
      int[] rLow = integers(cubes.get(root).getAsJsonArray("origin"));
      int[] rSize = integers(cubes.get(root).getAsJsonArray("size"));
      for (int host = 0; host < cubes.size(); host++) {
        if (!owners.get(host).equals(owners.get(root))) continue;
        int[] hLow = integers(cubes.get(host).getAsJsonArray("origin"));
        int[] hSize = integers(cubes.get(host).getAsJsonArray("size"));
        boolean touches = true;
        for (int axis = 0; axis < 3; axis++)
          touches &=
              Math.min(rLow[axis] + rSize[axis], hLow[axis] + hSize[axis])
                  >= Math.max(rLow[axis], hLow[axis]);
        if (touches) fixed.add(cubes.get(host));
      }
    }
    for (int pass = 0; pass < 16; pass++) {
      boolean changed = false;
      for (int first = 0; first < cubes.size(); first++)
        for (int second = first + 1; second < cubes.size(); second++) {
          if (owners.get(first).equals(owners.get(second))) continue;
          if (removed.contains(cubes.get(first)) || removed.contains(cubes.get(second))) continue;
          JsonObject one = cubes.get(first);
          JsonObject two = cubes.get(second);
          int[] aLow = integers(one.getAsJsonArray("origin"));
          int[] aSize = integers(one.getAsJsonArray("size"));
          int[] bLow = integers(two.getAsJsonArray("origin"));
          int[] bSize = integers(two.getAsJsonArray("size"));
          int[] aHigh = {aLow[0] + aSize[0], aLow[1] + aSize[1], aLow[2] + aSize[2]};
          int[] bHigh = {bLow[0] + bSize[0], bLow[1] + bSize[1], bLow[2] + bSize[2]};
          for (int axis = 0; axis < 3; axis++) {
            boolean low = aLow[axis] == bLow[axis];
            boolean high = aHigh[axis] == bHigh[axis];
            if (!low && !high) continue;
            boolean area = true;
            for (int other = 0; other < 3; other++)
              if (other != axis)
                area &= Math.min(aHigh[other], bHigh[other]) > Math.max(aLow[other], bLow[other]);
            if (!area) continue;
            boolean smallest =
                (long) aSize[0] * aSize[1] * aSize[2] <= (long) bSize[0] * bSize[1] * bSize[2];
            boolean firstSmaller = smallest;
            // Thin cubes and supported attachments keep their geometry; the other cube yields.
            if (firstSmaller ? aSize[axis] <= 2 : bSize[axis] <= 2) firstSmaller = !firstSmaller;
            if (fixed.contains(firstSmaller ? one : two)) firstSmaller = !firstSmaller;
            if (fixed.contains(firstSmaller ? one : two)) continue;
            JsonObject target = firstSmaller ? one : two;
            int[] origin = firstSmaller ? aLow : bLow;
            int[] size = firstSmaller ? aSize : bSize;
            if (size[axis] <= 2) {
              // Two thin decorative strips in one plane: drop the smaller one.
              if (fixed.contains(smallest ? one : two)) continue;
              removed.add(smallest ? one : two);
              changed = true;
              break;
            }
            size[axis] -= 1;
            if (low) origin[axis] += 1;
            target.add("origin", numbers(origin[0], origin[1], origin[2]));
            target.add("size", numbers(size[0], size[1], size[2]));
            changed = true;
            break;
          }
        }
      if (!changed) break;
    }
    for (var bone : rig.bones.values()) {
      JsonArray kept = new JsonArray();
      for (var value : bone.getAsJsonArray("cubes"))
        if (!removed.contains(value.getAsJsonObject())) kept.add(value);
      bone.add("cubes", kept);
    }
  }

  /** Whether two boxes share a face plane on the same side with a positive overlapping area. */
  private static boolean coplanar(int[] aLow, int[] aHigh, int[] bLow, int[] bHigh) {
    for (int axis = 0; axis < 3; axis++) {
      if (aLow[axis] != bLow[axis] && aHigh[axis] != bHigh[axis]) continue;
      boolean area = true;
      for (int other = 0; other < 3; other++)
        if (other != axis)
          area &= Math.min(aHigh[other], bHigh[other]) > Math.max(aLow[other], bLow[other]);
      if (area) return true;
    }
    return false;
  }

  private static JsonObject solid(
      Rig rig, String bone, int x, int y, int z, int width, int height, int depth, String role) {
    JsonObject cube = cube(x, y, z, width, height, depth);
    cube.addProperty("_role", role);
    rig.cubes(bone).add(cube);
    return cube;
  }

  private static JsonObject block(
      Rig rig, String bone, int x, int y, int z, int width, int height, int depth, int slot) {
    JsonObject cube = cube(x, y, z, width, height, depth);
    cube.addProperty("_slot", slot);
    rig.cubes(bone).add(cube);
    return cube;
  }

  private static JsonObject cube(int x, int y, int z, int width, int height, int depth) {
    if (width < 0 || height <= 0 || depth < 0 || width + depth <= 0)
      throw new IllegalArgumentException("Invalid whale cube");
    JsonObject cube = new JsonObject();
    cube.add("origin", numbers(x, y, z));
    cube.add("size", numbers(width, height, depth));
    return cube;
  }

  private static void sprite(
      Rig rig, String bone, int x, int y, int z, int width, int height, int slot, int yaw) {
    JsonObject cube = block(rig, bone, x - width / 2, y, z, width, height, 0, slot);
    cube.addProperty("_sampling", "sprite");
    cube.add("pivot", numbers(x, y, z));
    cube.add("rotation", numbers(0, yaw, 0));
  }

  private static JsonObject inlay(
      Rig rig, String bone, int x, int y, int z, int width, int height, int depth, int slot) {
    carve(rig, bone, x, y, z, x + width, y + height, z + depth);
    return block(rig, bone, x, y, z, width, height, depth, slot);
  }

  private static void carve(Rig rig, String bone, int x0, int y0, int z0, int x1, int y1, int z1) {
    JsonArray replacements = new JsonArray();
    for (var value : rig.cubes(bone)) {
      JsonObject cube = value.getAsJsonObject();
      if (cube.has("rotation")) {
        replacements.add(cube);
        continue;
      }
      int[] a = integers(cube.getAsJsonArray("origin"));
      int[] size = integers(cube.getAsJsonArray("size"));
      int[] b = {a[0] + size[0], a[1] + size[1], a[2] + size[2]};
      int[] lo = {Math.max(a[0], x0), Math.max(a[1], y0), Math.max(a[2], z0)};
      int[] hi = {Math.min(b[0], x1), Math.min(b[1], y1), Math.min(b[2], z1)};
      if (lo[0] >= hi[0] || lo[1] >= hi[1] || lo[2] >= hi[2]) {
        replacements.add(cube);
        continue;
      }
      fragment(replacements, cube, a[0], a[1], a[2], lo[0], b[1], b[2]);
      fragment(replacements, cube, hi[0], a[1], a[2], b[0], b[1], b[2]);
      fragment(replacements, cube, lo[0], a[1], a[2], hi[0], lo[1], b[2]);
      fragment(replacements, cube, lo[0], hi[1], a[2], hi[0], b[1], b[2]);
      fragment(replacements, cube, lo[0], lo[1], a[2], hi[0], hi[1], lo[2]);
      fragment(replacements, cube, lo[0], lo[1], hi[2], hi[0], hi[1], b[2]);
    }
    rig.bones.get(bone).add("cubes", replacements);
  }

  private static void fragment(
      JsonArray target, JsonObject source, int x0, int y0, int z0, int x1, int y1, int z1) {
    if (x1 <= x0 || y1 <= y0 || z1 <= z0) return;
    JsonObject cube = source.deepCopy();
    cube.add("origin", numbers(x0, y0, z0));
    cube.add("size", numbers(x1 - x0, y1 - y0, z1 - z0));
    target.add(cube);
  }

  private static void extraMaterials(JsonObject layout, String variant) {
    JsonArray slots = layout.getAsJsonArray("slots");
    switch (variant) {
      case "stone" -> {
        slots.add(surface("minecraft:block/coal_ore"));
        slots.add(surface("minecraft:block/iron_ore"));
        slots.add(surface("minecraft:block/copper_ore"));
        slots.add(surface("minecraft:block/deepslate"));
        slots.add(surface("minecraft:block/glow_lichen"));
      }
      case "fungal" -> slots.add(surface("minecraft:block/mushroom_block_inside"));
      case "snow" -> {
        slots.add(surface("minecraft:block/blue_ice"));
        slots.add(
            surface(
                "minecraft:block/spruce_log",
                "minecraft:block/spruce_log_top",
                "minecraft:block/spruce_log_top"));
        slots.add(surface("minecraft:block/azalea_leaves"));
      }
      case "moss" -> {
        slots.add(surface("minecraft:block/dandelion"));
        slots.add(surface("minecraft:block/mud"));
        slots.add(surface("minecraft:block/poppy"));
        slots.add(surface("minecraft:block/kelp_plant"));
      }
      case "rooted" -> {
        slots.add(surface("minecraft:block/oak_log_top"));
        slots.add(surface("minecraft:block/poppy"));
        slots.add(surface("minecraft:block/dandelion"));
        slots.add(surface("minecraft:block/kelp_plant"));
        slots.add(surface("minecraft:block/stripped_oak_log"));
        slots.add(surface("minecraft:block/flowering_azalea_leaves"));
      }
      case "granite" -> slots.add(surface("minecraft:block/amethyst_block"));
      case "chalk" -> {
        slots.add(surface("minecraft:block/tuff"));
        slots.add(surface("minecraft:block/moss_block"));
        slots.add(surface("minecraft:block/azure_bluet"));
      }
      case "lava" ->
          slots.add(
              surface(
                  "minecraft:block/lava_flow",
                  "minecraft:block/lava_still",
                  "minecraft:block/lava_still"));
      case "sandstone" -> {
        slots.add(
            surface(
                "minecraft:block/bone_block_side",
                "minecraft:block/bone_block_top",
                "minecraft:block/bone_block_top"));
        slots.add(
            surface(
                "minecraft:block/cactus_side",
                "minecraft:block/cactus_top",
                "minecraft:block/cactus_bottom"));
        slots.add(surface("minecraft:block/dead_bush"));
      }
      case "reef" -> {
        slots.add(surface("minecraft:block/kelp_plant"));
        slots.add(surface("minecraft:block/brain_coral_fan"));
        slots.add(surface("minecraft:block/fire_coral"));
      }
      default -> {}
    }
    slots.add(patch(variant));
  }

  static final class Bounds {
    final double[] minimum = {
      Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
    };
    final double[] maximum = {
      Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY
    };

    boolean empty() {
      return !Double.isFinite(minimum[0]);
    }

    void include(double[] point) {
      for (int axis = 0; axis < 3; axis++) {
        minimum[axis] = Math.min(minimum[axis], point[axis]);
        maximum[axis] = Math.max(maximum[axis], point[axis]);
      }
    }

    void include(Bounds other) {
      if (!other.empty()) {
        include(other.minimum);
        include(other.maximum);
      }
    }

    JsonObject json() {
      JsonObject bounds = new JsonObject();
      bounds.add("min", numbers(minimum));
      bounds.add("max", numbers(maximum));
      return bounds;
    }

    double[] corner(int corner) {
      double[] result = new double[3];
      for (int axis = 0; axis < 3; axis++)
        result[axis] = ((corner >> axis) & 1) == 0 ? minimum[axis] : maximum[axis];
      return result;
    }
  }

  private static Bounds boneBounds(Rig rig, JsonObject bone) {
    Bounds bounds = new Bounds();
    for (var value : bone.getAsJsonArray("cubes")) {
      JsonObject cube = value.getAsJsonObject();
      double[] origin = vector(cube.getAsJsonArray("origin"));
      double[] size = vector(cube.getAsJsonArray("size"));
      double[] pivot = cube.has("pivot") ? vector(cube.getAsJsonArray("pivot")) : new double[3];
      double[] angles =
          cube.has("rotation") ? vector(cube.getAsJsonArray("rotation")) : new double[3];
      for (int corner = 0; corner < 8; corner++) {
        double[] point = origin.clone();
        for (int axis = 0; axis < 3; axis++)
          point[axis] += ((corner >> axis) & 1) == 0 ? 0 : size[axis];
        bounds.include(rotate(point, pivot, angles));
      }
    }
    if (bounds.empty()) return bounds;
    JsonObject current = bone;
    while (current != null) {
      double[] pivot = vector(current.getAsJsonArray("pivot"));
      String name = current.get("name").getAsString();
      if (name.equals("lower_jaw") || name.startsWith("eyelid_")) {
        Bounds scaled = new Bounds();
        for (int corner = 0; corner < 8; corner++) {
          double[] point = bounds.corner(corner);
          scaled.include(point);
          point[1] = pivot[1] + (point[1] - pivot[1]) * (name.equals("lower_jaw") ? 1.4 : 0.0);
          scaled.include(point);
        }
        bounds = scaled;
      }
      double[] maximumAngles = motionEnvelope(name);
      for (int axis = 0; axis < 3; axis++)
        bounds = rotateRange(bounds, pivot, axis, maximumAngles[axis]);
      current = current.has("parent") ? rig.bones.get(current.get("parent").getAsString()) : null;
    }
    return bounds;
  }

  static double[] motionEnvelope(String bone) {
    return switch (bone) {
      case "body" -> new double[] {3, 0, 8};
      case "head" -> new double[] {17.5, 5, 2};
      case "lower_jaw" -> new double[] {35, 0, 0};
      case "tail" -> new double[] {20, 27.5, 0};
      case "tail_flukes" -> new double[] {10.669, 30.994, 7.319};
      case "pectoral_fin_left" -> new double[] {6, 4, 42.5};
      case "pectoral_fin_right" -> new double[] {6, 4, 37.5};
      case "flipper_tip_left", "flipper_tip_right" -> new double[] {3, 0, 7};
      case "flora_left", "flora_right" -> new double[] {2, 0, 2};
      default -> new double[3];
    };
  }

  private static Bounds rotateRange(Bounds source, double[] pivot, int axis, double degrees) {
    if (degrees == 0) return source;
    Bounds target = new Bounds();
    int first = (axis + 1) % 3;
    int second = (axis + 2) % 3;
    double angle = Math.toRadians(degrees);
    for (int corner = 0; corner < 8; corner++) {
      double[] point = source.corner(corner);
      double a = point[first] - pivot[first];
      double b = point[second] - pivot[second];
      List<Double> candidates = new ArrayList<>(List.of(-angle, angle, 0.0));
      for (double critical : new double[] {Math.atan2(-b, a), Math.atan2(a, b)}) {
        for (int turn = -2; turn <= 2; turn++) {
          double value = critical + turn * Math.PI;
          if (value >= -angle && value <= angle) candidates.add(value);
        }
      }
      for (double candidate : candidates) {
        double[] transformed = point.clone();
        transformed[first] = pivot[first] + a * Math.cos(candidate) - b * Math.sin(candidate);
        transformed[second] = pivot[second] + a * Math.sin(candidate) + b * Math.cos(candidate);
        target.include(transformed);
      }
    }
    return target;
  }

  private static double[] rotate(double[] source, double[] pivot, double[] angles) {
    double[] point = source.clone();
    for (int axis = 0; axis < 3; axis++) point[axis] -= pivot[axis];
    for (int axis = 0; axis < 3; axis++) {
      int first = (axis + 1) % 3, second = (axis + 2) % 3;
      double angle = Math.toRadians(angles[axis]), a = point[first], b = point[second];
      point[first] = a * Math.cos(angle) - b * Math.sin(angle);
      point[second] = a * Math.sin(angle) + b * Math.cos(angle);
    }
    for (int axis = 0; axis < 3; axis++) point[axis] += pivot[axis];
    return point;
  }

  private static double[] vector(JsonArray values) {
    return new double[] {
      values.get(0).getAsDouble(), values.get(1).getAsDouble(), values.get(2).getAsDouble()
    };
  }

  private static int[] integers(JsonArray values) {
    return new int[] {values.get(0).getAsInt(), values.get(1).getAsInt(), values.get(2).getAsInt()};
  }

  private static JsonArray numbers(double... values) {
    JsonArray array = new JsonArray();
    for (double value : values) array.add(value);
    return array;
  }

  private static void requireVariant(String variant) {
    if (!VARIANTS.contains(variant))
      throw new IllegalArgumentException("Unknown whale variety " + variant);
  }

  record NativeArt(JsonObject geometry, BufferedImage texture) {}
}
