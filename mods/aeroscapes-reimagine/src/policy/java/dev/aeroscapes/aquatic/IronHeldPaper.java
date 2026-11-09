package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.io.IOException;
import java.util.List;
import org.joml.Vector3f;

final class IronHeldPaper {
  private static final P FRONT = new P(0, 0, 1);
  private static final double[][] FACE_UV = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
  private static final int ROLL_SIDES = 10;

  private IronHeldPaper() {}

  static JsonObject build(IronHeldMesh mesh, String root, String state) throws IOException {
    return switch (root) {
      case "scroll" -> scroll(mesh);
      case "furled_map", "furled_map_ancient", "furled_map_citadel", "furled_map_ice_spider_den" ->
          furledMap(mesh);
      case "chronicle" -> chronicle(mesh);
      case "ruined_book" -> ruinedBook(mesh);
      case "unchained_book" -> unchainedBook(mesh);
      case "archevoker_logbook_translated", "archevoker_logbook_untranslated" -> logbook(mesh);
      case "eldritch_manuscript" -> manuscript(mesh);
      default -> throw new IllegalArgumentException("Unknown Iron's Spells paper model: " + state);
    };
  }

  private static JsonObject scroll(IronHeldMesh mesh) throws IOException {
    mesh.field("parchment", 8, 2, 11, 4);
    mesh.patch("binding", 4, 1, 5, 3);
    mesh.patch("ink", 7, 10, 9, 11);
    var obj = mesh.obj;
    double[] rows = {7.35, 8.12, 9.5, 10.8, 12.15, 13};
    double[] folds = {8.18, 7.99, 7.78, 7.83, 8.02, 8.23};
    sheet(obj, 5.15, 12.7, rows, folds, .12, .24);
    for (double y : new double[] {7.35, 13}) {
      obj.beam("parchment", new P(4.95, y, 8.04), new P(12.92, y, 8.04), .57, 8);
      obj.beam("binding", new P(4.43, y, 8.04), new P(5.04, y, 8.04), .48, 8);
      obj.beam("binding", new P(12.85, y, 8.04), new P(13.45, y, 8.04), .48, 8);
    }
    ink(obj, rows, folds, 6.03, 11.32, 9.39, 11.73);
    ink(obj, rows, folds, 9.86, 11.32, 11.65, 11.73);
    ink(obj, rows, folds, 6.35, 10.36, 8.51, 10.78);
    ink(obj, rows, folds, 8.95, 10.36, 11.44, 10.78);
    ink(obj, rows, folds, 6.05, 9.39, 7.98, 9.82);
    ink(obj, rows, folds, 8.41, 9.39, 11.15, 9.82);
    ink(obj, rows, folds, 6.35, 8.46, 9.77, 8.86);
    return paperPose(11.68f, 6.13f, 7.35f, 8.04f, .81, -18);
  }

  private static JsonObject manuscript(IronHeldMesh mesh) throws IOException {
    mesh.field("parchment", 8, 4, 10, 6);
    mesh.field("rim", 4, 4, 5, 7);
    mesh.patch("ink", 7, 7, 9, 8);
    var obj = mesh.obj;
    double[] rows = {6.75, 7.48, 8.9, 10.3, 11.9, 13.85};
    double[] folds = {8.19, 7.91, 7.79, 7.88, 7.96, 8.27};
    sheet(obj, 5.4, 11.45, rows, folds, .16, .34);
    obj.beam("rim", new P(11.45, 6.85, 8.15), new P(11.45, 13.76, 8.25), .25, 6);
    ink(obj, rows, folds, 7.04, 8.95, 7.55, 12.29);
    ink(obj, rows, folds, 7.55, 10.71, 9.20, 11.17);
    ink(obj, rows, folds, 8.03, 10.14, 8.52, 10.71);
    ink(obj, rows, folds, 8.52, 9.68, 9.60, 10.14);
    ink(obj, rows, folds, 7.55, 8.95, 8.27, 9.42);
    ink(obj, rows, folds, 7.61, 8.23, 9.91, 8.69);
    return paperPose(10.64f, 6.26f, 7.34f, 8.03f, .8, -18);
  }

