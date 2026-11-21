package dev.aeroscapes.aquatic;

import static dev.aeroscapes.aquatic.HexHeldMesh.point;

import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.util.ArrayList;
import java.util.List;

final class HexScrollLens {
  private HexScrollLens() {}

  private record Section(double y, double z, double ny, double nz) {}

  static void scroll(HexHeldMesh h, int size) {
    double half = 2.65 + size * 1.45, radius = .99 + size * .21, height = 7.5;
    var path = new ArrayList<Section>();
    int count = 18;
    for (int i = 0; i <= count; i++) {
      double t = (double) i / count, a = (1 - t) * Math.PI * 3;
      double r = radius * (.35 + .65 * t);
      path.add(new Section(height + Math.sin(a) * r, Math.cos(a) * r, Math.sin(a), Math.cos(a)));
    }
    path.add(new Section(height - radius * 1.35, radius + .20, .09, 1));
    path.add(new Section(height - radius * 2.9, radius + .16, -.07, 1));
    path.add(new Section(height - radius * 3.8, radius + .49, .32, .95));
    path.add(new Section(height - radius * 4.1, radius + .85, .75, .67));
    for (String mat : List.of("paper", "paper_edge", "inside", "ribbon", "knot"))
      h.mesh.projectedField(mat);
    strip(h, path, 0, 0, half, .18, "paper", "inside", "paper_edge");
    var tail = new ArrayList<Section>();
    for (int i = 5; i > 0; i--) {
      double a = i * Math.PI / 6;
      double r = radius * (1 - .65 * i / 18) + .098;
      tail.add(new Section(height + Math.sin(a) * r, Math.cos(a) * r, Math.sin(a), Math.cos(a)));
    }
    tail.add(new Section(height, radius + .11, .05, 1));
    tail.add(new Section(height - .47, radius + .33, .13, 1));
    tail.add(new Section(height - radius * 1.9, radius + .71, .05, 1));
    tail.add(new Section(height - radius * 3.15, radius + .65, -.10, 1));
    strip(h, tail, -half * .84 + .14, .55, .23 + size * .045, .10, "ribbon", "ribbon", "knot");
  }

  private static void strip(
      HexHeldMesh h,
      List<Section> sections,
      double x,
      double drift,
      double half,
      double thick,
      String top,
      String bottom,
      String edge) {
    P[] previous = null;
    for (int i = 0; i < sections.size(); i++) {
      Section s = sections.get(i);
      P[] row = new P[4];
      for (int k = 0; k < 4; k++) {
        double offset = (k < 2 ? 1 : -1) * thick * .5;
        row[k] =
            point(
                x
                    + drift * Math.pow((double) i / (sections.size() - 1), 1.65)
                    + (k == 0 || k == 3 ? -half : half),
                s.y + offset * s.ny,
                s.z + offset * s.nz);
      }
      if (previous != null) {
        for (int k = 0; k < 4; k++) {
          int next = (k + 1) % 4;
          P n =
              switch (k) {
                case 0 -> new P(0, s.ny, s.nz);
                case 2 -> new P(0, -s.ny, -s.nz);
                default -> new P(k == 1 ? 1 : -1, 0, 0);
              };
          h.mesh.quad(
              k == 0 ? top : k == 2 ? bottom : edge,
              new P[] {previous[k], previous[next], row[next], row[k]},
              n,
              null);
        }
      }
      if ((i == 0 || i == sections.size() - 1)) {
        Section adj = sections.get(i == 0 ? 1 : i - 1);
        h.mesh.quad(edge, row, new P(0, s.y - adj.y, s.z - adj.z), null);
      }
      previous = row;
    }
  }

  static void lens(HexHeldMesh h) {
    double[] rr = {.18, .15, -.04, -.24, -.34};
    double[] widths = {.70, .82, 1, .93, .78};
    int sides = 8;
    P[] previous = null;
    for (int r = 0; r < rr.length; r++) {
      P[] row = new P[sides];
      for (int i = 0; i < sides; i++) {
        double a = (i + .5) * 2 * Math.PI / sides;
        row[i] = point(Math.cos(a) * widths[r] * 2.25, 7.4 + Math.sin(a) * widths[r] * 2.45, rr[r]);
      }
      if (previous != null)
        for (int i = 0; i < sides; i++) {
          int j = (i + 1) % sides;
          P[] face = {previous[i], previous[j], row[j], row[i]};
          boolean edge = r >= 2 && r <= 3;
          P out = row[i].add(point(0, 7.4, 0).scale(-1));
          if (!edge) out = out.add(new P(0, 0, r <= rr.length / 2 ? 3 : -3));
          lensFace(h, edge ? "lens_edge" : "coating", face, out);
        }
      if (r == 0 || r == rr.length - 1) {
        P center = point(0, 7.4, rr[r]);
        for (int i = 0; i < sides; i++) {
          P next = row[(i + 1) % sides];
          lensFace(
              h, "coating", new P[] {center, row[i], next, next}, new P(0, 0, r == 0 ? 1 : -1));
        }
      }
      previous = row;
    }
  }

  private static void lensFace(HexHeldMesh h, String material, P[] points, P outward) {
    double[][] uv = new double[4][2];
    for (int i = 0; i < 4; i++) {
      uv[i][0] = .5 + (points[i].x() - 8) / 5.1;
      uv[i][1] = .5 - (points[i].y() - 7.4) / 5.1;
    }
    if (material.equals("lens_edge")) h.mesh.projectedField(material);
    h.mesh.quad(material, points, outward, uv);
  }

  static int size(String root) {
    return switch (root) {
      case "scroll_small" -> 0;
      case "scroll_medium" -> 1;
      case "scroll" -> 2;
      default -> throw new IllegalArgumentException("Unknown Hex scroll " + root);
    };
  }
}
