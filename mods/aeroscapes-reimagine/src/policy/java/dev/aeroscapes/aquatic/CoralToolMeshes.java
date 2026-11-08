package dev.aeroscapes.aquatic;

import dev.aeroscapes.aquatic.EquipmentObjMesh.P;

/** Compact coral working heads retain native silhouettes and broad physical tool planes. */
final class CoralToolMeshes {
  private CoralToolMeshes() {}

  static void blade(EquipmentObjMesh mesh) {
    mesh.group("seated_centre_prong");
    uprightHead(
        mesh,
        27.4,
        new double[][] {
          {8, 18.15, 1.1, 1.1}, {8, 20.1, 1.2, 1.2},
          {8, 25.9, 1, .75}, {8, 27.4, .6, .4}
        });
    for (int direction : new int[] {-1, 1}) {
      mesh.group(direction < 0 ? "left_fork_bridge" : "right_fork_bridge");
      P[] bridge = {
        new P(3.05, 19.75, 7.3), new P(6.95, 18.85, 6.95),
        new P(6.95, 20.55, 6.95), new P(4.45, 21.2, 7.17436),
        new P(3.05, 21.2, 7.3)
      };
      if (direction > 0)
        for (int point = 0; point < bridge.length; point++) {
          P source = bridge[point];
          bridge[point] = new P(16 - source.x(), source.y(), source.z());
        }
      convexHead(mesh, 27.4, bridge);
      mesh.group(direction < 0 ? "left_fork_prong" : "right_fork_prong");
      uprightHead(
          mesh,
          27.4,
          new double[][] {
            {8 + direction * 4.25, 20.5, .65, .6},
            {8 + direction * 4.4, 23.8, .75, .65},
            {8 + direction * 4.55, 24.55, .45, .4}
          });
    }
  }

  static void pickaxe(EquipmentObjMesh mesh) {
    mesh.group("seated_coral_crown");
    double[][] crown = {{17.55, 1.15, 1.1}, {19.9, 1.45, 1.3}, {21.15, 1.1, .9}, {23.3, .9, .6}};
    P[] previous = null;
    for (double[] section : crown) {
      double y = section[0], width = section[1], depth = section[2];
      P[] ring = {
        new P(8 - width, y, 8 - depth), new P(8 + width, y, 8 - depth),
        new P(8 + width, y, 8 + depth), new P(8 - width, y, 8 + depth)
      };
      if (previous == null)
        mesh.quad("shadow", ring, new P(0, -1, 0), pickaxeUv(ring, new P(0, -1, 0)));
      else {
        for (int side = 0; side < 4; side++) {
          int next = (side + 1) % 4;
          P[] face = {previous[side], previous[next], ring[next], ring[side]};
          P outward =
              switch (side) {
                case 0 -> new P(0, 0, -1);
                case 1 -> new P(1, 0, 0);
                case 2 -> new P(0, 0, 1);
                default -> new P(-1, 0, 0);
              };
          mesh.quad(side % 2 == 0 ? "head" : "edge", face, outward, pickaxeUv(face, outward));
        }
      }
      previous = ring;
    }
    mesh.quad("edge", previous, new P(0, 1, 0), pickaxeUv(previous, new P(0, 1, 0)));
    for (int direction : new int[] {-1, 1}) pickaxeArm(mesh, direction);
  }

