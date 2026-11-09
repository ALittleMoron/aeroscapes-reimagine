package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.io.IOException;
import java.util.List;
import org.joml.Vector3f;

/** Handheld forms for Iron's Spells ritual gear, vault keys and tinctures. */
final class IronHeldRitual {
  private static final double[][] UV = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};

  private IronHeldRitual() {}

  static JsonObject build(IronHeldMesh mesh, String root) throws IOException {
    return switch (root) {
      case "energized_core" -> core(mesh);
      case "cinderous_soulcaller" -> bell(mesh);
      case "decrepit_key" -> key(mesh, false);
      case "bone_key" -> key(mesh, true);
      case "dead_king_phylactery" -> phylactery(mesh);
      case "tincture_of_forgetfulness" -> tincture(mesh, false);
      case "netherward_tincture" -> tincture(mesh, true);
      default -> throw new IllegalArgumentException("Unknown ritual item: " + root);
    };
  }

  private static JsonObject core(IronHeldMesh mesh) throws IOException {
    mesh.field("copper", 7, 5, 10, 7);
    mesh.field("base", 6, 11, 10, 14);
    mesh.patch("seam", 10, 10, 12, 11);
    mesh.overlay("arc", "irons_spellbooks:item/energized_core_energy", 0, 0, 16, 16);

    P[] foot = ring(3.4, 1.95, 1.76);
    P[] base = ring(4.45, 3.65, 3.26);
    P[] waist = ring(6.42, 4.42, 3.93);
    P[] lowerRim = ring(6.94, 4.68, 4.15);
    P[] upperRim = ring(7.66, 4.66, 4.16);
    P[] shoulder = ring(8.92, 3.86, 3.47);
    P[] crown = ring(10.22, 2.45, 2.28);
    P[] cap = ring(10.65, 1.35, 1.27);
    var body = mesh.obj;
    disk(body, "base", foot, -1);
    wall(body, "base", foot, base);
    wall(body, "base", base, waist);
    wall(body, "base", waist, lowerRim);
    wall(body, "seam", lowerRim, upperRim);
    wall(body, "copper", upperRim, shoulder);
    wall(body, "copper", shoulder, crown);
    wall(body, "copper", crown, cap);
    disk(body, "copper", cap, 1);

    // Carry the native transient sparks around the copper shell, including its lower rim.
    P[][] rows = {foot, base, waist, lowerRim, upperRim, shoulder, crown, cap};
    for (int side : new int[] {-1, 1}) {
      for (int index = 1; index < rows.length; index++) {
        P[] lower = rows[index - 1], upper = rows[index];
        double bottom = (cap[0].y() - lower[0].y()) / (cap[0].y() - foot[0].y());
        double top = (cap[0].y() - upper[0].y()) / (cap[0].y() - foot[0].y());
        for (int facet = 0; facet < 4; facet++) {
          int first = Math.floorMod(side * facet, 8);
          int second = Math.floorMod(side * (facet + 1), 8);
          P a = spark(lower[first]), b = spark(lower[second]);
          P c = spark(upper[second]), d = spark(upper[first]);
          double left = 1 - facet / 4.0;
          double right = 1 - (facet + 1) / 4.0;
          body.quad(
              "arc",
              new P[] {a, b, c, d},
              new P(a.x() + b.x() - 16, 0, a.z() + b.z() - 16),
              new double[][] {{left, bottom}, {right, bottom}, {right, top}, {left, top}});
        }
      }
    }
    return IronHeldMesh.display(new P(8, 5.02, 8), .62, -16, -26, 8);
  }

  private static P spark(P point) {
    return new P(8 + (point.x() - 8) * 1.05, point.y(), 8 + (point.z() - 8) * 1.05);
  }

  private static JsonObject bell(IronHeldMesh mesh) throws IOException {
    mesh.field("metal", 9, 3, 13, 6);
    mesh.patch("edge", 8, 12, 9, 13);
    mesh.patch("inside", 8, 10, 9, 11);
    mesh.patch("cinder", 4, 7, 5, 8);
    var body = mesh.obj;
    P[] lip = ring(3.3, 4.07, 3.62);
    P[] lower = ring(3.96, 3.85, 3.45);
    P[] skirt = ring(4.7, 3.18, 2.9);
    P[] shoulder = ring(6.65, 2.33, 2.08);
    P[] crown = ring(8.3, 1.1, .99);
    P[] opening = ring(3.3, 3.51, 3.12);
    P[] inside = ring(7.83, .72, .65);
    wall(body, "edge", lip, lower);
    for (int facet = 0; facet < lip.length; facet++)
      wallFacet(
          body,
          facet == 1 || facet == 4 || facet == 6 ? "cinder" : "edge",
          lower,
          skirt,
          facet,
          false);
    wall(body, "metal", skirt, shoulder);
    wall(body, "metal", shoulder, crown);
    disk(body, "metal", crown, 1);
    ringFace(body, "edge", lip, opening, new P(0, -1, 0));
    for (int facet = 0; facet < lip.length; facet++)
      wallFacet(body, "inside", opening, inside, facet, true);
    disk(body, "inside", inside, -1);
    body.beam("edge", new P(8, 7.86, 8), new P(8, 3.81, 8), .29, 6);
    body.shell(
        "cinder", new P[][] {ring(3.54, .43, .39), ring(3.89, .63, .57), ring(4.33, .44, .4)});
    eye(body, "metal", "edge", 8, 9.62, 1.42, 1.66, .7, .88, .52);
    return IronHeldMesh.display(new P(8, 9.83, 8), .63, -75, -26, -60);
  }

  private static JsonObject key(IronHeldMesh mesh, boolean bone) throws IOException {
    var body = mesh.obj;
    if (bone) {
      mesh.field("shaft", 6, 12, 9, 13);
      mesh.field("edge", 6, 8, 8, 9);
      mesh.patch("skull", 4, 2, 10, 7);
      body.plate(
          "skull",
          "edge",
          List.of(
              p(4.55, 10),
              p(4.4, 12.65),
              p(5.1, 13.75),
              p(9.65, 13.75),
              p(10.4, 12.65),
              p(10.28, 10),
              p(8.75, 9.32),
              p(6.35, 9.32)),
          2.14);
    } else {
      mesh.field("shaft", 7, 8, 9, 10);
      mesh.field("edge", 7, 10, 9, 11);
      mesh.field("bow", 6, 1, 10, 3);
      eye(body, "bow", "edge", 7.6, 11.95, 3.31, 2.86, 1.84, 1.58, .67);
    }
    double shoulder = bone ? 9.67 : 9.48;
    double tip = bone ? 11.45 : 11.63;
    body.plate(
        "shaft",
        "edge",
        List.of(
            p(6.68, 2.65),
            p(tip, 2.65),
            p(tip, 3.85),
            p(8.93, 3.85),
            p(8.93, 4.69),
            p(10.27, 4.69),
            p(10.27, 5.9),
            p(8.52, 5.9),
            p(8.52, shoulder),
            p(6.65, shoulder)),
        bone ? 1.22 : 1.1);
    var display = new JsonObject();
    for (String hand : IronHeldMesh.HANDS) {
      var transform = new JsonObject();
      transform.add("rotation", IronHeldMesh.vector(-42, -7, -85));
      transform.add("scale", IronHeldMesh.vector(.73, .73, .73));
      display.add(hand, transform);
    }
    // The narrow shoulder crosses the front corner of the fist, exposing both ends.
    HeldItemModels.anchorHeld(display, new Vector3f(7.55f, 8.4f, 8), new Vector3f(0, .45f, .2f));
    return display;
  }

  private static JsonObject phylactery(IronHeldMesh mesh) throws IOException {
    mesh.field("bone", 6, 1, 9, 2);
    mesh.field("shard", 6, 14, 8, 15);
    mesh.patch("soul", 6, 9, 10, 14);
    var body = mesh.obj;

    P[] foot = ring(2.6, 3.52, 2.66);
    P[] edge = ring(3.36, 3.58, 2.74);
    P[] pedestal = ring(3.9, 2.49, 1.92);
    P[] seat = ring(4.22, 1.74, 1.48);
    disk(body, "shard", foot, -1);
    wall(body, "shard", foot, edge);
    wall(body, "bone", edge, pedestal);
    wall(body, "shard", pedestal, seat);
    disk(body, "shard", seat, 1);

    P[] ember = ring(5.03, 2.08, 1.78);
    P[] pulse = ring(6.22, 2.82, 2.29);
    P[] shoulder = ring(8.29, 2.53, 2.05);
    P[] neck = ring(9.85, 1.58, 1.35);
    soul(body, seat, ember, pulse, shoulder, neck);

    P[] collar = ring(10.05, 2.82, 2.02);
    P[] ringTop = ring(10.75, 2.61, 1.88);
    P[] finialBase = ring(11.03, 1.27, 1.17);
    P[] finialLip = ring(12.14, 1.48, 1.33);
    P[] finial = ring(12.89, 1.83, 1.62);
    wall(body, "shard", neck, collar);
    wall(body, "bone", collar, ringTop);
    wall(body, "bone", ringTop, finialBase);
    wall(body, "bone", finialBase, finialLip);
    wall(body, "bone", finialLip, finial);
    disk(body, "bone", finial, 1);
    for (int side : new int[] {-1, 1}) {
      eye(body, "bone", "shard", 8 + side * 3.28, 8.25, 1.55, 2.08, .82, 1.12, .83);
      body.beam("shard", new P(8 + side * 3.17, 6.42, 8), new P(8 + side * 2.1, 4.03, 8), .59, 4);
      body.beam("bone", new P(8 + side * 3.27, 9.95, 8), new P(8 + side * 2.25, 10.42, 8), .68, 4);
    }
    return IronHeldMesh.display(new P(8, 4.51, 8.18), .57, -9, -29, 7);
  }

  private static void soul(EquipmentObjMesh mesh, P[]... rings) {
    double bottom = rings[0][0].y();
    double top = rings[rings.length - 1][0].y();
    double halfWidth = 0;
    for (P[] ring : rings) halfWidth = Math.max(halfWidth, ring[0].x() - 8);
    // The bright part of the native soul stays in one place across all rows of the chamber.
    for (int row = 1; row < rings.length; row++) {
      P[] lower = rings[row - 1], upper = rings[row];
      for (int side = 0; side < lower.length; side++) {
        int next = (side + 1) % lower.length;
        P[] points = {lower[side], lower[next], upper[next], upper[side]};
        double[][] uv = new double[4][2];
        for (int i = 0; i < points.length; i++) {
          P point = points[i];
          double lateral = (point.x() - 8) / halfWidth;
          uv[i][0] = .5 + .5 * lateral;
          uv[i][1] = (top - point.y()) / (top - bottom) * (1 - .5 * lateral * lateral);
        }
        mesh.quad("soul", points, radial(points[0], points[1]), uv);
      }
    }
  }

  private static JsonObject tincture(IronHeldMesh mesh, boolean netherward) throws IOException {
    mesh.patch("cork", 7, 3, 9, 4);
    mesh.patch("stopper", 8, 4, 9, 5);
    mesh.glass("glass", 7, 4, 8, 6, .31);
    mesh.glass("side_glass", 9, 6, 10, 7, .37);
    mesh.glass("base", 7, 14, 10, 15, .46);
    mesh.glass("lip", 7, 6, 8, 7, .54);
    if (netherward) {
      mesh.patch("liquid", 9, 10, 10, 11);
      mesh.patch("meniscus", 8, 7, 9, 8);
    } else {
      mesh.patch("liquid", 8, 10, 9, 11);
      mesh.patch("meniscus", 9, 10, 10, 11);
    }

    double baseWidth = netherward ? 1.23 : 1.28;
    double width = netherward ? 1.66 : 3.25;
    double sideHeight = netherward ? 6.99 : 5.04;
    double neck = netherward ? 1.07 : .98;
    P[] foot = ring(3, baseWidth, baseWidth * .91);
    P[] heel = ring(3.58, netherward ? width : 2.13, netherward ? 1.52 : 1.78);
    P[] body = ring(netherward ? 4.22 : 4.67, width, width * .84);
    P[] shoulder = ring(sideHeight, width, width * .84);
    P[] throat = ring(netherward ? 8.19 : 7.8, neck, neck);
    P[] mouth = ring(10.72, neck, neck);
    P[] lip = ring(11.24, neck + .24, neck + .24);
    P[] opening = ring(11.24, neck - .25, neck - .25);

    var glass = mesh.layer("minecraft:translucent");
    disk(glass, "base", foot, -1);
    wall(glass, "base", foot, heel);
    for (int facet = 0; facet < lip.length; facet++) {
      String pane =
          facet == 2 || facet == 6 ? "lip" : facet == 0 || facet == 4 ? "side_glass" : "glass";
      wallFacet(glass, pane, heel, body, facet, false);
      wallFacet(glass, pane, body, shoulder, facet, false);
      wallFacet(glass, pane, shoulder, throat, facet, false);
      wallFacet(glass, pane, throat, mouth, facet, false);
    }
    wall(glass, "lip", mouth, lip);
    ringFace(glass, "lip", lip, opening, new P(0, 1, 0));
    P[] innerNeck = ring(10.42, neck - .24, neck - .24);
    for (int facet = 0; facet < lip.length; facet++)
      wallFacet(glass, "glass", innerNeck, opening, facet, true);

    var contents = mesh.obj;
    P[] liquidFoot = ring(3.27, baseWidth - .24, baseWidth * .91 - .24);
    P[] liquidHeel = ring(heel[0].y(), heel[0].x() - 8.24, heel[2].z() - 8.24);
    P[] liquidBody = ring(body[0].y(), width - .25, width * .84 - .25);
    double fillY = netherward ? 7.02 : 5.57;
    double shoulderFraction = (fillY - sideHeight) / (throat[0].y() - sideHeight);
    P[] fill =
        ring(
            fillY,
            width + (neck - width) * shoulderFraction - .24,
            width * .84 + (neck - width * .84) * shoulderFraction - .24);
    disk(contents, "liquid", liquidFoot, -1);
    wall(contents, "liquid", liquidFoot, liquidHeel);
    wall(contents, "liquid", liquidHeel, liquidBody);
    wall(contents, "liquid", liquidBody, fill);
    disk(contents, "meniscus", fill, 1);
    P[] stopper = ring(10.59, neck - .27, neck - .27);
    P[] capBase = ring(11.25, neck + .06, neck + .06);
    P[] top = ring(11.77, neck + .15, neck + .15);
    disk(contents, "stopper", stopper, -1);
    wall(contents, "stopper", stopper, capBase);
    wall(contents, "cork", capBase, top);
    disk(contents, "cork", top, 1);
    return IronHeldMesh.display(
        new P(8, 3.5, netherward ? 7.15 : 6.6), netherward ? .53 : .55, -17, -26, 26);
  }

  private static P[] ring(double y, double width, double depth) {
    return EquipmentObjMesh.octagonalRing(8, y, 8, width, depth);
  }

  private static void wall(EquipmentObjMesh mesh, String material, P[] lower, P[] upper) {
    for (int facet = 0; facet < lower.length; facet++)
      wallFacet(mesh, material, lower, upper, facet, false);
  }

  private static void wallFacet(
      EquipmentObjMesh mesh, String material, P[] lower, P[] upper, int facet, boolean inward) {
    int next = (facet + 1) % lower.length;
    P a = lower[facet], b = lower[next], c = upper[next], d = upper[facet];
    P outward = radial(a, b).scale(inward ? -1 : 1);
    mesh.quad(material, new P[] {a, b, c, d}, outward, UV);
  }

  private static P radial(P a, P b) {
    return new P(a.x() + b.x() - 16, 0, a.z() + b.z() - 16);
  }

  private static void ringFace(
      EquipmentObjMesh mesh, String material, P[] outside, P[] inside, P outward) {
    for (int side = 0; side < outside.length; side++) {
      int next = (side + 1) % outside.length;
      mesh.quad(
          material,
          new P[] {outside[side], outside[next], inside[next], inside[side]},
          outward,
          UV);
    }
  }

  private static void disk(EquipmentObjMesh mesh, String material, P[] ring, int direction) {
    P center = new P(8, ring[0].y(), 8);
    for (int side = 0; side < ring.length; side++) {
      int next = (side + 1) % ring.length;
      double angle = side * Math.PI / 4;
      double nextAngle = next * Math.PI / 4;
      double[] a = {.5 + .5 * Math.cos(angle), .5 + .5 * Math.sin(angle)};
      double[] b = {.5 + .5 * Math.cos(nextAngle), .5 + .5 * Math.sin(nextAngle)};
      mesh.quad(
          material,
          new P[] {center, ring[side], ring[next], ring[next]},
          new P(0, direction, 0),
          new double[][] {{.5, .5}, a, b, b});
    }
  }

  private static void eye(
      EquipmentObjMesh mesh,
      String face,
      String edge,
      double x,
      double y,
      double width,
      double height,
      double openingWidth,
      double openingHeight,
      double depth) {
    P[][] outer = new P[2][8], inner = new P[2][8];
    for (int end = 0; end < 2; end++) {
      double z = 8 + (2 * end - 1) * depth;
      for (int side = 0; side < 8; side++) {
        double angle = (side + .5) * Math.PI / 4;
        double horizontal = Math.cos(angle), vertical = Math.sin(angle);
        outer[end][side] = new P(x + width * horizontal, y + height * vertical, z);
        inner[end][side] = new P(x + openingWidth * horizontal, y + openingHeight * vertical, z);
      }
      ringFace(mesh, face, outer[end], inner[end], new P(0, 0, 2 * end - 1));
    }
    for (int side = 0; side < 8; side++) {
      int next = (side + 1) % 8;
      P a = outer[0][side], b = outer[0][next];
      P radial = new P(a.x() + b.x() - 2 * x, a.y() + b.y() - 2 * y, 0);
      mesh.quad(edge, new P[] {a, b, outer[1][next], outer[1][side]}, radial, UV);
      mesh.quad(
          edge,
          new P[] {inner[0][side], inner[0][next], inner[1][next], inner[1][side]},
          radial.scale(-1),
          UV);
    }
  }

  private static P p(double x, double y) {
    return new P(x, y, 8);
  }
}
