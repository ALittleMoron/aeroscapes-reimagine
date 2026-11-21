package dev.aeroscapes.aquatic;

import static dev.aeroscapes.aquatic.HexHeldMesh.point;

import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** Faceted stones and fitted settings for Hex’s four families of portable jewelry. */
final class HexCarriers {
  private final HexHeldMesh item;

  HexCarriers(HexHeldMesh item) {
    this.item = item;
  }

  private static final String[] GEM = {
    "gem", "gem", "gem_shadow", "gem_shadow", "gem", "gem_flash"
  };

  void triangle(String mat, P a, P b, P c, P outward) {
    item.mesh.quad(
        mat, new P[] {a, b, c, c}, outward, new double[][] {{0, 0}, {1, 0}, {.5, 1}, {.5, 1}});
  }

  void face(String mat, P a, P b, P c, P d, P outward) {
    triangle(mat, a, b, c, outward);
    triangle(mat, a, c, d, outward);
  }

  // Six cleavage faces run through a prism. Short end cuts leave both terminals visible.
  void crystal(
      double x,
      double y,
      double z,
      double angle,
      double length,
      double radius,
      double thick,
      boolean cut) {
    double a = Math.toRadians(angle), cos = Math.cos(a), sin = Math.sin(a);
    double[] axes = cut ? new double[] {-.985, -.58, .48, .96} : new double[] {-.98, -.68, .34, 1};
    double[] radii = cut ? new double[] {.1, .87, 1, .08} : new double[] {.07, 1, .93, .04};
    P[][] rings = new P[4][6];
    P centre = point(x, y, z);
    for (int k = 0; k < 4; k++)
      for (int i = 0; i < 6; i++) {
        double r = radii[k], t = (i + .25) * Math.PI / 3, along = length * .5 * axes[k];
        double across = radius * r * Math.cos(t) + (k == 3 ? .09 : k == 0 ? -.09 : 0) * radius;
        rings[k][i] =
            point(
                x + along * cos - across * sin,
                y + along * sin + across * cos,
                z + r * thick * Math.sin(t));
      }
    for (int k = 0; k < 3; k++)
      for (int i = 0; i < 6; i++) {
        int n = (i + 1) % 6;
        P[] p = {rings[k][i], rings[k][n], rings[k + 1][n], rings[k + 1][i]};
        P out = p[0].add(p[1]).add(p[2]).add(p[3]).scale(.25).add(centre.scale(-1));
        String mat =
            k == 0
                ? (i == 0 ? "gem_flash" : "gem_shadow")
                : k == 2 ? (i == 0 ? "gem_flash" : i == 3 ? "gem_shadow" : "gem") : GEM[i];
        face(mat, p[0], p[1], p[2], p[3], out);
      }
    for (int k : new int[] {0, 3})
      for (int i = 1; i < 5; i++)
        triangle(
            "gem_shadow",
            rings[k][0],
            rings[k][i],
            rings[k][i + 1],
            new P(cos, sin, 0).scale(k == 0 ? -1 : 1));
  }

  // A continuous broad strap clasped about the stone, with square cut edges.
  void strap(double x, double y, double radius, double depth, double width, double skew) {
    P[][] p = new P[4][8];
    double[] ax = {-width / 2, -width / 2, width / 2, width / 2};
    double[] r = {.77, 1, 1, .77};
    for (int i = 0; i < 8; i++)
      for (int s = 0; s < 4; s++) {
        double a = (i + .5) * Math.PI / 4,
            dy = Math.cos(a) * radius * r[s],
            dz = Math.sin(a) * depth * r[s];
        p[s][i] = point(x + dy * skew + ax[s], y + dy, dz);
      }
    for (int i = 0; i < 8; i++)
      for (int s = 0; s < 4; s++) {
        int n = (i + 1) % 8, t = (s + 1) % 4;
        P mid = p[s][i].add(p[t][n]).scale(.5).add(point(x, y, 0).scale(-1));
        P out =
            s == 0
                ? new P(-1, skew, 0)
                : s == 2 ? new P(1, -skew, 0) : new P(0, mid.y(), mid.z()).scale(s == 3 ? -1 : 1);
        face(
            s == 1 ? (i == 0 || i == 6 ? "leather_light" : "leather") : "leather_edge",
            p[s][i],
            p[s][n],
            p[t][n],
            p[t][i],
            out);
      }
  }

