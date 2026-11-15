package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;

/** Copper-framed glass canisters retain the provider's empty and steam item identities. */
final class SteamContainerModels {
  static final List<String> ROOTS =
      List.of("steampowered:pressurized_gas_container", "steampowered:pressurized_steam_container");

  private static final String DIRECTORY = "aeroscapes_reimagine:models/item/steam_containers/";
  private static final String TEXTURE = "aeroscapes_reimagine:item/steam_containers/materials";
  private static final List<String> MATERIALS =
      List.of("copper", "copper_edge", "copper_dark", "glass", "glass_edge", "steam");

  private SteamContainerModels() {}

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    Path directory = assets.resolve("aeroscapes_reimagine/models/item/steam_containers");
    EquipmentObjMesh frame = mesh();
    frame.group("lower_copper_end_plate");
    cap(frame, 2.7, false);
    frame.group("upper_copper_end_plate");
    cap(frame, 13.3, true);
    frame.group("copper_frame_rails");
    for (double side : new double[] {-1, 1}) {
      frame.box(
          "copper_dark", 8 + side * 2.28 - .17, 3.25, 7.77, 8 + side * 2.28 + .17, 13.35, 8.23);
      frame.box("copper", 7.77, 3.25, 8 + side * 2.28 - .17, 8.23, 13.35, 8 + side * 2.28 + .17);
    }
    EquipmentObjMesh glass = mesh();
    glass.group("sealed_glass_barrel");
    glass.shell("glass", new P[][] {ring(3.3, 2.24), ring(13.3, 2.24)});
    EquipmentObjMesh steam = mesh();
    steam.group("enclosed_steam_cloud");
    steam.shell(
        "steam",
        new P[][] {
          cloudRing(3.35, 2.10, 0, 0),
          cloudRing(5.2, 2.11, -.04, .03),
          cloudRing(7.6, 2.04, .06, -.02),
          cloudRing(10.3, 2.12, -.02, .03),
          cloudRing(13.25, 2.10, 0, 0)
        });
    EquipmentModels.text(output, directory.resolve("frame.obj"), frame.obj());
    EquipmentModels.text(output, directory.resolve("glass.obj"), glass.obj());
    EquipmentModels.text(output, directory.resolve("steam.obj"), steam.obj());
    StringBuilder materials = new StringBuilder();
    for (String material : MATERIALS)
      materials
          .append("newmtl ")
          .append(material)
          .append("\nKa 0 0 0\nKd 1 1 1\nmap_Kd ")
          .append(TEXTURE)
          .append("\n\n");
    EquipmentModels.text(output, directory.resolve("equipment.mtl"), materials.toString());
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/steam_containers/materials.png"),
        EquipmentModels.png(materials()));
    Map<String, JsonObject> models = new LinkedHashMap<>();
    for (String root : ROOTS) {
      JsonObject children = new JsonObject();
      children.add("frame", obj("frame", "minecraft:cutout"));
      boolean filled = root.endsWith("steam_container");
      if (filled) children.add("steam", obj("steam", "minecraft:cutout"));
      children.add("glass", obj("glass", "minecraft:translucent"));
      JsonObject model = new JsonObject();
      model.addProperty("loader", "neoforge:composite");
      model.add("children", children);
      JsonArray order = new JsonArray();
      order.add("frame");
      if (filled) order.add("steam");
      order.add("glass");
      model.add("item_render_order", order);
      JsonObject textures = new JsonObject();
      textures.addProperty("particle", TEXTURE);
      model.add("textures", textures);
      model.add("display", display());
      models.put(root, model);
    }
    HeldItemModels.add(assets, output, models);
  }

  private static EquipmentObjMesh mesh() {
    EquipmentObjMesh mesh = new EquipmentObjMesh();
    for (int index = 0; index < MATERIALS.size(); index++) {
      int x = index % 3 * 8, y = index / 3 * 8;
      mesh.region(
          MATERIALS.get(index), (x + .1) / 24, (y + .1) / 16, (x + 7.9) / 24, (y + 7.9) / 16);
    }
    return mesh;
  }

  private static P[] ring(double y, double radius) {
    return EquipmentObjMesh.octagonalRing(8, y, 8, radius, radius);
  }

  private static P[] cloudRing(double y, double radius, double x, double z) {
    return EquipmentObjMesh.octagonalRing(8 + x, y, 8 + z, radius, radius * .93);
  }

  private static void cap(EquipmentObjMesh mesh, double y, boolean upper) {
    double sign = upper ? -1 : 1;
    mesh.shell(
        "copper",
        new P[][] {
          ring(y - sign * .18, 2.17),
          ring(y, 2.48),
          ring(y + sign * .42, 2.48),
          ring(y + sign * .70, 2.27)
        });
    mesh.shell("copper_edge", new P[][] {ring(y + sign * .50, 2.51), ring(y + sign * .67, 2.51)});
  }

  private static JsonObject obj(String name, String renderType) {
    JsonObject model = new JsonObject();
    model.addProperty("loader", "neoforge:obj");
    model.addProperty("model", DIRECTORY + name + ".obj");
    model.addProperty("automatic_culling", false);
    model.addProperty("emissive_ambient", false);
    model.addProperty("flip_v", false);
    model.addProperty("ambientocclusion", false);
    model.addProperty("render_type", renderType);
    JsonObject textures = new JsonObject();
    textures.addProperty("particle", TEXTURE);
    model.add("textures", textures);
    return model;
  }

  private static JsonObject display() {
    JsonObject display = new JsonObject();
    for (String context :
        List.of(
            "firstperson_righthand", "firstperson_lefthand",
            "thirdperson_righthand", "thirdperson_lefthand")) {
      JsonObject transform = new JsonObject();
      transform.add("rotation", vector(-8, -20, 0));
      transform.add("scale", vector(.70f, .70f, .70f));
      display.add(context, transform);
    }
    HeldItemModels.anchorHeld(display, new Vector3f(8, 3.4f, 9.7f), new Vector3f(0, .48f, .62f));
    return display;
  }

  private static JsonArray vector(float x, float y, float z) {
    JsonArray array = new JsonArray();
    array.add(x);
    array.add(y);
    array.add(z);
    return array;
  }

  private static BufferedImage materials() {
    BufferedImage image = new BufferedImage(24, 16, BufferedImage.TYPE_INT_ARGB);
    int[] colors = {0xFF9D5A49, 0xFFE6AF74, 0xFF78483E, 0x4079ABB2, 0x809CCBD2, 0xFFC8CBCA};
    for (int material = 0; material < colors.length; material++) {
      int color = colors[material];
      for (int y = 0; y < 8; y++)
        for (int x = 0; x < 8; x++) {
          int cloudCenter = y < 2 ? 4 : y < 4 ? 3 : y < 6 ? 2 : 3;
          int offset =
              material == 5
                  ? Math.abs(x - cloudCenter) <= 1 ? 10 : 0
                  : material < 3 && (x * 13 + y * 7 + material * 3) % 19 == 0 ? 3 : 0;
          int red = Math.min(255, (color >> 16 & 255) + offset);
          int green = Math.min(255, (color >> 8 & 255) + offset);
          int blue = Math.min(255, (color & 255) + offset);
          image.setRGB(
              material % 3 * 8 + x,
              material / 3 * 8 + y,
              color & 0xFF000000 | red << 16 | green << 8 | blue);
        }
    }
    return image;
  }
}
