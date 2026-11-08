package dev.aeroscapes.aquatic;

import dev.aeroscapes.aquatic.EquipmentObjMesh.P;

/** Ribbed hollow valves and tapered coiled shells retain the native shell families. */
final class ShellToolMeshes {
  private ShellToolMeshes() {}

  static void axe(EquipmentObjMesh mesh) {
    fanValve(mesh, new ValveShape(-80, .82, .88, .72, new P(8, 17.5, 8.45)));
    mesh.group("lateral_shell_hinge_saddle");
    mesh.box("socket", 6.65, 16.2, 6.9, 9.35, 18, 9.1);
  }

  static void shovel(EquipmentObjMesh mesh) {
    fanValve(mesh, new ValveShape(0, 1, 1, 1, new P(8, 17.1, 8.45)));
    mesh.group("shell_hinge_saddle");
    mesh.box("socket", 6.7, 16.35, 6.9, 9.3, 17.9, 9.1);
  }

  private static void fanValve(EquipmentObjMesh mesh, ValveShape shape) {
    double[] rimX = {-1.9, -3.7, -4.6, -3.9, -2.1, 0, 2.1, 3.9, 4.6, 3.7, 1.9};
    double[] rimY = {.75, 2.5, 4.7, 6.9, 8.1, 8.5, 8.1, 6.9, 4.7, 2.5, .75};
    double[] growth = {0, .48, 1};
    double[] dome = {0, 1.9, 0};
    P[][] outside = new P[growth.length][rimX.length];
    P[][] inside = new P[growth.length][rimX.length];
    for (int ring = 0; ring < growth.length; ring++)
      for (int spoke = 0; spoke < rimX.length; spoke++) {
        double fraction = growth[ring];
        double fold = spoke % 2 == 1 ? .4 * fraction : 0;
        outside[ring][spoke] =
            new P(
                8 + rimX[spoke] * fraction,
                17.1 + rimY[spoke] * fraction,
                8.45 + dome[ring] + fold);
        inside[ring][spoke] = outside[ring][spoke].add(new P(0, 0, -.55));
      }
    mesh.group("fan_valve_with_radial_folds");
    for (int ring = 0; ring < growth.length - 1; ring++)
      for (int spoke = 0; spoke < rimX.length - 1; spoke++) {
        fanSurface(
            mesh,
            shape,
            "head",
            new P[] {
              outside[ring][spoke],
              outside[ring][spoke + 1],
              outside[ring + 1][spoke + 1],
              outside[ring + 1][spoke]
            },
            new P(0, 0, 1));
        fanSurface(
            mesh,
            shape,
            "inner",
            new P[] {
              inside[ring][spoke],
              inside[ring][spoke + 1],
              inside[ring + 1][spoke + 1],
              inside[ring + 1][spoke]
            },
            new P(0, 0, -1));
      }
    mesh.group("continuous_thin_shell_margin");
    int edge = growth.length - 1;
    for (int spoke = 0; spoke < rimX.length - 1; spoke++)
      valveSurface(
          mesh,
          shape,
          "edge",
          new P[] {
            outside[edge][spoke],
            outside[edge][spoke + 1],
            inside[edge][spoke + 1],
            inside[edge][spoke]
          },
          new P((rimX[spoke] + rimX[spoke + 1]) / 2, (rimY[spoke] + rimY[spoke + 1]) / 2 - 4.2, 0),
          new double[][] {{0, 0}, {.55, 0}, {.55, .14}, {0, .14}});
    for (int spoke : new int[] {0, rimX.length - 1})
      for (int ring = 0; ring < growth.length - 1; ring++)
        valveSurface(
            mesh,
            shape,
            "edge",
            new P[] {
              outside[ring][spoke],
              outside[ring + 1][spoke],
              inside[ring + 1][spoke],
              inside[ring][spoke]
            },
            new P(spoke == 0 ? -1 : 1, -1, 0),
            new double[][] {{0, 0}, {.5, 0}, {.5, .14}, {0, .14}});
    mesh.group("straight_hinge_with_auricles");
    for (int side : new int[] {-1, 1}) {
      P[] outline = {
        new P(8 + side * .7, 17.05, 8.4),
        new P(8 + side * 2.55, 17.05, 8.4),
        new P(8 + side * 2.3, 18.15, 8.4),
        new P(8 + side * .5, 18.55, 8.4)
      };
      for (int face : new int[] {-1, 1}) {
        P[] points = new P[4];
        for (int point = 0; point < points.length; point++)
          points[point] = outline[point].add(new P(0, 0, face * .3));
        fanSurface(mesh, shape, face == 1 ? "head" : "inner", points, new P(0, 0, face));
      }
      for (int point = 0; point < outline.length; point++) {
        P a = outline[point], b = outline[(point + 1) % outline.length];
        valveSurface(
            mesh,
            shape,
            "edge",
            new P[] {
              a.add(new P(0, 0, -.3)),
              b.add(new P(0, 0, -.3)),
              b.add(new P(0, 0, .3)),
              a.add(new P(0, 0, .3))
            },
            new P(b.y() - a.y(), a.x() - b.x(), 0).scale(side),
            new double[][] {{0, 0}, {.5, 0}, {.5, .15}, {0, .15}});
      }
    }
  }

