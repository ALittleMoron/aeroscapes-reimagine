package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/** Open vessels share construction while providers retain native icons and runtime contents. */
final class EngineeringBucketModels {
  private static final String DIRECTORY = "aeroscapes_reimagine:models/item/engineering_buckets/";
  private static final String ATLAS = "aeroscapes_reimagine:item/engineering_buckets/materials";
  private static final double[][] UV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
  private static final double[][] TRIANGLE_UV = {{.5, .5}, {0, 0}, {1, 0}, {1, 0}};
  static final String COPPER = "fluidlogistics:copper_bucket";
  static final List<String> ROOTS = roots();

  private EngineeringBucketModels() {}

  private static List<String> roots() {
    var roots = new ArrayList<>(List.of(COPPER, "steampowered:steam_bucket"));
    for (String fluid :
        List.of("plant_oil", "ethanol", "biodiesel", "crude_oil", "diesel", "gasoline"))
      roots.add("createdieselgenerators:" + fluid + "_bucket");
    for (String color :
        List.of(
            "white",
            "orange",
            "magenta",
            "light_blue",
            "yellow",
            "lime",
            "pink",
            "gray",
            "light_gray",
            "cyan",
            "purple",
            "blue",
            "brown",
            "green",
            "red",
            "black")) roots.add("createdieselgenerators:" + color + "_cement_bucket");
    roots.add("vintageimprovements:sulfuric_acid_bucket");
    for (String metal : List.of("cast_iron", "bronze", "steel", "nethersteel"))
      roots.add("createbigcannons:molten_" + metal + "_bucket");
    for (String metal :
        List.of(
            "iron",
            "gold",
            "copper",
            "zinc",
            "brass",
            "tungsten",
            "steel",
            "netherite",
            "aluminum",
            "lead",
            "nickel",
            "osmium",
            "silver",
            "tin",
            "lithium",
            "obdurium",
            "invar",
            "electrum",
            "bronze",
            "constantan",
            "void_steel",
            "necromium",
            "slag")) roots.add("createmetallurgy:molten_" + metal + "_bucket");
    return List.copyOf(roots);
  }

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    Path directory = assets.resolve("aeroscapes_reimagine/models/item/engineering_buckets");
    EquipmentModels.text(output, directory.resolve("steel.obj"), vessel(false).obj());
    EquipmentModels.text(output, directory.resolve("copper.obj"), vessel(true).obj());
    EquipmentModels.text(output, directory.resolve("fill.obj"), fill().obj());
    EquipmentModels.text(output, directory.resolve("equipment.mtl"), materials());
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/engineering_buckets/materials.png"),
        EquipmentModels.png(atlas()));
    Map<String, JsonObject> models = new LinkedHashMap<>();
    for (String id : ROOTS) {
      if (id.equals(COPPER)) continue;
      String texture = id.replace(":", ":item/");
      JsonObject model = obj("steel.obj", ATLAS, texture);
      model.add("display", display());
      models.put(id, model);
    }
    HeldItemModels.add(assets, output, models);
    var copper = obj("copper.obj", ATLAS, "fluidlogistics:item/copper_bucket");
    copper.add("display", display());
    var common = obj("steel.obj", ATLAS, "minecraft:item/bucket");
    common.add("display", display());
    EquipmentModels.text(
        output,
        assets.resolve("aeroscapes_reimagine/models/item/bucket_body.json"),
        common.toString() + "\n");
    EquipmentModels.text(
        output,
        assets.resolve("aeroscapes_reimagine/models/item/bucket_fill.json"),
        obj("fill.obj", "minecraft:block/water_still", "minecraft:item/bucket").toString() + "\n");
    var highlight = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    highlight.setRGB(0, 0, 0xFFFFFFFF);
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/bucket_highlight.png"),
        EquipmentModels.png(highlight));
    EquipmentModels.text(
        output,
        assets.resolve("fluidlogistics/models/item/equipment/held/copper_bucket.json"),
        copper.toString() + "\n");
    EquipmentModels.text(
        output,
        assets.resolve("fluidlogistics/models/item/equipment/held/copper_bucket_fluid.json"),
        obj("fill.obj", "minecraft:block/water_still", "fluidlogistics:item/copper_bucket")
                .toString()
            + "\n");
    JsonObject profiles = new JsonObject();
    for (String id : ROOTS) {
      JsonObject arm = new JsonObject();
      arm.addProperty("scale", 1);
      arm.addProperty("offY", -.52);
      JsonObject sides = new JsonObject();
      sides.add("right", arm);
      sides.add("left", arm.deepCopy());
      JsonObject profile = new JsonObject();
      profile.add("armTransforms", sides);
      profiles.add(id, profile);
    }
    JsonObject compat = new JsonObject();
    compat.add("itemSpecific", profiles);
    EquipmentModels.text(
        output,
        assets.resolve("minecraft/punchy/compat/aeroscapes_engineering_buckets.json"),
        compat.toString() + "\n");
  }

  private static JsonObject obj(String name, String texture, String particle) {
    JsonObject model = new JsonObject();
    model.addProperty("loader", "neoforge:obj");
    model.addProperty("model", DIRECTORY + name);
    model.addProperty("automatic_culling", false);
    model.addProperty("flip_v", false);
    model.addProperty("ambientocclusion", false);
    model.addProperty("render_type", "minecraft:cutout");
    JsonObject textures = particle(particle);
    textures.addProperty(name.equals("fill.obj") ? "fluid" : "body", texture);
    model.add("textures", textures);
    return model;
  }

  private static JsonObject particle(String texture) {
    JsonObject textures = new JsonObject();
    textures.addProperty("particle", texture);
    return textures;
  }

  private static String materials() {
    StringBuilder materials = new StringBuilder();
    for (String name :
        List.of("steel", "inside", "rim", "handle", "copper", "copper_edge", "copper_base"))
      materials.append("newmtl ").append(name).append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd #body\n\n");
    return materials.append("newmtl fluid\nKa 0 0 0\nKd 1 1 1\nmap_Kd #fluid\n").toString();
  }

  private static BufferedImage atlas() throws IOException {
    var steel = NativeEquipmentModels.image("minecraft:item/bucket");
    var copper = NativeEquipmentModels.image("fluidlogistics:item/copper_bucket");
    int[] colors = {
      closest(steel, 0xA8A8A8), closest(steel, 0x545454), closest(steel, 0x727272),
      closest(steel, 0x969696), closest(copper, 0xB56D4A), closest(copper, 0x753D2A),
      closest(copper, 0x626262)
    };
    var atlas = new BufferedImage(112, 16, BufferedImage.TYPE_INT_ARGB);
    for (int material = 0; material < colors.length; material++)
      for (int y = 0; y < 16; y++)
        for (int x = 0; x < 16; x++) atlas.setRGB(material * 16 + x, y, colors[material]);
    return atlas;
  }

  private static int closest(BufferedImage image, int color) {
    int nearest = 0, distance = Integer.MAX_VALUE;
    for (int y = 0; y < image.getHeight(); y++)
      for (int x = 0; x < image.getWidth(); x++) {
        int pixel = image.getRGB(x, y);
        if (pixel >>> 24 != 255) continue;
        int red = (pixel >> 16 & 255) - (color >> 16 & 255);
        int green = (pixel >> 8 & 255) - (color >> 8 & 255);
        int blue = (pixel & 255) - (color & 255);
        int difference = red * red + green * green + blue * blue;
        if (difference < distance) {
          nearest = pixel;
          distance = difference;
        }
      }
    if (distance == Integer.MAX_VALUE) throw new IllegalArgumentException("No opaque native metal");
    return nearest;
  }

  private static EquipmentObjMesh vessel(boolean copper) {
    var mesh = new EquipmentObjMesh();
    for (int index = 0; index < 7; index++)
      mesh.region(
          List.of("steel", "inside", "rim", "handle", "copper", "copper_edge", "copper_base")
              .get(index),
          (index * 16 + .1) / 112,
          .1 / 16,
          (index * 16 + 15.9) / 112,
          15.9 / 16);
    P[] foot = ring(1.15, 3.85), lower = ring(1.65, 4.25), middle = ring(5.6, 4.95);
    P[] shoulder = ring(9.15, 5.7), lip = ring(9.8, 5.7);
    P[] inside = ring(1.8, 3.7), opening = ring(9.8, 4.942);
    disk(mesh, "rim", foot, -1);
    wall(mesh, "rim", foot, lower, false);
    wall(mesh, copper ? "copper_base" : "steel", lower, middle, false);
    wall(mesh, copper ? "copper" : "steel", middle, shoulder, false);
    wall(mesh, copper ? "copper_edge" : "rim", shoulder, lip, false);
    rim(mesh, copper ? "copper_edge" : "rim", lip, opening);
    wall(mesh, "inside", inside, opening, true);
    disk(mesh, "inside", inside, 1);
    mesh.beam("rim", new P(1.7, 8.6, 8), new P(2.55, 8.6, 8), .7, 6);
    mesh.beam("rim", new P(13.45, 8.6, 8), new P(14.3, 8.6, 8), .7, 6);
    mesh.tube(
        copper ? "copper_edge" : "handle",
        List.of(
            new P(1.85, 8.6, 8),
            new P(2.3, 11.55, 8),
            new P(4.75, 13.8, 8),
            new P(8, 14.25, 8),
            new P(11.25, 13.8, 8),
            new P(13.7, 11.55, 8),
            new P(14.15, 8.6, 8)),
        .34,
        4,
        4);
    return mesh;
  }

  private static EquipmentObjMesh fill() {
    var mesh = new EquipmentObjMesh();
    P[] bottom = ring(1.8, 3.7), top = ring(9, 4.8);
    disk(mesh, "fluid", bottom, -1);
    wall(mesh, "fluid", bottom, top, false);
    disk(mesh, "fluid", top, 1);
    return mesh;
  }

  private static P[] ring(double height, double radius) {
    return EquipmentObjMesh.octagonalRing(8, height, 8, radius, radius);
  }

  private static void wall(
      EquipmentObjMesh mesh, String material, P[] bottom, P[] top, boolean inward) {
    for (int side = 0; side < bottom.length; side++) {
      int next = (side + 1) % bottom.length;
      P midpoint = bottom[side].add(bottom[next]).scale(.5);
      P normal = new P(midpoint.x() - 8, 0, midpoint.z() - 8);
      mesh.quad(
          material,
          new P[] {bottom[side], bottom[next], top[next], top[side]},
          normal.scale(inward ? -1 : 1),
          UV);
    }
  }

  private static void rim(EquipmentObjMesh mesh, String material, P[] outside, P[] inside) {
    for (int side = 0; side < outside.length; side++) {
      int next = (side + 1) % outside.length;
      mesh.quad(
          material,
          new P[] {outside[side], outside[next], inside[next], inside[side]},
          new P(0, 1, 0),
          UV);
    }
  }

  private static void disk(EquipmentObjMesh mesh, String material, P[] edge, int direction) {
    P center = new P(8, edge[0].y(), 8);
    for (int side = 0; side < edge.length; side++) {
      P next = edge[(side + 1) % edge.length];
      double[][] texture =
          material.equals("fluid")
              ? new double[][] {
                {.5, .5},
                {(edge[side].x() - 8) / 9.6 + .5, (edge[side].z() - 8) / 9.6 + .5},
                {(next.x() - 8) / 9.6 + .5, (next.z() - 8) / 9.6 + .5},
                {(next.x() - 8) / 9.6 + .5, (next.z() - 8) / 9.6 + .5}
              }
              : TRIANGLE_UV;
      mesh.quad(
          material, new P[] {center, edge[side], next, next}, new P(0, direction, 0), texture);
    }
  }

  private static JsonObject display() {
    JsonObject display = new JsonObject();
    for (String perspective : List.of("firstperson", "thirdperson"))
      for (String hand : List.of("righthand", "lefthand")) {
        boolean right = hand.equals("righthand");
        JsonObject transform = new JsonObject();
        transform.add("rotation", vector(18, -16, right ? 5 : -5));
        transform.add("scale", vector(.77F, .77F, .77F));
        display.add(perspective + "_" + hand, transform);
      }
    HeldItemModels.anchorHeld(display, new Vector3f(8, 14.25F, 8), new Vector3f(0, -1.2F, 1.2F));
    return display;
  }

  private static JsonArray vector(float x, float y, float z) {
    JsonArray result = new JsonArray();
    result.add(x);
    result.add(y);
    result.add(z);
    return result;
  }
}