  void tube(String m, double r, double[][] path) {
    item.mesh.tube(m, Arrays.stream(path).map(v -> point(v[0], v[1], v[2])).toList(), r, 6, 4);
  }

  void ring(
      String m,
      double x,
      double y,
      double rx,
      double ry,
      double wire,
      double depth,
      double tilt,
      boolean sculpted,
      double zOffset) {
    int n = sculpted ? 10 : 12;
    P[][] p = new P[n][8];
    double t = Math.toRadians(tilt);
    for (int i = 0; i < n; i++)
      for (int j = 0; j < 8; j++) {
        double a = i * 2 * Math.PI / n,
            b = (j + .5) * Math.PI / 4,
            bulge = sculpted ? 1 + .9 * Math.max(0, Math.sin(a)) : 1;
        double u = (rx + wire * bulge * Math.cos(b)) * Math.cos(a),
            v = (ry + wire * bulge * Math.cos(b)) * Math.sin(a);
        p[i][j] =
            point(
                x + u * Math.cos(t) - v * Math.sin(t),
                y + u * Math.sin(t) + v * Math.cos(t),
                zOffset + depth * (sculpted ? .85 + .15 * bulge : 1) * Math.sin(b));
      }
    for (int i = 0; i < n; i++)
      for (int j = 0; j < 8; j++) {
        int ni = (i + 1) % n, nj = (j + 1) % 8;
        double a = (i + .5) * 2 * Math.PI / n, b = (j + 1) * Math.PI / 4;
        P out = new P(Math.cos(a + t) * Math.cos(b), Math.sin(a + t) * Math.cos(b), Math.sin(b));
        face(m, p[i][j], p[ni][j], p[ni][nj], p[i][nj], out);
      }
  }

  void jewel(
      double x, double y, double z, double rx, double ry, double depth, double tilt, boolean rose) {
    int n = rose ? 7 : 8;
    P[][] p = new P[3][n];
    double t = Math.toRadians(tilt);
    for (int i = 0; i < n; i++)
      for (int k = 0; k < 3; k++) {
        double a = 2 * Math.PI * (i + (k == 2 && rose ? .4 : 0)) / n,
            r = k == 0 ? .44 : k == 1 ? 1 : rose ? .2 : .62;
        double u = rx * r * Math.cos(a), v = ry * r * Math.sin(a);
        p[k][i] =
            point(
                x + u * Math.cos(t) - v * Math.sin(t),
                y + u * Math.sin(t) + v * Math.cos(t),
                z + depth * (k == 0 ? -.8 : k == 1 ? -.05 : .7));
      }
    for (int k = 0; k < 2; k++)
      for (int i = 0; i < n; i++) {
        int n1 = (i + 1) % n;
        double a = (i + .5) * 2 * Math.PI / n + t;
        P out = new P(Math.cos(a), Math.sin(a), k == 0 ? -1 : 1);
        String m =
            k == 0 ? "gem_shadow" : i == 2 ? "gem_flash" : i == 4 || i == 6 ? "gem_shadow" : "gem";
        triangle(m, p[k][i], p[k][n1], p[k + 1][n1], out);
        triangle(
            k == 1 && rose && i == 1 ? "gem_flash" : m, p[k][i], p[k + 1][n1], p[k + 1][i], out);
      }
    for (int k : new int[] {0, 2})
      for (int i = 1; i < n - 1; i++)
        triangle(
            k == 0 ? "gem_shadow" : "gem",
            p[k][0],
            p[k][i],
            p[k][i + 1],
            new P(0, 0, k == 0 ? -1 : 1));
  }

