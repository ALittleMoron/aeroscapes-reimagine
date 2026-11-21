package dev.aeroscapes.aquatic;

import static dev.aeroscapes.aquatic.HexHeldMesh.point;

import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.util.ArrayList;
import java.util.List;

final class HexFoldedPhial {
  final HexPhials body;

  HexFoldedPhial(HexPhials b) {
    body = b;
  }

  record Bend(double x, double y, double z, double radius) {}

  void build(int level) {
    double[][] coordinates = {
      {-.62, 4.00, -.23, .13},
      {-.79, 4.12, -.22, .37},
      {-1.32, 3.68, -.21, .47},
      {-1.39, 3.43, -.16, .52},
      {-.90, 2.92, -.13, .56},
      {-.19, 2.47, -.08, .58},
      {.14, 2.47, -.04, .59},
      {.82, 3.03, 0, .60},
      {1.27, 3.55, .04, .61},
      {1.26, 3.86, .07, .62},
      {.66, 4.46, .11, .62},
      {-.48, 5.51, .13, .62},
      {-1.07, 6.08, .13, .61},
      {-1.04, 6.36, .13, .59},
      {-.47, 6.91, .10, .58},
      {-.20, 6.97, .05, .54},
      {.34, 6.46, -.04, .49},
      {.65, 6.19, -.23, .44},
      {.96, 6.22, -.46, .44},
      {1.40, 6.77, -.59, .48},
      {1.37, 7.02, -.63, .48},
      {.75, 7.6, -.68, .48},
      {.05, 8.03, -.54, .48},
      {.00, 8.36, -.40, .47},
      {.00, 8.63, -.40, .47},
      {.00, 8.70, -.40, .61},
      {.00, 8.91, -.40, .61},
      {.00, 8.97, -.40, .54}
    };
    int count = coordinates.length;
    var bends = new Bend[count];
    for (int k = 0; k < count; k++) {
      var v = coordinates[k];
      bends[k] = new Bend(v[0], v[1], v[2], v[3]);
    }
    P[][] outer = section(bends, 0), inner = section(bends, .095), fluid = section(bends, .142);
    for (int k = 1; k < count; k++) {
      int n = HexPhials.SIDES;
      for (int i = 0; i < n; i++) {
        int next = (i + 1) % n;
        P[] o = {outer[k - 1][i], outer[k - 1][next], outer[k][next], outer[k][i]};
        P[] h = {inner[k - 1][i], inner[k - 1][next], inner[k][next], inner[k][i]};
        P out = outward(o, bends[k - 1], bends[k]);
        String m = k >= count - 3 ? "lip" : i == 2 ? "glass_flash" : "glass";
        body.face(body.item.glass, m, o[0], o[1], o[2], o[3], out);
        body.face(body.item.glass, "glass", h[0], h[1], h[2], h[3], out.scale(-1));
      }
    }
    for (int k : new int[] {0, count - 1}) {
      P cap = direction(bends[k == 0 ? 1 : count - 2], bends[k]);
      for (int i = 0; i < HexPhials.SIDES; i++) {
        int next = (i + 1) % HexPhials.SIDES;
        body.face(
            body.item.glass,
            k == 0 ? "glass" : "lip",
            outer[k][i],
            outer[k][next],
            inner[k][next],
            inner[k][i],
            cap);
      }
      if (k == 0) {
        // The terminal of the curled lower chamber is closed in glass.
        disk(body.item.glass, "glass", inner[0], cap);
      }
    }
    if (level > 0) {
      fill(
          fluid,
          bends,
          0,
          10,
          level == 4 ? Double.POSITIVE_INFINITY : 2.01 + (4.60 - 2.01) * level / 4.0,
          "aqua");
      fill(
          fluid,
          bends,
          10,
          count - 5,
          level == 4 ? Double.POSITIVE_INFINITY : 4.31 + (8.40 - 4.31) * level / 4.0,
          "liquid");
    }
    var loop = new ArrayList<P>();
    for (int i = 0; i <= 12; i++) {
      double a = (i + .13) * Math.PI / 6;
      loop.add(
          point(.50 * Math.cos(a), 8.49 + i * .005, -.40 + .50 * HexPhials.DEPTH * Math.sin(a)));
    }
    body.item.mesh.tube("cord", loop, .095, 5, 3);
    body.item.mesh.tube(
        "cord",
        List.of(
            point(.46, 8.55, -.36),
            point(.72, 8.33, -.23),
            point(1.76, 7.57, -.22),
            point(1.99, 7.14, -.20),
            point(2.12, 5.00, -.11)),
        .095,
        5,
        3);
  }

  private P[][] section(Bend[] bends, double inset) {
    int n = HexPhials.SIDES;
    P[][] vertices = new P[bends.length][n];
    for (int k = 0; k < bends.length; k++) {
      Bend b = bends[k],
          before = bends[Math.max(0, k - 1)],
          after = bends[Math.min(bends.length - 1, k + 1)];
      double dx = after.x - before.x, dy = after.y - before.y, d = Math.hypot(dx, dy);
      double r = Math.max(.04, b.radius - inset);
      for (int i = 0; i < n; i++) {
        var v = body.at(new HexPhials.Row(0, 0, r), i);
        double across = v.x() - 8, depth = v.z() - 8;
        vertices[k][i] =
            point(
                b.x + across * dy / d + (k == 0 ? inset * dx / d : 0),
                b.y - across * dx / d + (k == 0 ? inset * dy / d : 0),
                b.z + depth);
      }
    }
    return vertices;
  }