  private static void fanSurface(
      EquipmentObjMesh mesh, ValveShape shape, String material, P[] points, P outward) {
    double[][] uv = new double[points.length][2];
    for (int point = 0; point < points.length; point++)
      uv[point] = new double[] {(points[point].x() - 3.4) / 9.2, (25.6 - points[point].y()) / 8.55};
    valveSurface(mesh, shape, material, points, outward, uv);
  }

  private static void valveSurface(
      EquipmentObjMesh mesh,
      ValveShape shape,
      String material,
      P[] points,
      P outward,
      double[][] uv) {
    P[] transformed = new P[points.length];
    for (int point = 0; point < points.length; point++)
      transformed[point] = shape.point(points[point]);
    texturedSurface(mesh, material, transformed, shape.normal(outward), uv);
  }

  private record ValveShape(double rotation, double width, double reach, double depth, P origin) {
    private P point(P point) {
      return place(
          new P((point.x() - 8) * width, (point.y() - 17.1) * reach, (point.z() - 8.45) * depth),
          origin,
          rotation);
    }

    private P normal(P normal) {
      return place(
          new P(normal.x() / width, normal.y() / reach, normal.z() / depth),
          new P(0, 0, 0),
          rotation);
    }
  }

  static void spear(EquipmentObjMesh mesh) {
    mesh.group("shell_base_sleeve");
    mesh.box("socket", 6.85, 20.6, 6.85, 9.15, 22.05, 9.15);
    tubularShell(
        mesh,
        new ShellShape(
            new P(8, 21.25, 8),
            new P(0, 1, 0),
            new P(0, 0, 1),
            new P(1, 0, 0),
            new P(-.2, 0, 0),
            10.8,
            .8));
  }

  private static void texturedSurface(
      EquipmentObjMesh mesh, String material, P[] points, P outward, double[][] uv) {
    for (int[] triangle : new int[][] {{0, 1, 2}, {0, 2, 3}}) {
      int a = triangle[0], b = triangle[1], c = triangle[2];
      if (points[b]
              .vector()
              .sub(points[a].vector())
              .cross(points[c].vector().sub(points[a].vector()))
              .lengthSquared()
          < .0000001) continue;
      mesh.quad(
          material,
          new P[] {points[a], points[b], points[c], points[c]},
          outward,
          new double[][] {uv[a], uv[b], uv[c], uv[c]});
    }
  }

  static void hoe(EquipmentObjMesh mesh) {
    mesh.group("seated_shell_saddle");
    mesh.box("socket", 6.7, 15.9, 6.7, 9.3, 17.55, 9.3);
    tubularShell(
        mesh,
        new ShellShape(
            new P(8, 17.8, 7.9),
            new P(0, 0, 1),
            new P(1, 0, 0),
            new P(0, 1, 0),
            new P(0, -1.8, 0),
            9.5,
            .85));
  }

