package dev.aeroscapes.aquatic;

import static dev.aeroscapes.aquatic.HexHeldMesh.point;

import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import dev.aeroscapes.atmosphere.EquipmentStyle;
import java.io.IOException;
import java.util.List;
import org.joml.Vector3f;

final class HexStudyTools {
  private HexStudyTools() {}

  static JsonObject hammerDisplay() {
    var fallback = new JsonObject();
    var held = new JsonObject();
    for (String hand : IronHeldMesh.HANDS) {
      boolean first = hand.startsWith("firstperson");
      boolean left = hand.endsWith("lefthand");
      double scale = first ? .64 : .92;
      var transform = new JsonObject();
      // Minecraft mirrors display rotations, while the flat striking face stays on local -X.
      transform.add("rotation", IronHeldMesh.vector(first ? 12 : -14, left ? 90 : -90, 0));
      transform.add("scale", IronHeldMesh.vector(scale, scale, scale));
      (first ? fallback : held).add(hand, transform);
    }
    var grip = new Vector3f(8, 4.3f, 8);
    HeldItemModels.anchorHeld(fallback, grip, new Vector3f(-.8f, 1.5f, -.8f));
    HeldItemModels.anchorHeld(held, grip, new Vector3f(.3f, -1.7f, 2));
    fallback.entrySet().forEach(entry -> held.add(entry.getKey(), entry.getValue()));
    return held;
  }

  private static P[] hammerSection(double x, double y, double halfHeight, double halfDepth) {
    double bevel = Math.min(.24, halfHeight / 2);
    return new P[] {
      point(x, y - halfHeight, -halfDepth + bevel),
      point(x, y - halfHeight, halfDepth - bevel),
      point(x, y - halfHeight + bevel, halfDepth),
      point(x, y + halfHeight - bevel, halfDepth),
      point(x, y + halfHeight, halfDepth - bevel),
      point(x, y + halfHeight, -halfDepth + bevel),
      point(x, y + halfHeight - bevel, -halfDepth),
      point(x, y - halfHeight + bevel, -halfDepth)
    };
  }

  private static void hammerHead(EquipmentObjMesh mesh) {
    P[][] sections = {
      hammerSection(-3.1, 12.28, 1.13, 1.06),
      hammerSection(-2.65, 12.28, 1.13, 1.06),
      hammerSection(-1.32, 12.28, .76, .76),
      hammerSection(-.83, 12.25, .93, .95),
      hammerSection(.83, 12.25, .93, .95),
      hammerSection(1.52, 12.25, .78, .88),
      hammerSection(3.14, 11.79, .22, .76)
    };
    for (int row = 0; row < sections.length; row++) {
      P[] rim = sections[row];
      P center = rim[0].add(rim[4]).scale(.5);
      for (int side = 0; side < rim.length; side++) {
        int next = (side + 1) % rim.length;
        if (row > 0) {
          P[] back = sections[row - 1];
          String material =
              switch (side) {
                case 0, 1, 7 -> "dark_iron";
                case 3, 4, 5 -> "polished_iron";
                default -> row == 1 ? "polished_iron" : "metal";
              };
          mesh.quad(
              material,
              new P[] {back[side], back[next], rim[next], rim[side]},
              rim[side].add(rim[next]).scale(.5).add(center.scale(-1)),
              null);
        }
        if (row == 0 || row == sections.length - 1)
          mesh.quad(
              row == 0 ? "polished_iron" : "dark_iron",
              new P[] {center, rim[side], rim[next], rim[next]},
              new P(row == 0 ? -1 : 1, 0, 0),
              null);
      }
    }
  }

  static JsonObject bookDisplay() throws IOException {
    return NativeEquipmentModels.sculpt("chipped:alchemy_book", EquipmentStyle.BOOK)
        .getAsJsonObject("display");
  }

  static void book(HexHeldMesh h, int form, boolean filled, boolean sealed) {
    // Use the Alchemy Book's lower edge, spine and cover axes in model space.
    double spine = -5.5, bottom = 1.5;
    double width = form == 2 ? 8.25 : 10, height = form == 3 ? 14 : 12.5;
    double center = spine + width / 2, foreEdge = spine + width;
    double thick = form == 1 || form == 3 ? 3.55 : 3;
    double front = thick / 2 + .5;
    h.box("paper", center + .17, bottom + .4, 0, width - .95, height - .85, thick);
    h.box("cover", spine + .48, bottom + .06, 0, 1, height - .12, thick + 1.06);
    for (int side : new int[] {-1, 1}) {
      h.box("cover", center, bottom, side * (front - .25), width, height, .5);
    }
    double ribbon = spine + width * .26;
    if (form == 0 || form == 2 || form == 4 || form == 5 || form == 6)
      h.beam(
          "ribbon",
          .34,
          point(ribbon, bottom + .65, .43),
          point(ribbon, bottom - .65, .6),
          point(ribbon - .58, bottom - 1.28, .74));
    if (form == 2 || form == 5 || form == 6)
      for (int side : new int[] {-1, 1})
        h.box(
            "metal",
            center + side * (width / 2 - .65),
            bottom + height - 1.05,
            front,
            1.32,
            .92,
            .25);
    if (form == 1 || form == 3)
      h.box("cover", center, bottom + .22, -.1, width + .12, .7, thick + 1.12);
    if (form == 4 || form == 5 || form == 6 || filled) {
      h.tapered(
          filled ? "rune" : "metal",
          center,
          front + .03,
          bottom + 4.6,
          .75,
          bottom + 7.13,
          1.04,
          .37);
    }
    if (sealed) {
      h.box("metal", foreEdge - .15, bottom + 5.4, 0, .65, 1.05, thick + 1.38);
      h.box("metal", foreEdge - .99, bottom + 5.41, front + .14, 1.9, 1.03, .31);
    }
    if (form == 7)
      h.box("paper", center + .1, bottom + .5, front + .12, width - 1.36, height - 1.28, .3);
  }