  private static void sheet(
      EquipmentObjMesh obj,
      double left,
      double right,
      double[] rows,
      double[] folds,
      double depth,
      double taper) {
    for (int row = 1; row < rows.length; row++) {
      double v0 = (rows[rows.length - 1] - rows[row - 1]) / (rows[rows.length - 1] - rows[0]);
      double v1 = (rows[rows.length - 1] - rows[row]) / (rows[rows.length - 1] - rows[0]);
      double x0 = row == 1 ? left + taper : left;
      double x1 = row == rows.length - 1 ? right - taper : right;
      P a = new P(x0, rows[row - 1], folds[row - 1]);
      P b = new P(right, rows[row - 1], folds[row - 1]);
      P c = new P(x1, rows[row], folds[row]);
      P d = new P(left, rows[row], folds[row]);
      var uv = new double[][] {{0, v0}, {1, v0}, {1, v1}, {0, v1}};
      P thickness = new P(0, 0, -depth);
      obj.quad("parchment", new P[] {a, b, c, d}, FRONT, uv);
      obj.quad(
          "parchment",
          new P[] {a.add(thickness), b.add(thickness), c.add(thickness), d.add(thickness)},
          FRONT.scale(-1),
          uv);
      sheetEdge(obj, a, d, thickness, new P(-1, 0, 0));
      sheetEdge(obj, b, c, thickness, new P(1, 0, 0));
      if (row == 1) sheetEdge(obj, a, b, thickness, new P(0, -1, 0));
      if (row == rows.length - 1) sheetEdge(obj, d, c, thickness, new P(0, 1, 0));
    }
  }

  private static void sheetEdge(EquipmentObjMesh obj, P a, P b, P depth, P outward) {
    obj.quad("parchment", new P[] {a, b, b.add(depth), a.add(depth)}, outward, FACE_UV);
  }

  private static void ink(
      EquipmentObjMesh obj,
      double[] rows,
      double[] folds,
      double left,
      double bottom,
      double right,
      double top) {
    for (int row = 1; row < rows.length; row++) {
      double low = Math.max(bottom, rows[row - 1]);
      double high = Math.min(top, rows[row]);
      if (high <= low) continue;
      double slope = (folds[row] - folds[row - 1]) / (rows[row] - rows[row - 1]);
      double z0 = folds[row - 1] + (low - rows[row - 1]) * slope + .012;
      double z1 = folds[row - 1] + (high - rows[row - 1]) * slope + .012;
      obj.quad(
          "ink",
          new P[] {
            new P(left, low, z0),
            new P(right, low, z0),
            new P(right, high, z1),
            new P(left, high, z1)
          },
          FRONT,
          FACE_UV);
    }
  }

  private static JsonObject furledMap(IronHeldMesh mesh) throws IOException {
    mesh.field("parchment", 7, 4, 10, 5);
    mesh.patch("seam", 8, 4, 9, 5);
    mesh.patch("edge", 6, 1, 7, 2);
    mesh.patch("inner", 6, 0, 7, 1);
    mesh.field("tie", 7, 7, 10, 9);
    var obj = mesh.obj;
    P[][] paper = {
      ring(7.5, 3.1, 8, 1.56), ring(7.58, 4.65, 8, 1.45), ring(7.91, 7.18, 8, 1.0),
      ring(8.12, 8.62, 8, 1.03), ring(8.45, 11.1, 8, 1.4), ring(8.55, 12.7, 8, 1.57)
    };
    for (int i = 1; i < paper.length; i++) {
      joinRoll(obj, "parchment", paper[i - 1], paper[i]);
      if (i != 3) joinSeam(obj, paper[i - 1], paper[i]);
    }
    rollEnd(obj, paper[0], 7.5, 3.1, 8, 1);
    rollEnd(obj, paper[paper.length - 1], 8.55, 12.7, 8, -1);
    P[] tieBottom = ring(7.91, 7.18, 8, 1.11);
    P[] tieTop = ring(8.12, 8.62, 8, 1.14);
    joinRoll(obj, "tie", tieBottom, tieTop);
    annulus(obj, "tie", tieBottom, paper[2], -1);
    annulus(obj, "tie", tieTop, paper[3], 1);
    obj.box("tie", 7.69, 7.52, 8.98, 8.35, 8.31, 9.31);
    return IronHeldMesh.display(new P(8, 7.8, 8), .75, -12, 9, -16);
  }

  private static P[] ring(double x, double y, double z, double radius) {
    P[] points = new P[ROLL_SIDES];
    for (int i = 0; i < points.length; i++) {
      double angle = (i + .5) * 2 * Math.PI / points.length;
      points[i] = new P(x + radius * Math.cos(angle), y, z + radius * Math.sin(angle));
    }
    return points;
  }