  P build(int form, String root, boolean sealed, BufferedImage base, BufferedImage overlay) {
    String setting = root.equals("focus") ? "leather" : "metal";
    switch (form) {
      case 0 -> shard(root, setting);
      case 1 -> shellCharm(root, setting);
      case 2 -> spindle(root, setting);
      case 3 -> tablet(root, setting);
      case 4 -> linkedStones(root, setting);
      case 5 -> pin(root, setting);
      case 6 -> fingerRing(root, setting);
      case 7 -> crest(root, setting);
      default -> throw new IllegalArgumentException("Unknown carrier form " + form);
    }
    P grip = HexPocketGrip.contact(item.mesh);
    if (sealed) wax(form);
    else if (overlay != null) glint(base, overlay, root.equals("focus") ? "rune" : "glint");
    return grip;
  }

  private void shard(String root, String mount) {
    switch (root) {
      case "focus" -> {
        jewel(0, 5.15, 0, 2.7, 1.55, 1.38, -9, true);
        tube(
            mount,
            .41,
            new double[][] {
              {-1.62, 5.86, -.18},
              {-1.85, 5.06, -.49},
              {-1.58, 4.15, -.16},
              {-.75, 3.8, .16},
              {.72, 3.76, .08},
              {1.55, 4.35, -.23},
              {1.75, 5.3, -.3}
            });
        strap(-1, 5.18, 1.6, 1.1, .62, .32);
        strap(1.04, 5.04, 1.6, 1.07, .64, -.3);
      }
      case "cypher" -> {
        jewel(.02, 5.04, 0, 1.38, 2.68, 1.27, -9, true);
        tube(
            mount,
            .30,
            new double[][] {
              {-.8, 6.16, .57},
              {-1.3, 5.84, .19},
              {-1.42, 4.80, -.04},
              {-.87, 3.65, -.08},
              {.34, 3.19, -.21},
              {.95, 3.57, .06},
              {1.13, 4.27, .49},
              {.79, 4.72, .64}
            });
        tube(
            mount,
            .25,
            new double[][] {
              {.28, 3.24, -.2},
              {.63, 3.63, -.65},
              {.72, 5.05, -.69},
              {.54, 5.76, -.47},
              {.63, 6.12, .12},
              {.42, 6.1, .44}
            });
      }
      case "trinket" -> {
        crystal(-.38, 5.22, .05, 98, 5.37, 1, .77, true);
        crystal(.47, 4.51, -.12, 71, 3.4, .69, .64, false);
        tube(
            mount,
            .32,
            new double[][] {
              {-.8, 3.65, .27},
              {-.89, 4.02, .57},
              {-.49, 4.50, .72},
              {.86, 5.30, .51},
              {1.12, 5.64, .12},
              {.86, 5.81, -.34},
              {.24, 5.74, -.60},
              {-.4, 5.34, -.55}
            });
      }
      default -> {
        jewel(0, 5.04, .06, 1.14, 2.34, 1.17, 0, true);
        for (int s : new int[] {-1, 1})
          for (int t : new int[] {-1, 1})
            tube(
                mount,
                .24,
                new double[][] {
                  {s * .34, 5.05 + t * .20, -.30},
                  {s * 1.33, 5.05 + t * .35, -.16},
                  {s * 1.95, 5.05 + t * 1.22, .01},
                  {s * 1.88, 5.05 + t * 2.00, .12},
                  {s * 1.22, 5.05 + t * 2.18, .19},
                  {s * .60, 5.05 + t * 1.21, .39}
                });
      }
    }
  }