  private static void pickaxeArm(EquipmentObjMesh mesh, int direction) {
    mesh.group(direction < 0 ? "left_coral_pick" : "right_coral_pick");
    double[][] sections = {
      {1.05, 19.25, 21.15, .85},
      {3.65, 18.9, 20.7, .75},
      {6.15, 18.1, 19.65, .45},
      {6.65, 17.95, 18.85, .3}
    };
    P[] previous = null;
    for (double[] section : sections) {
      double x = 8 + direction * section[0], depth = section[3];
      P[] ring = {
        new P(x, section[1], 8 - depth), new P(x, section[2], 8 - depth),
        new P(x, section[2], 8 + depth), new P(x, section[1], 8 + depth)
      };
      if (previous != null) {
        for (int side = 0; side < 4; side++) {
          int next = (side + 1) % 4;
          P[] face = {previous[side], ring[side], ring[next], previous[next]};
          P outward =
              switch (side) {
                case 0 -> new P(0, 0, -1);
                case 1 -> new P(0, 1, 0);
                case 2 -> new P(0, 0, 1);
                default -> new P(0, -1, 0);
              };
          String material = side == 1 ? "edge" : side == 3 ? "shadow" : "head";
          mesh.quad(material, face, outward, pickaxeUv(face, outward));
        }
      }
      previous = ring;
    }
    mesh.quad(
        "edge", previous, new P(direction, 0, 0), pickaxeUv(previous, new P(direction, 0, 0)));
  }

  private static double[][] pickaxeUv(P[] face, P outward) {
    double[][] uv = new double[face.length][2];
    for (int index = 0; index < face.length; index++) {
      P point = face[index];
      double across = outward.x() == 0 ? point.x() - 1.35 : point.z() - 6;
      double vertical = outward.y() == 0 ? 23.3 - point.y() : point.z() - 6;
      uv[index] = new double[] {across / 16, vertical / 16};
    }
    return uv;
  }

  static void axe(EquipmentObjMesh mesh) {
    mesh.group("seated_coral_axe_eye");
    uprightHead(
        mesh, 23, new double[][] {{8, 17.55, 1.15, 1.1}, {8, 20.9, 1.25, 1.2}, {8, 21.3, .75, .9}});
    mesh.group("flared_coral_cutting_wedge");
    convexHead(
        mesh,
        23,
        new P(8.85, 17.35, 6.9),
        new P(15, 16.3, 7.75),
        new P(15, 22.15, 7.75),
        new P(13.3, 23, 7.51504),
        new P(8.85, 20.75, 6.9));
    mesh.group("short_coral_poll");
    convexHead(
        mesh,
        23,
        new P(5.3, 18.85, 7.45),
        new P(6.95, 18.4, 7),
        new P(6.95, 20.55, 7),
        new P(5.3, 20.15, 7.45));
  }

  static void shovel(EquipmentObjMesh mesh) {
    mesh.group("seated_spade_neck");
    uprightHead(
        mesh,
        25.5,
        new double[][] {{8, 17.55, .95, 1.05}, {8, 19.8, 1.05, .9}, {8, 20.8, .85, .7}});
    mesh.group("shallow_coral_spade_pan");
    P bottomLeft = new P(7, 19.65, 7.25), bottomRight = new P(9, 19.65, 7.25);
    P lowerLeft = new P(6.7, 20.8, 7.85), lowerRight = new P(9.3, 20.8, 7.85);
    P middleLeft = new P(6.7, 23.4, 7.85), middleRight = new P(9.3, 23.4, 7.85);
    P tipLeft = new P(7.5, 25.5, 7.5), tipRight = new P(8.5, 25.5, 7.5);
    P leftShoulder = new P(4.75, 20.6, 7.2), rightShoulder = new P(11.25, 20.6, 7.2);
    P leftSide = new P(4.75, 23.1, 7.2), rightSide = new P(11.25, 23.1, 7.2);
    P leftUpper = new P(6.25, 24.75, 7.4), rightUpper = new P(9.75, 24.75, 7.4);
    P[][] workingFaces = {
      {bottomLeft, bottomRight, lowerRight, lowerLeft},
      {lowerLeft, lowerRight, middleRight, middleLeft},
      {middleLeft, middleRight, tipRight, tipLeft},
      {bottomRight, rightShoulder, lowerRight, lowerRight},
      {lowerRight, rightShoulder, rightSide, middleRight},
      {middleRight, rightSide, rightUpper, rightUpper},
      {middleRight, rightUpper, tipRight, tipRight},
      {bottomLeft, lowerLeft, leftShoulder, leftShoulder},
      {lowerLeft, middleLeft, leftSide, leftShoulder},
      {middleLeft, leftUpper, leftSide, leftSide},
      {middleLeft, tipLeft, leftUpper, leftUpper}
    };
    for (P[] face : workingFaces) {
      surface(mesh, "head", 25.5, new P(0, 0, -1), face);
      P[] back = new P[4];
      for (int point = 0; point < face.length; point++)
        back[point] = face[point].add(new P(0, 0, .55));
      surface(mesh, "head", 25.5, new P(0, 0, 1), back);
    }
    P[] outline = {
      bottomLeft, bottomRight, rightShoulder, rightSide, rightUpper,
      tipRight, tipLeft, leftUpper, leftSide, leftShoulder
    };
    for (int edge = 0; edge < outline.length; edge++) {
      P a = outline[edge], b = outline[(edge + 1) % outline.length];
      P outward = new P(b.y() - a.y(), a.x() - b.x(), 0);
      surface(
          mesh,
          edge >= 3 && edge <= 7 ? "edge" : "shadow",
          25.5,
          outward,
          a,
          b,
          b.add(new P(0, 0, .55)),
          a.add(new P(0, 0, .55)));
    }
  }