  static void add(HexHeldMesh h, String name, String root) {
    switch (root) {
      case "jeweler_hammer" -> {
        h.mesh.region("wood", .25, .06, .47, .74);
        h.box("wood", 0, .8, 0, 1.32, 11.24, 1.22);
        for (String metal : List.of("metal", "polished_iron", "dark_iron"))
          h.mesh.projectedField(metal);
        hammerHead(h.mesh);
        h.mesh.beam("gem", point(0, 12.24, -1.04), point(0, 12.24, 1.04), .67, 6);
      }
      case "abacus" -> abacus(h);
      case "lens" -> HexScrollLens.lens(h);
      case "thought_knot" -> thoughtKnot(h, name.endsWith("written"));
      case "scroll", "scroll_small", "scroll_medium" ->
          HexScrollLens.scroll(h, HexScrollLens.size(root));
      case "slate" -> slate(h);
      case "patchouli_book" -> book(h, 0, false, false);
      case "lore_fragment" -> loreFragment(h);
      default -> {
        if (!root.contains("colorizer"))
          throw new IllegalArgumentException("Missing held equipment: " + root);
        HexPigment.build(h.mesh);
      }
    }
  }

  private static void abacus(HexHeldMesh h) {
    for (int side : new int[] {-1, 1}) {
      h.box("wood", side * 4.55, 1.8, 0, .75, 7.85, 1.06);
      h.box("wood", 0, side < 0 ? 2.14 : 8.68, 0, 8.47, .7, .89);
    }
    h.box("wood", 1.28, 2.72, 0, .57, 6.13, .77);
    for (int row = 0; row < 2; row++) {
      double y = 4.31 + row * 2.74;
      h.beam("wood", .18, point(-4.22, y, 0), point(4.22, y, 0));
      for (double x :
          row == 0 ? new double[] {-3.30, -1.46, -.49} : new double[] {-3.30, -2.33, -.49})
        h.mesh.beam("gem", point(x - .38, y, 0), point(x + .38, y, 0), .82, 8);
      double light = row == 0 ? 2.20 : 3.51;
      h.mesh.beam("paper", point(light - .34, y, 0), point(light + .34, y, 0), .74, 8);
    }
  }

  private static void thoughtKnot(HexHeldMesh h, boolean written) {
    h.mesh.tube(
        "paper",
        List.of(
            point(-2.46, 2.30, -.08),
            point(-2.8, 3.02, -.12),
            point(-2.25, 3.55, -.18),
            point(-.78, 2.48, -.23),
            point(-.30, 2.7, -.34),
            point(-.48, 3.82, -.39),
            point(-1.51, 4.57, -.3),
            point(-2.78, 4.73, -.18),
            point(-2.96, 5.42, .08),
            point(-2.43, 5.85, .29),
            point(-1.2, 5.32, .36),
            point(.42, 5.1, .28),
            point(-.01, 5.71, .08),
            point(-1.45, 7.14, -.16),
            point(-1.69, 8.30, -.22),
            point(-1.19, 8.70, -.08),
            point(-.71, 8.38, .12),
            point(-.79, 7.58, .25),
            point(-1.06, 7.35, .24),
            point(-.93, 6.61, .46),
            point(-.02, 5.74, .38),
            point(.8, 5.12, -.10),
            point(1.52, 5.42, -.26),
            point(1.67, 6.58, -.07),
            point(2.04, 7.51, .2),
            point(2.61, 7.67, .3),
            point(2.98, 7.18, .25),
            point(2.53, 6.23, .09),
            point(1.8, 5.37, -.03),
            point(2.44, 5.24, -.11),
            point(2.66, 4.91, -.08),
            point(1.96, 4.76, .03),
            point(.75, 4.98, -.37),
            point(-.15, 5.24, -.16),
            point(-.58, 5.71, .22),
            point(-.23, 5.91, .44),
            point(.34, 5.31, .48),
            point(.95, 4.39, .06),
            point(1.42, 3.23, -.14),
            point(2.13, 2.47, -.09),
            point(2.73, 2.66, .1),
            point(2.87, 3.47, .18)),
        .21,
        5,
        5);
    if (written) h.gem("rune", -.05, 4.95, .40, .76, 1.72);
  }