  private void shellCharm(String root, String mount) {
    boolean gold = root.equals("artifact"),
        focus = root.equals("focus"),
        iron = root.equals("trinket");
    jewel(-.12, 4.72, -.14, gold ? 1.90 : 1.76, 1.57, .86, -25, true);
    crystal(.32, 4.56, .35, 25, gold ? 3.65 : 3.14, .68, .60, true);
    crystal(-.16, 5.33, .31, iron ? 102 : 71, 2.8, .84, .63, false);
    if (root.equals("cypher")) {
      tube(
          mount,
          .31,
          new double[][] {
            {.55, 4.98, .31},
            {.78, 5.83, .10},
            {.66, 6.70, -.06},
            {.09, 7.23, -.11},
            {-.78, 7.18, -.08},
            {-1.33, 6.58, -.09},
            {-1.27, 6.13, .11}
          });
    } else if (focus) {
      tube(
          mount,
          .40,
          new double[][] {
            {-1.34, 3.8, .12},
            {-1.56, 3.57, .09},
            {-1.43, 3.19, -.12},
            {-.80, 3.43, -.22},
            {.34, 4.55, .78},
            {.76, 5.55, .29},
            {.56, 6.10, -.15},
            {-.53, 6.56, -.11},
            {-1.09, 6.38, .05}
          });
    } else {
      tube(
          mount,
          iron ? .30 : .39,
          new double[][] {
            {-1.24, 5.60, -.29},
            {-1.64, 5.08, -.10},
            {-1.52, 4.61, .30},
            {-.76, 4.29, .49},
            {.91, 4.94, .74},
            {1.61, 5.55, .21},
            {1.62, 6.25, -.01},
            {1.34, 6.63, -.17}
          });
      if (gold)
        for (int s : new int[] {-1, 1})
          tube(
              mount,
              .27,
              new double[][] {
                {s * 1.35, 5.42, -.31},
                {s * 2.08, 5.63, -.08},
                {s * 2.20, 6.27, .06},
                {s * 1.64, 6.48, .15}
              });
    }
  }

  private void spindle(String root, String mount) {
    switch (root) {
      case "cypher" -> {
        jewel(0, 5.0, 0, 1.30, .93, .72, -38, false);
        tube(
            mount,
            .22,
            new double[][] {
              {-1.06, 5.26, .31},
              {-1.00, 4.78, -.03},
              {-.56, 4.36, -.11},
              {.47, 4.24, .03},
              {.78, 4.47, .26}
            });
      }
      case "focus" -> {
        jewel(0, 5.08, 0, 1.55, 1.38, 1.05, 34, true);
        strap(.30, 5.07, 1.40, .94, .61, -.40);
      }
      case "trinket" -> {
        crystal(0, 5.06, 0, -47, 5.03, .95, .71, true);
        tube(
            mount,
            .23,
            new double[][] {
              {-.89, 5.72, .28},
              {-.91, 5.21, .49},
              {-.50, 4.86, .65},
              {.39, 4.72, .61},
              {.68, 5.01, .18},
              {.45, 5.44, -.48},
              {.04, 5.67, -.49}
            });
      }
      default -> {
        jewel(0, 5, .06, 1.42, 1.28, .81, -33, true);
        for (int s : new int[] {-1, 1})
          for (int t : new int[] {-1, 1}) {
            jewel(s * 1.13, 5 + t * 1.14, -.16, .70, .69, .56, 19 * s * t, false);
            tube(
                mount,
                .22,
                new double[][] {
                  {s * .51, 5 + t * .59, -.24},
                  {s * .86, 5 + t * 1.63, -.25},
                  {s * 1.56, 5 + t * 1.68, -.01},
                  {s * 1.80, 5 + t * 1.23, .10},
                  {s * 1.48, 5 + t * .73, .24}
                });
          }
      }
    }
  }

  private void tablet(String root, String mount) {
    if (root.equals("focus")) {
      jewel(0, 5.14, .04, 1.45, 1.15, .78, 6, true);
      item.box(mount, 0, 3.61, 0, 3.25, .45, 2.32);
      tube(
          mount,
          .29,
          new double[][] {
            {-1.39, 5.64, -.32},
            {-1.60, 5.03, -.18},
            {-1.31, 4.42, .67},
            {-.23, 4.20, .89},
            {1.12, 4.44, .58},
            {1.64, 4.88, .12}
          });
      return;
    }
    boolean iron = root.equals("trinket"), gold = root.equals("artifact");
    item.box("gem", 0, 3.56, 0, 2.76, 3.0, 2.32);
    jewel(.02, 5.18, .65, 1.40, 1.30, 1.14, 25, true);
    item.box(mount, -1.41, 3.35, .05, iron ? .43 : .64, 3.48, 2.88);
    item.box(mount, 1.40, 3.52, -.10, gold ? .79 : .50, 3.07, 2.60);
    if (iron) {
      item.box(mount, .39, 3.61, 1.20, 2.38, 1.25, .45);
      item.box(mount, -.36, 6.27, -.92, 2.18, .46, .82);
    } else if (gold) {
      item.box(mount, .21, 3.28, 1.16, .83, 2.31, .50);
      item.box(mount, -.30, 6.41, -.57, 1.40, .53, 1.30);
    } else {
      item.box(mount, -.40, 6.37, -.99, 2.33, .40, .68);
      item.box(mount, .38, 3.31, .61, 2.14, .41, 1.69);
    }
  }