  private static P outward(P[] points, Bend a, Bend b) {
    return points[0]
        .add(points[1])
        .add(points[2])
        .add(points[3])
        .scale(.25)
        .add(point((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2).scale(-1));
  }

  private static P direction(Bend a, Bend b) {
    return new P(b.x - a.x, b.y - a.y, b.z - a.z);
  }

  private void disk(EquipmentObjMesh target, String m, P[] p, P normal) {
    P center = p[0].add(p[p.length / 2]).scale(.5);
    for (int i = 0; i < p.length; i++)
      target.quad(
          m,
          new P[] {center, p[i], p[(i + 1) % p.length], p[(i + 1) % p.length]},
          normal,
          new double[][] {{.5, 0}, {1, 1}, {0, 1}, {0, 1}});
  }

  private void fill(P[][] rings, Bend[] bends, int first, int last, double surface, String mat) {
    var cuts = new ArrayList<P[]>();
    for (int k = first + 1; k <= last; k++) {
      for (int i = 0; i < HexPhials.SIDES; i++) {
        int n = (i + 1) % HexPhials.SIDES;
        P[] p = {rings[k - 1][i], rings[k - 1][n], rings[k][n], rings[k][i]};
        P normal = outward(p, bends[k - 1], bends[k]);
        clipped(mat, List.of(p[0], p[1], p[2]), surface, normal, cuts);
        clipped(mat, List.of(p[0], p[2], p[3]), surface, normal, cuts);
      }
    }
    for (int k : new int[] {first, last}) {
      P[] rim = rings[k];
      P center = rim[0].add(rim[rim.length / 2]).scale(.5);
      P normal = direction(bends[k == first ? k + 1 : k - 1], bends[k]);
      for (int i = 0; i < rim.length; i++)
        clipped(mat, List.of(center, rim[i], rim[(i + 1) % rim.length]), surface, normal, cuts);
    }
    meniscus(mat, cuts, surface);
  }

  private void clipped(String mat, List<P> polygon, double surface, P normal, List<P[]> cuts) {
    var clipped = new ArrayList<P>();
    var crossing = new ArrayList<P>();
    P previous = polygon.getLast();
    for (P point : polygon) {
      if ((point.y() <= surface) != (previous.y() <= surface)) {
        double t = (surface - previous.y()) / (point.y() - previous.y());
        P intersection = previous.scale(1 - t).add(point.scale(t));
        clipped.add(intersection);
        crossing.add(intersection);
      }
      if (point.y() <= surface) clipped.add(point);
      previous = point;
    }
    if (crossing.size() == 2 && Math.abs(crossing.get(0).z() - crossing.get(1).z()) > 1e-8)
      cuts.add(new P[] {crossing.get(0), crossing.get(1)});
    for (int i = 1; i + 1 < clipped.size(); i++)
      tri(mat, clipped.getFirst(), clipped.get(i), clipped.get(i + 1), normal);
  }

  // Each pair of crossings bounds one horizontal liquid interval. A folded tube can
  // intersect the same water line several times, including on the returning hook.
  private void meniscus(String mat, List<P[]> cuts, double height) {
    var depths = new java.util.TreeSet<Double>();
    for (P[] cut : cuts) {
      depths.add(cut[0].z());
      depths.add(cut[1].z());
    }
    var bounds = List.copyOf(depths);
    var up = new P(0, 1, 0);
    for (int i = 1; i < bounds.size(); i++) {
      double z0 = bounds.get(i - 1), z1 = bounds.get(i), middle = (z0 + z1) / 2;
      if (z1 - z0 < .00001) continue;
      var edges =
          cuts.stream()
              .filter(
                  c ->
                      middle > Math.min(c[0].z(), c[1].z())
                          && middle < Math.max(c[0].z(), c[1].z()))
              .sorted(java.util.Comparator.comparingDouble(c -> crossingX(c, middle)))
              .toList();
      for (int j = 1; j < edges.size(); j += 2) {
        var left = edges.get(j - 1);
        var right = edges.get(j);
        var a = new P(crossingX(left, z0), height, z0);
        var b = new P(crossingX(right, z0), height, z0);
        var c = new P(crossingX(right, z1), height, z1);
        var d = new P(crossingX(left, z1), height, z1);
        tri(mat, a, b, c, up);
        tri(mat, a, c, d, up);
      }
    }
  }

  private static double crossingX(P[] edge, double z) {
    var a = edge[0];
    var b = edge[1];
    return a.x() + (z - a.z()) * (b.x() - a.x()) / (b.z() - a.z());
  }

  private void tri(String mat, P a, P b, P c, P normal) {
    if (b.vector().sub(a.vector()).cross(c.vector().sub(a.vector())).lengthSquared() < 1e-12)
      return;
    body.item.mesh.quad(
        mat, new P[] {a, b, c, c}, normal, new double[][] {{0, 0}, {1, 0}, {.5, 1}, {.5, 1}});
  }
}
