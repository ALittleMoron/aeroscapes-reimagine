package dev.aeroscapes.aquatic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joml.Vector3d;

/** A volumetric extracting head on the provider's full-length square oak staff. */
final class CannonWormModels {
  private static final String DIRECTORY = "aeroscapes_reimagine/models/item/cannon_worm/";

  private CannonWormModels() {}

  static void addResources(Path assets, Map<Path, byte[]> output) throws IOException {
    EquipmentObjMesh mesh = new EquipmentObjMesh();
    mesh.material("wood", "worm_wood");
    mesh.material("end", "worm_end");
    mesh.material("iron", "worm_iron");
    mesh.region("worm_wood", 0, 0, .125, 1);
    mesh.region("worm_end", 7.0 / 16, 7.0 / 16, 9.0 / 16, 9.0 / 16);
    mesh.projectedField("worm_iron");
    staff(mesh, -16, 0);
    staff(mesh, 0, 16);
    staff(mesh, 16, 24);
    mesh.quad(
        "end",
        new P[] {new P(7, -16, 7), new P(9, -16, 7), new P(9, -16, 9), new P(7, -16, 9)},
        new P(0, -1, 0),
        new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
    socket(mesh);
    tine(mesh, 0);
    tine(mesh, Math.PI);
    output.put(assets.resolve(DIRECTORY + "worm.obj"), mesh.obj().getBytes(StandardCharsets.UTF_8));
    EquipmentModels.text(
        output,
        assets.resolve(DIRECTORY + "equipment.mtl"),
        """
        newmtl worm_wood
        Ka 0 0 0
        Kd 1 1 1
        map_Kd minecraft:block/oak_log

        newmtl worm_end
        Ka 0 0 0
        Kd 1 1 1
        map_Kd create:block/axis_top

        newmtl worm_iron
        Ka 0 0 0
        Kd 1 1 1
        map_Kd aeroscapes_reimagine:item/cannon_worm/iron
        """);
    output.put(
        assets.resolve("aeroscapes_reimagine/textures/item/cannon_worm/iron.png"),
        EquipmentModels.png(iron()));
    JsonObject model = new JsonObject();
    model.addProperty("loader", "neoforge:obj");
    model.addProperty("model", "aeroscapes_reimagine:models/item/cannon_worm/worm.obj");
    model.addProperty("automatic_culling", false);
    model.addProperty("flip_v", false);
    model.addProperty("ambientocclusion", false);
    model.addProperty("render_type", "minecraft:cutout");
    JsonObject textures = new JsonObject();
    textures.addProperty("particle", "minecraft:block/oak_log");
    model.add("textures", textures);
    JsonObject display =
        HeldItemModels.nativeModel("createbigcannons:worm").getAsJsonObject("display").deepCopy();
    for (String hand : List.of("thirdperson_righthand", "thirdperson_lefthand")) {
      JsonArray rotation = new JsonArray();
      rotation.add(-45);
      rotation.add(45);
      rotation.add(0);
      display.getAsJsonObject(hand).add("rotation", rotation);
    }
    model.add("display", display);
    HeldItemModels.add(assets, output, Map.of("createbigcannons:worm", model));
  }

  private static void staff(EquipmentObjMesh mesh, double bottom, double top) {
    double[][] uv = {{0, 1}, {1, 1}, {1, 1 - (top - bottom) / 16}, {0, 1 - (top - bottom) / 16}};
    mesh.quad(
        "wood",
        new P[] {new P(7, bottom, 7), new P(9, bottom, 7), new P(9, top, 7), new P(7, top, 7)},
        new P(0, 0, -1),
        uv);
    mesh.quad(
        "wood",
        new P[] {new P(9, bottom, 9), new P(7, bottom, 9), new P(7, top, 9), new P(9, top, 9)},
        new P(0, 0, 1),
        uv);
    mesh.quad(
        "wood",
        new P[] {new P(7, bottom, 9), new P(7, bottom, 7), new P(7, top, 7), new P(7, top, 9)},
        new P(-1, 0, 0),
        uv);
    mesh.quad(
        "wood",
        new P[] {new P(9, bottom, 7), new P(9, bottom, 9), new P(9, top, 9), new P(9, top, 7)},
        new P(1, 0, 0),
        uv);
  }

  private static void socket(EquipmentObjMesh mesh) {
    mesh.box("iron", 6.65, 22.8, 6.65, 7, 24, 9.35);
    mesh.box("iron", 9, 22.8, 6.65, 9.35, 24, 9.35);
    mesh.box("iron", 7, 22.8, 6.65, 9, 24, 7);
    mesh.box("iron", 7, 22.8, 9, 9, 24, 9.35);
    mesh.box("iron", 6.65, 24, 6.65, 9.35, 25.2, 9.35);
    mesh.box("iron", 6.48, 23.22, 7.72, 9.52, 23.78, 8.28);
  }

  private static void tine(EquipmentObjMesh mesh, double phase) {
    List<P> points = new ArrayList<>();
    points.add(new P(8 + .85 * Math.cos(phase), 24.8, 8 + .85 * Math.sin(phase)));
    for (int step = 0; step <= 20; step++) {
      double progress = step / 20.0;
      double angle = phase + progress * Math.PI * 2.4;
      points.add(
          new P(8 + 2.25 * Math.cos(angle), 25.1 + 6.9 * progress, 8 + 2.25 * Math.sin(angle)));
    }
    List<P[]> rings = new ArrayList<>();
    for (int index = 0; index < points.size(); index++) {
      P point = points.get(index);
      Vector3d tangent =
          points
              .get(Math.min(index + 1, points.size() - 1))
              .vector()
              .sub(points.get(Math.max(0, index - 1)).vector())
              .normalize();
      Vector3d radial = new Vector3d(point.x() - 8, 0, point.z() - 8).normalize();
      Vector3d across = new Vector3d(tangent).cross(radial).normalize();
      radial = new Vector3d(across).cross(tangent).normalize();
      double halfWidth = index < 18 ? .37 : .37 - (index - 17) * .07;
      P[] ring = new P[4];
      for (int side = 0; side < 4; side++) {
        int radialSign = side == 0 || side == 3 ? -1 : 1;
        int acrossSign = side < 2 ? -1 : 1;
        Vector3d offset =
            new Vector3d(radial)
                .mul(radialSign * halfWidth)
                .add(new Vector3d(across).mul(acrossSign * halfWidth));
        ring[side] = point.add(new P(offset.x, offset.y, offset.z));
      }
      rings.add(ring);
    }
    for (int index = 1; index < rings.size(); index++) {
      P[] before = rings.get(index - 1), after = rings.get(index);
      for (int side = 0; side < 4; side++) {
        int next = (side + 1) % 4;
        P a = before[side], b = after[side], c = after[next], d = before[next];
        P outward =
            a.add(b)
                .add(c)
                .add(d)
                .scale(.25)
                .add(points.get(index - 1).add(points.get(index)).scale(-.5));
        mesh.quad("iron", new P[] {a, b, c, c}, outward, null);
        mesh.quad("iron", new P[] {a, c, d, d}, outward, null);
      }
    }
    for (int end : new int[] {0, rings.size() - 1}) {
      int neighbor = end == 0 ? 1 : end - 1;
      mesh.quad("iron", rings.get(end), points.get(end).add(points.get(neighbor).scale(-1)), null);
    }
  }

  private static BufferedImage iron() {
    BufferedImage texture = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 64; y++) {
      for (int x = 0; x < 64; x++) {
        int variation = Math.floorMod(x / 3 * 7 + y / 4 * 11, 5) - 2;
        int shade = 43 + variation * 3;
        texture.setRGB(x, y, 0xff000000 | shade << 16 | shade << 8 | shade + 4);
      }
    }
    return texture;
  }
}