  private void linkedStones(String root, String mount) {
    if (root.equals("artifact")) {
      for (int i = 0; i < 3; i++) {
        double a = Math.toRadians(90 + 120 * i), x = .91 * Math.cos(a), y = 5 + .90 * Math.sin(a);
        ring("gem", x, y, 1.01, .88, .32, .37, 15 + i * 38, true, (i - 1) * .17);
        tube(
            mount,
            .23,
            new double[][] {
              {x * 1.40, y + (y - 5) * .42, .13},
              {x * 1.72, y + (y - 5) * .71, .04},
              {x * 1.84 + .21, y + (y - 5) * .90, -.09}
            });
      }
    } else if (root.equals("trinket")) {
      ring("gem", -.58, 5.49, .91, 1.09, .32, .39, -34, false, -.13);
      ring("gem", .65, 4.62, 1.01, .93, .36, .46, -37, true, .12);
      tube(
          mount,
          .24,
          new double[][] {
            {-.80, 4.91, .33},
            {-.32, 5.00, .42},
            {.35, 4.98, .47},
            {.84, 5.37, .24},
            {1.32, 5.99, -.08}
          });
    } else {
      ring("gem", 0, 5.1, 1.44, 1.35, .42, .53, -35, true, 0);
      if (root.equals("focus"))
        tube(
            mount,
            .26,
            new double[][] {
              {-1.24, 4.02, .08},
              {-.95, 4.11, .39},
              {-.14, 4.87, .44},
              {.96, 5.93, .48},
              {1.24, 6.10, .11},
              {1.25, 5.82, -.31}
            });
      else
        for (int s : new int[] {-1, 1})
          tube(
              mount,
              .31,
              new double[][] {
                {s * .83, 5.1 - s * .97, .13},
                {s * 1.15, 5.1 - s * 1.25, .16},
                {s * 1.50, 5.1 - s * 1.42, -.03}
              });
    }
  }

  private void pin(String root, String mount) {
    boolean gold = root.equals("artifact"), focus = root.equals("focus");
    double tip = focus ? 1.73 : 2.06;
    crystal(.02, 5.09, 0, 51, focus ? 3.2 : 4.81, focus ? .60 : .27, focus ? .49 : .26, true);
    tube(
        mount,
        focus ? .25 : .19,
        new double[][] {{-tip, 2.66, .04}, {-1.21, 3.60, 0}, {-.24, 4.79, -.07}});
    tube(
        mount,
        focus ? .22 : .20,
        new double[][] {{.38, 5.55, -.10}, {1.43, 6.83, .01}, {tip, 7.35, .07}});
    if (root.equals("trinket") || gold) {
      double r = gold ? .75 : .43;
      item.tapered(mount, tip, .06, 7.13, r, 7.11 + r * 1.6, r * .73, .88);
    }
    if (focus)
      tube(
          mount,
          .26,
          new double[][] {
            {-.55, 4.37, .12},
            {-.71, 4.88, .33},
            {-.27, 5.24, .40},
            {.70, 5.35, .16},
            {.82, 5.78, -.16},
            {.52, 6.04, -.21}
          });
  }