  private static void joinRoll(EquipmentObjMesh obj, String material, P[] lower, P[] upper) {
    P axis = lower[0].add(lower[ROLL_SIDES / 2]).scale(.5);
    for (int side = 0; side < ROLL_SIDES; side++) {
      int next = (side + 1) % ROLL_SIDES;
      P outward = lower[side].add(lower[next]).scale(.5).add(axis.scale(-1));
      obj.quad(
          material, new P[] {lower[side], lower[next], upper[next], upper[side]}, outward, FACE_UV);
    }
  }

  private static void joinSeam(EquipmentObjMesh obj, P[] lower, P[] upper) {
    P offset = new P(0, 0, .025);
    P lowerStart = lower[1].scale(.17).add(lower[2].scale(.83)).add(offset);
    P upperStart = upper[1].scale(.17).add(upper[2].scale(.83)).add(offset);
    obj.quad(
        "seam",
        new P[] {lowerStart, lower[2].add(offset), upper[2].add(offset), upperStart},
        FRONT,
        FACE_UV);
  }

  private static void rollEnd(
      EquipmentObjMesh obj, P[] edge, double x, double y, double z, int inward) {
    P[] crease = ring(x + .12, y + .08 * inward, z, 1.0);
    P[] rim = ring(x + .17, y + .23 * inward, z, .72);
    P[] inside = ring(x + .16, y + .98 * inward, z, .50);
    annulus(obj, "edge", edge, crease, -inward);
    annulus(obj, "parchment", crease, rim, -inward);
    annulus(obj, "inner", rim, inside, -inward);
    P center = new P(x + .16, y + .98 * inward, z);
    for (int side = 0; side < ROLL_SIDES; side++) {
      P a = inside[side], b = inside[(side + 1) % ROLL_SIDES];
      obj.quad("inner", new P[] {a, b, center, center}, new P(0, -inward, 0), FACE_UV);
    }
  }

  private static void annulus(
      EquipmentObjMesh obj, String material, P[] outer, P[] inner, int normalY) {
    for (int side = 0; side < ROLL_SIDES; side++) {
      int next = (side + 1) % ROLL_SIDES;
      obj.quad(
          material,
          new P[] {outer[side], outer[next], inner[next], inner[side]},
          new P(0, normalY, 0),
          FACE_UV);
    }
  }

  private static JsonObject chronicle(IronHeldMesh mesh) throws IOException {
    String atlas = "irons_spellbooks:item/spell_book_models/chronicle";
    mesh.field("cover", atlas, 12, 11, 20, 14);
    mesh.field("leaves", atlas, 22, 16, 28, 19);
    mesh.field("binding", atlas, 8, 14, 13, 16);
    mesh.field("ribbon", atlas, 14, 23, 20, 24);
    mesh.field("spine", atlas, 2, 10, 8, 14);
    var obj = mesh.obj;
    codex(obj, 5.03, 6.3, 12.56, 14.8, 1.25);
    for (double z : new double[] {6.47, 9.32}) {
      for (double x : new double[] {5.07, 11.35}) {
        obj.box("binding", x, 6.25, z, x + 1.26, 6.92, z + .18);
        obj.box("binding", x, 14.13, z, x + 1.26, 14.85, z + .18);
      }
      obj.box("binding", 6.33, 6.29, z, 11.35, 6.6, z + .18);
      obj.box("binding", 6.33, 14.49, z, 11.35, 14.82, z + .18);
    }
    obj.box("binding", 4.82, 6.62, 6.69, 5.12, 7.38, 9.30);
    obj.box("binding", 4.82, 13.64, 6.69, 5.12, 14.4, 9.30);
    chronicleCover(obj);
    bookmark(obj, 9.8, 6.66, 5.47);
    return bookPose(11.28f, 6.35f, 7.45f);
  }

