package dev.aeroscapes.aquatic;

import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.util.ArrayList;

/** Builds model-space solids. Local x and z are centered on the held object's grip. */
final class HexHeldMesh {
  final EquipmentObjMesh mesh = new EquipmentObjMesh("equipment.mtl");
  final EquipmentObjMesh glass = new EquipmentObjMesh("equipment.mtl");

  static P point(double x, double y, double z) {
    return new P(8 + x, y, 8 + z);
  }

  void box(String mat, double x, double y, double z, double w, double h, double d) {
    mesh.box(mat, 8 + x - w / 2, y, 8 + z - d / 2, 8 + x + w / 2, y + h, 8 + z + d / 2);
  }

  void beam(String mat, double width, P... path) {
    for (int i = 1; i < path.length; i++) mesh.beam(mat, path[i - 1], path[i], width, 4);
  }

  void tapered(
      String mat, double x, double z, double y0, double r0, double y1, double r1, double depth) {
    shell(mesh, mat, x, z, depth, y0, r0, y1, r1);
  }

  static void shell(
      EquipmentObjMesh target, String mat, double x, double z, double depth, double... profile) {
    P[][] rings = new P[profile.length / 2][];
    for (int i = 0; i < rings.length; i++) {
      double radius = profile[2 * i + 1];
      rings[i] =
          EquipmentObjMesh.octagonalRing(8 + x, profile[2 * i], 8 + z, radius, radius * depth);
    }
    target.shell(mat, rings);
  }

  void gem(String mat, double x, double y, double z, double radius, double height) {
    shell(
        mesh, mat, x, z, .79, y, radius * .54, y + height * .43, radius, y + height, radius * .07);
  }

  void loop(String mat, double x, double y, double z, double rx, double ry, double wire) {
    var points = new ArrayList<P>();
    for (int i = 0; i < 8; i++) {
      double angle = i * Math.PI / 4;
      points.add(point(x + rx * Math.cos(angle), y + ry * Math.sin(angle), z));
    }
    points.add(points.getFirst());
    mesh.tube(mat, points, wire, 4, 4);
  }

  void staff(String wood) {
    HexStaves.build(this, wood);
  }
}