  private void fingerRing(String root, String mount) {
    if (root.equals("artifact")) {
      tube(
          mount,
          .33,
          new double[][] {
            {-.64, 5.80, .04},
            {-.88, 5.68, -.22},
            {-1.40, 5.29, -.16},
            {-1.54, 4.57, -.09},
            {-1.23, 3.72, -.06},
            {-.68, 3.28, 0},
            {.12, 3.12, .03},
            {1.00, 3.46, .08},
            {1.56, 4.12, .13},
            {1.72, 4.91, .14},
            {1.51, 5.56, .1},
            {1.01, 6.07, .07},
            {.46, 6.30, .13},
            {-.19, 6.28, .17},
            {-.45, 6.09, .26}
          });
      tube(
          mount,
          .20,
          new double[][] {
            {-1.30, 3.87, .04}, {-1.65, 4.22, .18}, {-1.76, 4.78, .28}, {-1.55, 5.12, .23}
          });
      jewel(-1.61, 4.99, .30, .44, .39, .32, 14, false);
      jewel(-.60, 5.98, .3, .92, .69, .65, -24, true);
      tube(
          mount,
          .19,
          new double[][] {
            {-1.30, 5.52, -.13},
            {-1.34, 5.96, .13},
            {-1.18, 6.46, .25},
            {-.73, 6.63, .3},
            {-.46, 6.36, .51}
          });
      tube(
          mount,
          .17,
          new double[][] {{.24, 6.11, -.12}, {.29, 5.9, .19}, {.01, 5.63, .44}, {-.24, 5.66, .52}});
    } else {
      boolean focus = root.equals("focus"), iron = root.equals("trinket");
      ring(
          mount,
          .05,
          4.85,
          iron ? 1.38 : 1.46,
          1.45,
          focus ? .38 : .31,
          iron ? .42 : .32,
          27,
          iron,
          0);
      jewel(-.65, 6.13, .09, iron ? .79 : .62, .64, .47, 18, false);
      if (focus) {
        jewel(-1.35, 4.86, .12, .43, .37, .31, 31, true);
        jewel(.16, 6.31, .08, .34, .36, .28, 14, false);
      }
    }
  }

  private void crest(String root, String mount) {
    if (root.equals("trinket")) {
      for (int s : new int[] {-1, 1}) {
        crystal(s * .92, 4.51, .11, s * 49, 2.82, .76, .55, true);
        tube(
            mount,
            .22,
            new double[][] {
              {s * .15, 5.69, -.03},
              {s * .71, 5.42, .24},
              {s * 1.11, 5.19, .22},
              {s * 1.31, 4.94, -.14}
            });
      }
      jewel(0, 5.95, .05, .77, 1.31, .78, 2, true);
    } else if (root.equals("artifact")) {
      jewel(0, 5.13, .04, 1.26, 2.27, .96, -5, true);
      crystal(.06, 4.96, .21, 4, 4.09, .58, .49, true);
      for (int s : new int[] {-1, 1})
        tube(
            mount,
            .28,
            new double[][] {
              {s * .37, 5.76, -.28},
              {s * 1.13, 6.55, -.16},
              {s * 1.46, 6.66, .05},
              {s * 1.29, 5.80, .17},
              {s * .39, 4.04, .42},
              {s * .66, 3.31, .19}
            });
    } else {
      jewel(0, 5.06, 0, 1.00, 2.45, .97, 0, true);
      for (int s : new int[] {-1, 1})
        tube(
            mount,
            .26,
            new double[][] {
              {s * .22, 5.33, -.36},
              {s * 1.11, 5.44, -.24},
              {s * 1.48, 5.14, .02},
              {s * 1.21, 4.88, .20},
              {s * .66, 4.94, .38}
            });
      if (root.equals("focus"))
        tube(
            mount,
            .27,
            new double[][] {
              {-.77, 4.35, -.08},
              {-.98, 4.19, .08},
              {-.88, 3.95, .27},
              {.57, 3.85, .38},
              {.85, 4.20, .11}
            });
    }
  }