  private static void chronicleCover(EquipmentObjMesh obj) {
    double gold = 9.44;
    obj.plate(
        "binding",
        "binding",
        List.of(
            new P(6.48, 10.78, gold), new P(6.92, 12.12, gold),
            new P(8.09, 12.86, gold), new P(9.55, 12.64, gold),
            new P(10.27, 11.86, gold), new P(9.83, 11.47, gold),
            new P(9.24, 12.05, gold), new P(8.19, 12.22, gold),
            new P(7.45, 11.72, gold), new P(7.1, 10.79, gold),
            new P(7.59, 10.01, gold), new P(8.68, 9.76, gold),
            new P(9.16, 10.14, gold), new P(9.57, 9.68, gold),
            new P(8.81, 9.13, gold), new P(7.22, 9.49, gold)),
        .095);
    double ribbon = 9.48;
    obj.box("ribbon", 9.78, 6.22, 8.04, 10.30, 6.40, 9.49);
    obj.plate(
        "ribbon",
        "ribbon",
        List.of(
            new P(9.72, 6.32, ribbon), new P(10.34, 6.33, ribbon),
            new P(10.46, 8.26, ribbon), new P(10.10, 9.5, ribbon),
            new P(9.39, 10.56, ribbon), new P(8.73, 10.67, ribbon),
            new P(9.28, 9.51, ribbon), new P(9.74, 8.17, ribbon)),
        .12);
  }

  private static JsonObject logbook(IronHeldMesh mesh) throws IOException {
    mesh.field("cover", 4, 4, 6, 6);
    mesh.field("leaves", 10, 9, 12, 11);
    mesh.field("spine", 2, 6, 4, 8);
    mesh.patch("binding", 6, 5, 7, 6);
    mesh.patch("title", 11, 5, 13, 7);
    mesh.patch("ribbon", 3, 11, 5, 12);
    var obj = mesh.obj;
    codex(obj, 5.23, 6.68, 12.01, 14.8, .89);
    obj.box("binding", 5.42, 13.84, 8.98, 11.67, 14.13, 9.15);
    obj.box("binding", 5.42, 7.23, 8.98, 11.67, 7.52, 9.15);
    obj.box("title", 7.42, 10.57, 8.98, 10.68, 11.61, 9.14);
    bookmark(obj, 6.38, 7.04, 5.78);
    return bookPose(10.79f, 6.4f, 7.63f);
  }

  private static JsonObject ruinedBook(IronHeldMesh mesh) throws IOException {
    mesh.field("cover", 4, 5, 9, 7);
    mesh.field("leaves", 4, 10, 5, 11);
    mesh.field("spine", 1, 8, 2, 10);
    mesh.patch("sculk", 2, 5, 5, 8);
    mesh.patch("sculk_rim", 2, 5, 3, 6);
    var obj = mesh.obj;
    chippedBoard(obj, 6.78, -1);
    chippedBoard(obj, 9.22, 1);
    obj.plate(
        "leaves",
        "leaves",
        List.of(
            new P(5.53, 6.82, 8),
            new P(11.9, 6.82, 8),
            new P(12.21, 7.66, 8),
            new P(12.37, 9.3, 8),
            new P(11.99, 10.53, 8),
            new P(12.38, 11.2, 8),
            new P(12.32, 13.23, 8),
            new P(11.69, 14.25, 8),
            new P(5.52, 14.25, 8)),
        2.25);
    obj.box("spine", 4.99, 6.68, 6.74, 5.82, 14.39, 9.27);
    obj.plate(
        "sculk",
        "sculk",
        List.of(
            new P(5.19, 12.43, 9.36),
            new P(6.15, 12.43, 9.36),
            new P(6.15, 10.92, 9.36),
            new P(7.07, 10.92, 9.36),
            new P(7.07, 12.43, 9.36),
            new P(7.34, 12.43, 9.36),
            new P(7.34, 14.33, 9.36),
            new P(5.19, 14.33, 9.36)),
        .19);
    obj.plate(
        "sculk",
        "sculk",
        List.of(
            new P(8.96, 6.63, 9.36),
            new P(10.81, 6.63, 9.36),
            new P(10.81, 7.05, 9.36),
            new P(11.81, 7.05, 9.36),
            new P(11.81, 8.52, 9.36),
            new P(10.8, 8.52, 9.36),
            new P(10.8, 7.48, 9.36),
            new P(8.96, 7.48, 9.36)),
        .19);
    return bookPose(11.21f, 6.35f, 7.51f);
  }