  static void pickaxe(EquipmentObjMesh mesh) {
    mesh.group("seated_shell_saddle");
    mesh.box("socket", 6.7, 15.9, 6.7, 9.3, 17.55, 9.3);

    tubularShell(
        mesh,
        new ShellShape(
            new P(10.8, 18.6, 8),
            new P(-1, 0, 0),
            new P(0, 0, 1),
            new P(0, 1, 0),
            new P(0, -2.15, 0),
            12.1,
            1));

    mesh.group("opposed_pointed_siphonal_extension");
    P[] centers = {
      new P(10.2, 17.5, 8), new P(12.1, 17.35, 8), new P(14.2, 16.8, 8), new P(16, 16, 8)
    };
    double[] canalRadius = {.95, .78, .42, .025};
    P[][] canal = new P[centers.length][6];
    for (int ring = 0; ring < centers.length; ring++)
      for (int side = 0; side < 6; side++) {
        double angle = side * Math.PI / 3;
        canal[ring][side] =
            centers[ring].add(
                new P(0, canalRadius[ring] * Math.cos(angle), canalRadius[ring] * Math.sin(angle)));
      }
    for (int ring = 0; ring < centers.length - 1; ring++)
      for (int side = 0; side < 6; side++) {
        int next = (side + 1) % 6;
        texturedSurface(
            mesh,
            "head",
            new P[] {
              canal[ring][side], canal[ring][next], canal[ring + 1][next], canal[ring + 1][side]
            },
            canal[ring][side].add(canal[ring][next]).scale(.5).add(centers[ring].scale(-1)),
            new double[][] {
              {side / 6.0, ring / 4.0},
              {(side + 1) / 6.0, ring / 4.0},
              {(side + 1) / 6.0, (ring + 1) / 4.0},
              {side / 6.0, (ring + 1) / 4.0}
            });
      }
    for (int side = 0; side < 6; side++) {
      int next = (side + 1) % 6;
      mesh.quad(
          "edge",
          new P[] {centers[3], canal[3][side], canal[3][next], canal[3][next]},
          new P(1, 0, 0),
          new double[][] {{.5, .5}, {.25, .5}, {.25, .75}, {.25, .75}});
    }
  }

  private static void tubularShell(EquipmentObjMesh mesh, ShellShape shape) {
    double[] reach = {0, .08, .18, .28, .32};
    double[] mouthWidth = {0, .7, 1, 0, 1};
    P[][] rings = new P[reach.length][8];
    for (int ring = 0; ring < reach.length; ring++)
      for (int side = 0; side < 8; side++) {
        double angle = side * Math.PI / 4;
        if (side == 6 || side == 7)
          angle = 13 * Math.PI / 8 + (side == 6 ? -1 : 1) * Math.PI / 8 * mouthWidth[ring];
        rings[ring][side] = shape.point(reach[ring], angle, 0);
      }
    mesh.group("large_body_whorl_with_tapered_side_mouth");
    for (int ring = 0; ring < reach.length - 1; ring++)
      for (int side = 0; side < 8; side++) {
        if (side == 6 && ring < 3) continue;
        int next = (side + 1) % 8;
        P[] points = {
          rings[ring][side], rings[ring][next], rings[ring + 1][next], rings[ring + 1][side]
        };
        P center = shape.center(reach[ring]);
        texturedSurface(
            mesh,
            "head",
            points,
            points[0].add(points[1]).scale(.5).add(center.scale(-1)),
            new double[][] {
              {side / 8.0, reach[ring]},
              {(side + 1) / 8.0, reach[ring]},
              {(side + 1) / 8.0, reach[ring + 1]},
              {side / 8.0, reach[ring + 1]}
            });
      }
    P center = shape.origin();
    for (int side = 0; side < 8; side++) {
      int next = (side + 1) % 8;
      texturedSurface(
          mesh,
          "head",
          new P[] {center, rings[0][side], rings[0][next], rings[0][next]},
          shape.axis().scale(-1),
          new double[][] {{.5, .5}, {.25, .5}, {.25, .75}, {.25, .75}});
    }
    shellSpire(mesh, shape);

    mesh.group("short_lateral_aperture_with_shell_wall");
    P[][] inside = new P[4][2];
    for (int ring = 0; ring < 4; ring++) {
      inside[ring][0] =
          rings[ring][6].add(
              shape.up().scale(.12 * mouthWidth[ring]).add(shape.across().scale(.65)));
      inside[ring][1] =
          rings[ring][7].add(
              shape.up().scale(-.12 * mouthWidth[ring]).add(shape.across().scale(.65)));
    }
    for (int ring = 0; ring < 3; ring++) {
      for (int edge = 0; edge < 2; edge++)
        surface(
            mesh,
            "inner",
            rings[ring][6 + edge],
            rings[ring + 1][6 + edge],
            inside[ring + 1][edge],
            inside[ring][edge],
            shape.up().scale(edge == 0 ? 1 : -1));
      texturedSurface(
          mesh,
          "shadow",
          new P[] {inside[ring][0], inside[ring][1], inside[ring + 1][1], inside[ring + 1][0]},
          shape.across().scale(-1),
          new double[][] {{0, .25}, {0, .75}, {1, .75}, {1, .25}});
    }
  }

