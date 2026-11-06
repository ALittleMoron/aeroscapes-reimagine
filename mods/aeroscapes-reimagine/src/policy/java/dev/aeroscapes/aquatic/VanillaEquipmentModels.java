package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The first owner-reviewed batch: native instrument faces and continuous horn/rope volumes. */
final class VanillaEquipmentModels {
  private static final String TEXTURES = "aeroscapes_reimagine:item/vanilla_equipment/";
  private final JsonObject model = new JsonObject();
  private final JsonArray elements = new JsonArray();
  private final boolean copper;

  private VanillaEquipmentModels(JsonObject original, String material) {
    copper = material.equals("copper_horn");
    model.addProperty("parent", "minecraft:block/block");
    model.addProperty("ambientocclusion", false);
    JsonObject textures = original.getAsJsonObject("textures").deepCopy();
    textures.addProperty("particle", "#layer0");
    textures.addProperty("body", TEXTURES + material);
    if (copper) textures.addProperty("band", TEXTURES + "copper_band");
    model.add("textures", textures);
    model.add("elements", elements);
    JsonObject display = original.getAsJsonObject("display").deepCopy();
    for (String context :
        List.of(
            "firstperson_righthand",
            "firstperson_lefthand",
            "thirdperson_righthand",
            "thirdperson_lefthand"))
      if (!display.has(context))
        display.add(context, display.get(context.replace("lefthand", "righthand")).deepCopy());
    model.add("display", display);
  }

  static boolean supports(String id) {
    return id.equals("horseman:copper_horn")
        || id.equals("horseman:tooting_copper_horn")
        || id.matches(
            "minecraft:(clock(?:_[0-9]{2})?|(?:recovery_)?compass(?:_[0-9]{2})?|(?:tooting_)?goat_horn|lead)");
  }

  static JsonObject model(String id, JsonObject original) {
    if (VanillaEquipmentMesh.supports(id)) return VanillaEquipmentMesh.model(id, original);
    VanillaEquipmentModels shape =
        new VanillaEquipmentModels(original, id.startsWith("horseman:") ? "copper_horn" : "horn");
    shape.horn();
    return shape.model;
  }

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    for (var entry :
        Map.of(
                "copper_horn",
                0xE87D55,
                "copper_band",
                0x66645F,
                "gold",
                0xC69431,
                "silver",
                0xA1ABB0,
                "recovery",
                0x4B6873,
                "horn",
                0x69685E,
                "leather",
                0x996239)
            .entrySet()) {
      BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
      for (int y = 0; y < 32; y++)
        for (int x = 0; x < 32; x++) {
          int shade =
              (x == 2 || x == 3)
                  ? 3
                  : x == 29 ? -3 : Math.floorMod(x * 7 + y * 13, 37) == 0 ? 1 : 0;
          if (entry.getKey().equals("leather"))
            shade =
                Math.floorMod(x + y * 2, 11) < 2 ? -2 : Math.floorMod(x + y * 2, 11) == 3 ? 2 : 0;
          if (entry.getKey().equals("horn"))
            shade = Math.floorMod(x + y / 5, 9) == 0 ? -2 : x % 9 == 2 ? 1 : 0;
          image.setRGB(x, y, EquipmentModels.shaded(entry.getValue(), shade));
        }
      output.put(
          assets.resolve(
              "aeroscapes_reimagine/textures/item/vanilla_equipment/" + entry.getKey() + ".png"),
          EquipmentModels.png(image));
    }
  }

  private void horn() {
    List<Point> curve = new ArrayList<>();
    List<Double> radii = new ArrayList<>();
    for (int step = 0; step <= 64; step++) {
      double along = step / 64.0, t = along < .5 ? along * 2 : along * 2 - 1;
      double inverse = 1 - t;
      Point start = along < .5 ? new Point(4.8, 13, 8) : new Point(7, 3.2, 8);
      Point first = along < .5 ? new Point(3.8, 8, 8) : new Point(10.5, 2.6, 8);
      Point second = along < .5 ? new Point(3.5, 3.5, 8) : new Point(12.8, 4, 8);
      Point end = along < .5 ? new Point(7, 3.2, 8) : new Point(12.5, 8, 8);
      curve.add(
          new Point(
              inverse * inverse * inverse * start.x
                  + 3 * inverse * inverse * t * first.x
                  + 3 * inverse * t * t * second.x
                  + t * t * t * end.x,
              inverse * inverse * inverse * start.y
                  + 3 * inverse * inverse * t * first.y
                  + 3 * inverse * t * t * second.y
                  + t * t * t * end.y,
              8));
      radii.add(1.95 - 1.4 * along);
    }
    voxels(
        (x, y, z) -> {
          if (y > 13) return false;
          boolean shell = false;
          for (int index = 0; index < curve.size(); index++) {
            Point center = curve.get(index);
            double radius = radii.get(index);
            double distance =
                Math.sqrt(
                    (x - center.x) * (x - center.x)
                        + (y - center.y) * (y - center.y)
                        + (z - 8) * (z - 8));
            if (distance < radius - .65) return false;
            shell |= distance < radius;
          }
          return shell;
        });
  }

  private void voxels(Volume volume) {
    for (int y = 0; y < 30; y++)
      for (int z = 4; z < 28; z++) {
        int x = 0;
        while (x < 30) {
          if (!volume.contains((x + .5) / 2, (y + .5) / 2, (z + .5) / 2)) {
            x++;
            continue;
          }
          int end = x + 1;
          while (end < 30 && volume.contains((end + .5) / 2, (y + .5) / 2, (z + .5) / 2)) end++;
          box(x / 2.0, y / 2.0, z / 2.0, end / 2.0, (y + 1) / 2.0, (z + 1) / 2.0);
          x = end;
        }
      }
  }

  private void box(double x1, double y1, double z1, double x2, double y2, double z2) {
    JsonObject part = new JsonObject();
    part.add("from", vector(x1, y1, z1));
    part.add("to", vector(x2, y2, z2));
    JsonObject faces = new JsonObject();
    for (String direction : List.of("north", "south", "east", "west", "up", "down")) {
      double[] uv =
          switch (direction) {
            case "north" -> new double[] {16 - x2, 16 - y2, 16 - x1, 16 - y1};
            case "south" -> new double[] {x1, 16 - y2, x2, 16 - y1};
            case "east" -> new double[] {16 - z2, 16 - y2, 16 - z1, 16 - y1};
            case "west" -> new double[] {z1, 16 - y2, z2, 16 - y1};
            case "up" -> new double[] {x1, z1, x2, z2};
            case "down" -> new double[] {x1, 16 - z2, x2, 16 - z1};
            default -> throw new IllegalStateException(direction);
          };
      JsonObject face = new JsonObject();
      face.addProperty(
          "texture", copper && (y1 > 11.5 || x1 > 11.5 && y1 > 6.5) ? "#band" : "#body");
      face.add("uv", vector(uv));
      faces.add(direction, face);
    }
    part.add("faces", faces);
    elements.add(part);
  }

  private static JsonArray vector(double... values) {
    JsonArray result = new JsonArray();
    for (double value : values) result.add(value);
    return result;
  }

  private record Point(double x, double y, double z) {}

  @FunctionalInterface
  private interface Volume {
    boolean contains(double x, double y, double z);
  }
}
