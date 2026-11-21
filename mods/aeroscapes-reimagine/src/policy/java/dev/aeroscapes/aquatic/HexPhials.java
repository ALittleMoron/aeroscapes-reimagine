package dev.aeroscapes.aquatic;

import static dev.aeroscapes.aquatic.HexHeldMesh.point;

import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class HexPhials {
  record Row(double y, double x, double r) {
    Row inset(double d) {
      return new Row(y, x, Math.max(.06, r - d));
    }
  }

  final HexHeldMesh item;
  static final int SIDES = 8;
  static final double DEPTH = .66;
  private static final double[][] SECTION = {
    {1, .55}, {.55, 1}, {-.55, 1}, {-1, .55}, {-1, -.55}, {-.55, -1}, {.55, -1}, {1, -.55}
  };

  HexPhials(HexHeldMesh item) {
    this.item = item;
  }

  Row[] profile(int size) {
    double[][] values =
        switch (size) {
          case 0 ->
              new double[][] {
                {2.03, 0, .32}, {2.17, 0, .62}, {6.97, 0, .62}, {7.13, 0, .51}, {7.64, 0, .51}
              };
          case 1 ->
              new double[][] {
                {2.1, 0, 1.17},
                {2.29, 0, 1.50},
                {2.65, 0, 1.49},
                {5.38, 0, .75},
                {6.06, 0, .47},
                {7.41, 0, .48}
              };
          case 2 ->
              new double[][] {
                {2.04, 0, .67},
                {2.21, 0, .97},
                {3.49, 0, 1.85},
                {4.81, 0, 1.94},
                {5.49, 0, 1.68},
                {6.68, 0, .54},
                {7.48, 0, .54}
              };
          case 3 ->
              new double[][] {
                {1.74, 0, .60},
                {1.99, 0, 1.03},
                {2.99, 0, 1.56},
                {3.45, 0, 1.53},
                {4.58, 0, .66},
                {4.80, 0, .54},
                {5.06, 0, .68},
                {6.06, 0, 1.55},
                {6.59, 0, 1.48},
                {7.51, 0, .53},
                {8.05, 0, .51}
              };
          default -> throw new IllegalArgumentException("Unknown phial size " + size);
        };
    return Arrays.stream(values).map(v -> new Row(v[0], v[1], v[2])).toArray(Row[]::new);
  }

  P at(Row row, int i) {
    var xy = SECTION[i % SIDES];
    return point(row.x + xy[0] * row.r, row.y, xy[1] * row.r * DEPTH);
  }

  void face(EquipmentObjMesh mesh, String mat, P a, P b, P c, P d, P out) {
    mesh.quad(mat, new P[] {a, b, c, c}, out, new double[][] {{0, 1}, {1, 1}, {1, 0}, {1, 0}});
    mesh.quad(mat, new P[] {a, c, d, d}, out, new double[][] {{0, 1}, {1, 0}, {0, 0}, {0, 0}});
  }

  void connect(EquipmentObjMesh mesh, String material, Row lower, Row upper, boolean inside) {
    for (int i = 0; i < SIDES; i++) {
      P a = at(lower, i), b = at(lower, i + 1), c = at(upper, i + 1), d = at(upper, i);
      P normal = a.add(b).scale(.5).add(point(lower.x, lower.y, 0).scale(-1));
      String m = material;
      if (!inside && material.equals("glass") && i == 2) m = "glass_flash";
      face(mesh, m, a, b, c, d, normal.scale(inside ? -1 : 1));
    }
  }

  void annulus(String mat, Row outer, Row inner) {
    for (int i = 0; i < SIDES; i++)
      face(
          item.glass,
          mat,
          at(outer, i),
          at(outer, i + 1),
          at(inner, i + 1),
          at(inner, i),
          new P(0, 1, 0));
  }

  void floor(EquipmentObjMesh target, String mat, Row r, boolean up) {
    for (int i = 0; i < SIDES; i++)
      target.quad(
          mat,
          new P[] {point(r.x, r.y, 0), at(r, i), at(r, i + 1), at(r, i + 1)},
          new P(0, up ? 1 : -1, 0),
          new double[][] {{.5, 0}, {1, 1}, {0, 1}, {0, 1}});
  }

  Row sample(Row[] profile, double y) {
    for (int i = 1; i < profile.length; i++)
      if (y <= profile[i].y) {
        var a = profile[i - 1];
        var b = profile[i];
        double t = (y - a.y) / (b.y - a.y);
        return new Row(y, a.x + (b.x - a.x) * t, a.r + (b.r - a.r) * t);
      }
    throw new IllegalArgumentException("outside body: " + y);
  }

  void liquid(Row[] rows, double low, double high, String mat) {
    var profile = new ArrayList<Row>();
    profile.add(sample(rows, low).inset(.16));
    for (Row r : rows) if (r.y > low + .01 && r.y < high - .01) profile.add(r.inset(.16));
    profile.add(sample(rows, high).inset(.16));
    floor(item.mesh, mat, profile.getFirst(), false);
    for (int i = 1; i < profile.size(); i++)
      connect(item.mesh, mat, profile.get(i - 1), profile.get(i), false);
    floor(item.mesh, mat, profile.getLast(), true);
  }

  void build(int size, int level) {
    if (size == 4) {
      new HexFoldedPhial(this).build(level);
      return;
    }
    var rows = profile(size);
    var bottom = rows[0];
    var top = rows[rows.length - 1];
    var outer = new ArrayList<>(List.of(rows));
    outer.add(new Row(top.y + .08, top.x, top.r + .13));
    outer.add(new Row(top.y + .28, top.x, top.r + .13));
    outer.add(new Row(top.y + .36, top.x, top.r + .07));
    var inner = new ArrayList<Row>();
    for (var rr : outer) inner.add(rr.inset(.10));
    inner.set(0, new Row(bottom.y + .12, bottom.x, Math.max(.09, bottom.r - .12)));
    for (int i = 1; i < outer.size(); i++) {
      String shade = i >= rows.length ? "lip" : "glass";
      connect(item.glass, shade, outer.get(i - 1), outer.get(i), false);
      connect(item.glass, "glass", inner.get(i - 1), inner.get(i), true);
    }
    floor(item.glass, "glass", bottom, false);
    floor(item.glass, "glass", inner.getFirst(), true);
    annulus("lip", outer.getLast(), inner.getLast());
    if (size == 3) {
      Row membrane = sample(rows, 4.80).inset(.09);
      floor(item.glass, "glass", membrane, true);
      floor(item.glass, "glass", new Row(membrane.y - .065, membrane.x, membrane.r), false);
    }
    if (level > 0) {
      double low = bottom.y + .18;
      if (size == 3) {
        liquid(rows, low, low + (4.69 - low) * level / 4.0, "liquid");
        liquid(rows, 4.90, 4.90 + (top.y - .34 - 4.90) * level / 4.0, "aqua");
      } else liquid(rows, low, low + (top.y - .38 - low) * level / 4.0, "liquid");
    }
    double ny = top.y - .15, rx = top.r + .045;
    var wrap = new ArrayList<P>();
    for (int i = 0; i <= 12; i++) {
      double t = (i + .17) * Math.PI / 6;
      wrap.add(point(top.x + rx * Math.cos(t), ny + .07 * i / 12, rx * DEPTH * Math.sin(t)));
    }
    item.mesh.tube("cord", wrap, .105, 5, 3);
    double shoulder = size == 0 ? .83 : size == 2 ? 2.15 : 1.83;
    item.mesh.tube(
        "cord",
        List.of(
            point(top.x + rx, ny + .05, .045),
            point(top.x + rx + .16, ny - .15, .03),
            point(shoulder, ny - (size == 0 ? .65 : 1.03), .15),
            point(shoulder + .12, ny - (size == 0 ? 3.65 : 3.05), .27)),
        .095,
        5,
        3);
    item.mesh.beam(
        "cord", point(top.x + rx, ny + .07, .10), point(top.x + rx + .19, ny - .30, .30), .145, 5);
  }
}