  private record ShellShape(P origin, P axis, P across, P up, P bend, double length, double width) {
    private P center(double fraction) {
      return origin.add(axis.scale(length * fraction)).add(bend.scale(fraction * fraction));
    }

    private P point(double fraction, double angle, double shoulder) {
      double[] reach = {0, .11, .28, .39, .48, .61, .72, .84, 1};
      double[] radius = {1.5, 2.3, 2.22, 1.85, 1.7, 1.3, 1.05, .63, .035};
      int interval = 0;
      while (interval < reach.length - 2 && fraction > reach[interval + 1]) interval++;
      double blend = (fraction - reach[interval]) / (reach[interval + 1] - reach[interval]);
      double shellRadius = radius[interval] + blend * (radius[interval + 1] - radius[interval]);
      shellRadius = width * (shellRadius + shoulder * (1 - .75 * fraction));
      return center(fraction)
          .add(up.scale(shellRadius * Math.cos(angle)))
          .add(across.scale(shellRadius * .84 * Math.sin(angle)));
    }
  }

  private static void shellSpire(EquipmentObjMesh mesh, ShellShape shape) {
    mesh.group("two_broad_helical_whorl_shoulders");
    double turns = 2.25;
    double[] band = {0, .14, 1};
    double[] shoulder = {-.42, .06, -.42};
    for (int turn = -1; turn < 3; turn++)
      for (int strip = 0; strip < band.length - 1; strip++)
        for (int side = 0; side < 8; side++) {
          P[] points = new P[4];
          double[][] uv = new double[4][2];
          for (int point = 0; point < 4; point++) {
            int edge = point == 0 || point == 3 ? 0 : 1;
            int ring = point < 2 ? strip : strip + 1;
            double phase = (side + edge) / 8.0;
            double spiral = Math.clamp(turn + band[ring] + phase, 0, turns);
            double fraction = .32 + .68 * spiral / turns;
            double fade = Math.min(1, spiral * 5) * Math.min(1, (turns - spiral) * 5);
            points[point] = shape.point(fraction, phase * Math.PI * 2, shoulder[ring] * fade);
            uv[point] = new double[] {phase, fraction};
          }
          double middle = (uv[0][1] + uv[1][1]) / 2;
          P center = shape.center(middle);
          texturedSurface(
              mesh,
              strip == 0 ? "rib" : "head",
              points,
              points[0].add(points[1]).scale(.5).add(center.scale(-1)),
              uv);
        }
    P apex = shape.center(1);
    for (int side = 0; side < 8; side++) {
      double angle = side * Math.PI / 4, next = (side + 1) * Math.PI / 4;
      P a = shape.point(1, angle, 0);
      P b = shape.point(1, next, 0);
      double[] firstUv = {.5 + .03 * Math.cos(angle), .95 + .03 * Math.sin(angle)};
      double[] nextUv = {.5 + .03 * Math.cos(next), .95 + .03 * Math.sin(next)};
      mesh.quad(
          "head",
          new P[] {apex, a, b, b},
          shape.axis(),
          new double[][] {{.5, .95}, firstUv, nextUv, nextUv});
    }
  }

  private static P place(P point, P origin, double degrees) {
    double angle = Math.toRadians(degrees);
    return new P(
        origin.x() + point.x() * Math.cos(angle) - point.y() * Math.sin(angle),
        origin.y() + point.x() * Math.sin(angle) + point.y() * Math.cos(angle),
        origin.z() + point.z());
  }

  private static void surface(
      EquipmentObjMesh mesh, String material, P a, P b, P c, P d, P outward) {
    if (b.vector().sub(a.vector()).cross(c.vector().sub(a.vector())).lengthSquared() > .0000001)
      mesh.quad(
          material, new P[] {a, b, c, c}, outward, new double[][] {{0, 1}, {1, 1}, {1, 0}, {1, 0}});
    if (c.vector().sub(a.vector()).cross(d.vector().sub(a.vector())).lengthSquared() > .0000001)
      mesh.quad(
          material, new P[] {a, c, d, d}, outward, new double[][] {{0, 1}, {1, 0}, {0, 0}, {0, 0}});
  }
}