  private void wax(int form) {
    double[][] marks = {
      {-.94, 5.05, .66, .56, 1.02},
      {-.70, 4.42, .50, .61, .78},
      {.39, 5.09, .61, .72, .76},
      {.06, 4.31, .69, 1.03, .33},
      {.65, 5.31, .42, .34, .78},
      {-.10, 5.10, .34, .50, .56},
      {-.67, 6.15, .36, .44, .35},
      {-.07, 4.21, .37, .77, .37}
    };
    var w = marks[form];
    P[] border = new P[7];
    P center = point(w[0], w[1], w[2] + .28);
    for (int i = 0; i < 7; i++) {
      double a = (i + .17) * 2 * Math.PI / 7;
      double r = i == 2 ? .78 : i == 5 ? 1.17 : 1;
      border[i] = point(w[0] + w[3] * r * Math.cos(a), w[1] + w[4] * r * Math.sin(a), w[2] - .03);
    }
    P back = point(w[0], w[1], w[2] - .36);
    for (int i = 0; i < 7; i++) {
      int n = (i + 1) % 7;
      triangle("rune", center, border[i], border[n], new P(0, 0, 1));
      triangle("rune", back, border[i], border[n], new P(0, 0, -1));
    }
  }

  private void glint(BufferedImage base, BufferedImage overlay, String material) {
    var nativeBounds = bounds(base);
    var stars = bounds(overlay);
    var x = new java.util.DoubleSummaryStatistics();
    var y = new java.util.DoubleSummaryStatistics();
    var z = new java.util.DoubleSummaryStatistics();
    item.mesh
        .obj()
        .lines()
        .filter(line -> line.startsWith("v "))
        .forEach(
            line -> {
              String[] f = line.split(" ");
              x.accept(Double.parseDouble(f[1]) * 16 - 8);
              y.accept(Double.parseDouble(f[2]) * 16);
              z.accept(Double.parseDouble(f[3]) * 16 - 8);
            });
    double left =
        x.getMin()
            + (stars[0] - nativeBounds[0])
                * (x.getMax() - x.getMin())
                / (nativeBounds[2] - nativeBounds[0]);
    double right =
        x.getMin()
            + (stars[2] - nativeBounds[0])
                * (x.getMax() - x.getMin())
                / (nativeBounds[2] - nativeBounds[0]);
    double top =
        y.getMax()
            - (stars[1] - nativeBounds[1])
                * (y.getMax() - y.getMin())
                / (nativeBounds[3] - nativeBounds[1]);
    double bottom =
        y.getMax()
            - (stars[3] - nativeBounds[1])
                * (y.getMax() - y.getMin())
                / (nativeBounds[3] - nativeBounds[1]);
    // A sparkle below the inventory silhouette disappears behind the fingers.
    // Move it to the exposed shoulder, retaining its native sprite, side and size.
    double lift = Math.max(0, y.getMin() + .66 * (y.getMax() - y.getMin()) - (bottom + top) * .5);
    bottom += lift;
    top += lift;
    double u = stars[0] / (double) overlay.getWidth(),
        v = stars[1] / (double) overlay.getHeight(),
        U = stars[2] / (double) overlay.getWidth(),
        V = stars[3] / (double) overlay.getHeight();
    for (int side : new int[] {-1, 1}) {
      double depth = side > 0 ? z.getMax() + .04 : z.getMin() - .04;
      item.mesh.quad(
          material,
          new P[] {
            point(left, bottom, depth),
            point(right, bottom, depth),
            point(right, top, depth),
            point(left, top, depth)
          },
          new P(0, 0, side),
          new double[][] {{u, V}, {U, V}, {U, v}, {u, v}});
    }
  }

  private static int[] bounds(BufferedImage im) {
    int[] b = {im.getWidth(), im.getHeight(), 0, 0};
    for (int y = 0; y < im.getHeight(); y++)
      for (int x = 0; x < im.getWidth(); x++)
        if ((im.getRGB(x, y) >>> 24) > 0) {
          b[0] = Math.min(b[0], x);
          b[1] = Math.min(b[1], y);
          b[2] = Math.max(b[2], x + 1);
          b[3] = Math.max(b[3], y + 1);
        }
    return b;
  }
}