  private static void slate(HexHeldMesh h) {
    // The placeable slate is a one-pixel thick square. Its two item sprites depict the top
    // obliquely.
    P[] front = {
      point(-3.95, 1.9, .27), point(3.95, 1.9, .27), point(3.95, 9.8, .27), point(-3.95, 9.8, .27)
    };
    double[][] surface = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
    h.mesh.quad("pigment", front, new P(0, 0, 1), surface);
    P[] back = new P[4];
    for (int i = 0; i < 4; i++) back[i] = front[i].add(new P(0, 0, -.51));
    h.mesh.quad("cover", back, new P(0, 0, -1), surface);
    for (int i = 0; i < 4; i++) {
      int next = (i + 1) % 4;
      P outward = front[i].add(front[next]).scale(.5).add(point(0, 5.85, .27).scale(-1));
      h.mesh.quad(
          "cover",
          new P[] {front[i], front[next], back[next], back[i]},
          outward,
          new double[][] {{0, 0}, {1, 0}, {1, 1.0 / 16}, {0, 1.0 / 16}});
    }
  }

  static JsonObject instrumentDisplay(String root) {
    boolean lens = root.equals("lens"), scroll = root.startsWith("scroll");
    boolean slate = root.equals("slate"),
        abacus = root.equals("abacus"),
        pigment = root.contains("colorizer");
    var result = new JsonObject();
    for (String slot : IronHeldMesh.HANDS) {
      boolean left = slot.endsWith("lefthand"), first = slot.startsWith("first");
      var single = new JsonObject();
      var transform = new JsonObject();
      double scale = lens ? (first ? .49 : .65) : first ? .63 : scroll ? .84 : .81;
      transform.add("scale", IronHeldMesh.vector(scale, scale, scale));
      transform.add(
          "rotation",
          IronHeldMesh.vector(
              first ? -15 : -22,
              first ? 14 : 18,
              lens ? 10 : slate || abacus || scroll ? -10 : -2));
      single.add(slot, transform);
      float radial = scroll ? .99f + .21f * HexScrollLens.size(root) : 0;
      float gripX =
          (left ? -1 : 1)
              * (lens
                  ? 1.05f
                  : scroll
                      ? (float) ((2.65 + 1.45 * HexScrollLens.size(root)) * .90)
                      : abacus ? 4.48f : slate ? 3.56f : pigment ? 1.34f : 1.55f);
      float gripY =
          lens
              ? 5.50f
              : scroll
                  ? 7.50f - 3.57f * radial
                  : abacus ? 3.10f : slate ? 2.77f : pigment ? 3.85f : 3.31f;
      HeldItemModels.anchorHeld(
          single,
          new Vector3f(8 + gripX, gripY, scroll ? 8.38f + radial : 8),
          new Vector3f(
              first ? -.3f : lens ? -1.0f : -.4f,
              first ? 2.2f : lens ? .22f : -.1f,
              first ? -.9f : lens ? .5f : 1));
      result.add(slot, transform);
    }
    return result;
  }

  private static void loreFragment(HexHeldMesh h) {
    fragmentTexel(h, "cover", 10, 1);
    h.box("cover", -.5, 1.52, -.74, 9.8, 12.1, .35);
    fragmentTexel(h, "paper", 13, 8);
    h.box("paper", -.48, 1.73, 0, 9.35, 11.69, 1.15);
    fragmentTexel(h, "ribbon", 2, 11);
    h.mesh.plate(
        "ribbon",
        "ribbon",
        List.of(
            point(-3, 2.18, .09),
            point(-2.02, 2.18, .09),
            point(-2.34, -.05, .09),
            point(-2.9, .28, .09),
            point(-3.45, .15, .09)),
        .22);

    P[] face = {
      point(-5.4, 1.5, .82), point(4.4, 1.5, .82), point(4.4, 13.65, .82), point(-5.4, 13.65, .82)
    };
    // The sprite depicts the marked upper sheet in perspective. Sample only that quadrilateral.
    double[][] artwork = {
      {1.4 / 16, 6.03 / 16},
      {5.92 / 16, 10.42 / 16},
      {14.51 / 16, 6.52 / 16},
      {10.02 / 16, 1.97 / 16}
    };
    h.mesh.quad("pigment", face, new P(0, 0, 1), artwork);
    P[] underside = new P[4];
    for (int i = 0; i < face.length; i++) underside[i] = face[i].add(new P(0, 0, -.29));
    double[][] square = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    fragmentTexel(h, "cover", 14, 6);
    h.mesh.quad("cover", underside, new P(0, 0, -1), square);
    for (int i = 0; i < face.length; i++) {
      int next = (i + 1) % face.length;
      P outward = face[i].add(face[next]).scale(.5).add(point(-.5, 7.575, .82).scale(-1));
      h.mesh.quad(
          "cover", new P[] {face[i], face[next], underside[next], underside[i]}, outward, square);
    }
  }

  private static void fragmentTexel(HexHeldMesh h, String part, int x, int y) {
    h.mesh.region(part, (x + .12) / 16, (y + .12) / 16, (x + .88) / 16, (y + .88) / 16);
  }
}
