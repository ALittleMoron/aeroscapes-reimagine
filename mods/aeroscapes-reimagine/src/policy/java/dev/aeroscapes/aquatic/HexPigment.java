package dev.aeroscapes.aquatic;

import static dev.aeroscapes.aquatic.HexHeldMesh.point;

import dev.aeroscapes.aquatic.EquipmentObjMesh.P;

/** Closed animated droplets. Every cutout in the native frame also opens through the thickness. */
final class HexPigment {
  private static final int SIZE = 16;
  private static final int[] FACETS = {0, 6, 10, SIZE};

  private HexPigment() {}

  static void build(EquipmentObjMesh mesh) {
    for (int a = 1; a < FACETS.length; a++) {
      int u = FACETS[a - 1], endU = FACETS[a];
      for (int b = 1; b < FACETS.length; b++) {
        int v = FACETS[b - 1], endV = FACETS[b];
        for (int side : new int[] {-1, 1})
          mesh.quad(
              "pigment",
              new P[] {
                vertex(u, v, side),
                vertex(endU, v, side),
                vertex(endU, endV, side),
                vertex(u, endV, side)
              },
              new P(0, 0, side),
              new double[][] {uv(u, v), uv(endU, v), uv(endU, endV), uv(u, endV)});
      }
      // Each side samples just inside its texel. An opaque neighbour conceals the internal wall.
      for (int pixel = 0; pixel < SIZE; pixel++)
        for (int side : new int[] {-1, 1}) {
          int edge = side < 0 ? pixel : pixel + 1;
          double inner = pixel + (side < 0 ? .15 : .85), outer = pixel + (side < 0 ? .35 : .65);
          mesh.quad(
              "pigment",
              new P[] {
                vertex(edge, u, 1),
                vertex(edge, endU, 1),
                vertex(edge, endU, -1),
                vertex(edge, u, -1)
              },
              new P(side, 0, 0),
              new double[][] {uv(inner, u), uv(inner, endU), uv(outer, endU), uv(outer, u)});
          mesh.quad(
              "pigment",
              new P[] {
                vertex(u, edge, 1),
                vertex(endU, edge, 1),
                vertex(endU, edge, -1),
                vertex(u, edge, -1)
              },
              new P(0, -side, 0),
              new double[][] {uv(u, inner), uv(endU, inner), uv(endU, outer), uv(u, outer)});
        }
    }
  }

  private static double[] uv(double u, double v) {
    return new double[] {u / SIZE, v / SIZE};
  }

  private static double curvature(int texel) {
    return Math.min(6, Math.min(texel, SIZE - texel)) / 6.0;
  }

  private static P vertex(int u, int v, int side) {
    double thickness = .11 + .42 * (curvature(u) + curvature(v));
    return point((u - 8) * .43, 5.15 + (8 - v) * .43, thickness * side);
  }
}