  private static void uprightHead(EquipmentObjMesh mesh, double top, double[][] sections) {
    P[] previous = null;
    for (double[] section : sections) {
      double x = section[0], y = section[1], width = section[2], depth = section[3];
      P[] ring = {
        new P(x - width, y, 8 - depth), new P(x + width, y, 8 - depth),
        new P(x + width, y, 8 + depth), new P(x - width, y, 8 + depth)
      };
      if (previous == null) surface(mesh, "shadow", top, new P(0, -1, 0), ring);
      else
        for (int side = 0; side < 4; side++) {
          int next = (side + 1) % 4;
          P outward =
              switch (side) {
                case 0 -> new P(0, 0, -1);
                case 1 -> new P(1, 0, 0);
                case 2 -> new P(0, 0, 1);
                default -> new P(-1, 0, 0);
              };
          surface(
              mesh,
              side % 2 == 0 ? "head" : "edge",
              top,
              outward,
              previous[side],
              previous[next],
              ring[next],
              ring[side]);
        }
      previous = ring;
    }
    surface(mesh, "edge", top, new P(0, 1, 0), previous);
  }

  private static void convexHead(EquipmentObjMesh mesh, double top, P... front) {
    P[] back = new P[front.length];
    double signed = 0;
    for (int point = 0; point < front.length; point++) {
      P a = front[point], b = front[(point + 1) % front.length];
      back[point] = new P(a.x(), a.y(), 16 - a.z());
      signed += a.x() * b.y() - b.x() * a.y();
    }
    for (int point = 1; point < front.length - 1; point++) {
      surface(
          mesh,
          "head",
          top,
          new P(0, 0, -1),
          front[0],
          front[point],
          front[point + 1],
          front[point + 1]);
      surface(
          mesh,
          "head",
          top,
          new P(0, 0, 1),
          back[0],
          back[point],
          back[point + 1],
          back[point + 1]);
    }
    for (int edge = 0; edge < front.length; edge++) {
      int next = (edge + 1) % front.length;
      P a = front[edge], b = front[next];
      P outward = new P(b.y() - a.y(), a.x() - b.x(), 0).scale(Math.signum(signed));
      surface(
          mesh, outward.y() < 0 ? "shadow" : "edge", top, outward, a, b, back[next], back[edge]);
    }
  }

  private static void surface(
      EquipmentObjMesh mesh, String material, double top, P outward, P... face) {
    double[][] uv = new double[face.length][2];
    for (int point = 0; point < face.length; point++) {
      P vertex = face[point];
      double across = vertex.x() - 1.35, vertical = top - vertex.y();
      if (Math.abs(outward.x()) > Math.max(Math.abs(outward.y()), Math.abs(outward.z())))
        across = vertex.z() - 6;
      else if (Math.abs(outward.y()) > Math.abs(outward.z())) vertical = vertex.z() - 6;
      uv[point] = new double[] {across / 16, vertical / 16};
    }
    mesh.quad(material, face, outward, uv);
  }
}