  private static void chippedBoard(EquipmentObjMesh obj, double z, int outward) {
    List<P> outline =
        List.of(
            new P(5.04, 6.44, z),
            new P(11.89, 6.44, z),
            new P(12.55, 7.51, z),
            new P(12.63, 9.14, z),
            new P(11.96, 10.54, z),
            new P(12.62, 11.18, z),
            new P(12.53, 13.43, z),
            new P(11.72, 14.6, z),
            new P(5.04, 14.6, z));
    obj.plate("cover", "sculk_rim", outline, .31);
    double[] inset = {.77, .82, .93, .73, .89, .78, .84, .72, .80};
    P center = new P(8.7, 10.6, z);
    P surface = new P(0, 0, outward * .170);
    for (int i = 0; i < outline.size(); i++) {
      int next = (i + 1) % outline.size();
      P a = outline.get(i), b = outline.get(next);
      P insideA = a.scale(inset[i]).add(center.scale(1 - inset[i]));
      P insideB = b.scale(inset[next]).add(center.scale(1 - inset[next]));
      obj.quad(
          "sculk_rim",
          new P[] {a.add(surface), b.add(surface), insideB.add(surface), insideA.add(surface)},
          FRONT.scale(outward),
          FACE_UV);
    }
  }

  private static JsonObject unchainedBook(IronHeldMesh mesh) throws IOException {
    mesh.field("cover", 4, 4, 10, 8);
    mesh.field("leaves", 3, 10, 5, 11);
    mesh.field("spine", 1, 6, 2, 9);
    mesh.field("restraint", 3, 1, 7, 2);
    mesh.patch("clasp", 3, 1, 4, 2);
    mesh.patch("rivet", 3, 2, 4, 3);
    var obj = mesh.obj;
    codex(obj, 5.04, 6.32, 12.7, 14.67, 1.17);
    double z = 9.295;
    obj.plate(
        "restraint",
        "restraint",
        List.of(
            new P(5.32, 13.07, z),
            new P(7.01, 13.07, z),
            new P(7.01, 13.64, z),
            new P(5.84, 13.64, z),
            new P(5.84, 14.82, z),
            new P(6.64, 14.82, z),
            new P(6.64, 14.3, z),
            new P(7.05, 14.3, z),
            new P(7.05, 15.23, z),
            new P(5.32, 15.23, z)),
        .27);
    obj.box("clasp", 10.23, 7.05, 9.26, 12.35, 7.89, 9.62);
    obj.box("clasp", 12.35, 7.05, 8.11, 12.80, 7.89, 9.62);
    obj.box("rivet", 10.44, 7.26, 9.57, 10.86, 7.69, 9.70);
    return bookPose(11.4f, 6.37f, 7.42f);
  }

  private static void codex(
      EquipmentObjMesh obj,
      double left,
      double bottom,
      double right,
      double top,
      double halfDepth) {
    double back = 8 - halfDepth, front = 8 + halfDepth;
    obj.box("leaves", left + .49, bottom + .34, back + .14, right - .30, top - .35, front - .13);
    obj.box("cover", left, bottom, back - .20, right, top, back + .16);
    obj.box("cover", left, bottom, front - .15, right, top, front + .18);
    obj.box("spine", left - .13, bottom + .14, back + .03, left + .53, top - .14, front - .04);
  }

  private static void bookmark(EquipmentObjMesh obj, double x, double start, double tip) {
    obj.plate(
        "ribbon",
        "ribbon",
        List.of(
            new P(x, start, 8.04),
            new P(x - .12, tip, 8.04),
            new P(x + .22, tip + .26, 8.04),
            new P(x + .48, tip + .09, 8.04),
            new P(x + .51, start, 8.04)),
        .12);
  }

  private static JsonObject bookPose(float right, float left, float bottom) {
    return paperPose(right, left, bottom, 8, .79, -23);
  }

  private static JsonObject paperPose(
      float right, float left, float bottom, float depth, double scale, double tilt) {
    JsonObject display = new JsonObject();
    for (String hand : IronHeldMesh.HANDS) {
      boolean firstPerson = hand.startsWith("firstperson");
      var transform = new JsonObject();
      transform.add(
          "rotation", IronHeldMesh.vector(firstPerson ? -12 : tilt, firstPerson ? -22 : -4, -3));
      transform.add("scale", IronHeldMesh.vector(scale, scale, scale));
      var perspective = new JsonObject();
      perspective.add(hand, transform);
      // ItemInHandLayer reverses item X, so the right hand grips the high-X edge.
      float edge = hand.endsWith("righthand") ? right : left;
      HeldItemModels.anchorHeld(
          perspective,
          new Vector3f(edge, bottom, depth),
          new Vector3f(0, firstPerson ? 3.2f : 0, 0));
      display.add(hand, transform);
    }
    return display;
  }
}
