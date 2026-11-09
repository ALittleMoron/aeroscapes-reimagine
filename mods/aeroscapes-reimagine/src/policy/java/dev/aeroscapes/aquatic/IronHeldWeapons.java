package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.io.IOException;
import java.util.List;
import org.joml.Vector3f;

/** Faceted blades, socketed polearms and the autoloader's moving bow assembly. */
final class IronHeldWeapons {
  private static final P GRIP = new P(8, 8, 8);
  private static final double[][] QUAD_UV = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

  private IronHeldWeapons() {}

  static JsonObject build(IronHeldMesh mesh, String root, String state, JsonObject nativeDisplay)
      throws IOException {
    double firstScale = .76, thirdScale = .9, forwardTilt = 0;
    switch (root) {
      case "magehunter" -> magehunter(mesh);
      case "spellbreaker" -> spellbreaker(mesh);
      case "amethyst_rapier" -> rapier(mesh);
      case "misery" -> misery(mesh);
      case "keeper_flamberge", "legionnaire_flamberge" ->
          flamberge(mesh, root.equals("legionnaire_flamberge"));
      case "decrepit_scythe", "hellrazor" -> {
        scythe(mesh, root.equals("hellrazor"));
        firstScale = .83;
        thirdScale = 1.08;
        forwardTilt = 58;
      }
      case "boreal_blade" -> boreal(mesh);
      case "twilight_gale" -> {
        twilight(mesh);
        firstScale = .85;
        thirdScale = 1;
        if (state.equals(root)) forwardTilt = 43;
      }
      case "autoloader_crossbow" -> {
        crossbow(mesh, state);
        return weaponDisplay(nativeDisplay, .72, .83, 45, 0);
      }
      default -> throw new IllegalArgumentException("Unknown held weapon: " + root);
    }
    return weaponDisplay(nativeDisplay, firstScale, thirdScale, -45, forwardTilt);
  }

  private static JsonObject weaponDisplay(
      JsonObject original,
      double firstScale,
      double thirdScale,
      double alignRoll,
      double forwardTilt) {
    JsonObject display = new JsonObject();
    for (String perspective : List.of("firstperson", "thirdperson")) {
      boolean first = perspective.equals("firstperson");
      double scale = first ? firstScale : thirdScale;
      for (String hand : List.of("righthand", "lefthand")) {
        String slot = perspective + "_" + hand;
        var old = original.getAsJsonObject(slot).getAsJsonArray("rotation");
        double side = hand.equals("righthand") ? 1 : -1;
        // Punchy uses the third-person display slot in its first-person camera.
        double roll = alignRoll + (first ? 0 : forwardTilt);
        JsonObject transform = new JsonObject();
        transform.add(
            "rotation",
            IronHeldMesh.vector(
                old.get(0).getAsDouble(),
                old.get(1).getAsDouble(),
                old.get(2).getAsDouble() + side * roll));
        transform.add("scale", IronHeldMesh.vector(scale, scale, scale));
        display.add(slot, transform);
      }
    }
    HeldItemModels.anchorHeld(
        display,
        new Vector3f((float) GRIP.x(), (float) GRIP.y(), (float) GRIP.z()),
        new Vector3f(0, -1.5f, 1.5f));
    return display;
  }

  private static void magehunter(IronHeldMesh mesh) throws IOException {
    mesh.field("grip", 6, 24, 9, 25);
    mesh.field("pommel", 3, 27, 6, 28);
    mesh.field("guard", 7, 20, 8, 21);
    mesh.field("guard_edge", 6, 20, 8, 21);
    mesh.field("blade", 16, 15, 18, 16);
    mesh.field("bevel", 16, 14, 17, 15);
    mesh.field("dark", 16, 16, 17, 17);
    hilt(mesh.obj, 4.3, 11.7, 1.07, 1.45);
    quillons(mesh.obj, 11.8, 4.0, 1.85, 1.38, false);
    blade(
        mesh.obj,
        rib(12.1, 0, 1.25, .55),
        rib(14.4, 0, 1.45, .55),
        rib(27.7, .04, 1.52, .46),
        rib(29.6, .14, 1.29, .37),
        rib(31.2, .22, .10, .065));
  }

  private static void spellbreaker(IronHeldMesh mesh) throws IOException {
    String allurite = "galosphere:block/allurite_block";
    mesh.field("grip", 5, 25, 8, 26);
    mesh.field("pommel", allurite, 6, 5, 10, 7);
    mesh.field("guard", 11, 20, 12, 21);
    mesh.field("guard_edge", allurite, 6, 5, 10, 7);
    mesh.field("blade", allurite, 10, 4, 15, 8);
    mesh.field("bevel", allurite, 1, 8, 5, 11);
    mesh.field("dark", allurite, 6, 5, 10, 7);
    mesh.glow("blade", 5);
    mesh.glow("bevel", 6);
    hilt(mesh.obj, 3.5, 11.95, 1.0, 1.6);
    quillons(mesh.obj, 12.45, 5.65, 1.45, 1.35, true);
    blade(
        mesh.obj,
        rib(12.55, 0, 1.00, .60),
        rib(16, .05, 1.14, .55),
        rib(30, .05, 1.4, .48),
        rib(34.7, .03, 1.30, .41),
        rib(36.0, 0, .09, .065));
  }

  private static void rapier(IronHeldMesh mesh) throws IOException {
    mesh.field("grip", 6, 24, 9, 25);
    mesh.field("pommel", 3, 27, 6, 28);
    mesh.field("guard", 6, 20, 9, 21);
    mesh.field("guard_edge", 6, 19, 8, 20);
    mesh.field("blade", 16, 14, 18, 15);
    mesh.field("bevel", 22, 8, 23, 9);
    mesh.field("dark", 18, 14, 19, 15);
    hilt(mesh.obj, 4.1, 12.7, .78, 1.02);
    quillons(mesh.obj, 12.25, 3.35, .70, .85, false);
    for (int side : new int[] {-1, 1}) {
      mesh.obj.tube(
          "guard",
          List.of(
              new P(8, 12.9, 8),
              new P(8 + side * 1.75, 12.25, 8 + side * 3.2),
              new P(8 + side * 2.5, 9.55, 8 + side * 4.0),
              new P(8 + side * 1.9, 6.45, 8 + side * 3.7),
              new P(8, 4.15, 8 + side * .55)),
          .39,
          4,
          3);
    }
    blade(
        mesh.obj,
        rib(12.5, 0, .68, .53),
        rib(26, 0, .66, .38),
        rib(36.5, 0, .49, .3),
        rib(38, 0, .065, .045));
  }

  private static void misery(IronHeldMesh mesh) throws IOException {
    mesh.field("grip", 6, 24, 8, 25);
    mesh.field("pommel", 3, 27, 6, 28);
    mesh.field("guard", 6, 20, 8, 21);
    mesh.field("guard_edge", 8, 20, 10, 21);
    mesh.field("blade", 16, 14, 18, 15);
    mesh.field("bevel", 23, 8, 24, 9);
    mesh.field("dark", 18, 14, 19, 15);
    hilt(mesh.obj, 4.15, 12.55, .89, 1.38);
    quillons(mesh.obj, 12.7, 4.65, .5, 1.5, false);
    blade(
        mesh.obj,
        rib(12.95, 0, 1.02, .55),
        rib(15.25, 0, .93, .52),
        rib(23.5, 0, .84, .43),
        rib(34.1, .08, .68, .30),
        rib(35.6, .16, .06, .045));
  }

  private static void flamberge(IronHeldMesh mesh, boolean restored) throws IOException {
    mesh.field("grip", 5, 25, 8, 26);
    mesh.field("pommel", 2, 28, 5, 29);
    mesh.field("guard", 7, 20, 9, 21);
    mesh.field("guard_edge", 6, 19, 9, 20);
    mesh.field("blade", 19, 10, 22, 11);
    mesh.field("bevel", 22, 7, 24, 8);
    mesh.field("dark", 18, 12, 20, 13);
    hilt(mesh.obj, 3.5, 12.7, 1.04, 1.4);
    quillons(mesh.obj, 12.85, 4.7, 1.10, 1.45, true);
    if (restored) {
      mesh.obj.box("guard_edge", 6.85, 12.4, 6.65, 9.15, 13.9, 9.35);
    }
    blade(
        mesh.obj,
        rib(13.1, 0, 1.36, .66),
        rib(16, -.15, 1.62, .66),
        rib(18.2, .62, 1.47, .62),
        rib(20.4, -.48, 1.56, .62),
        rib(22.6, .53, 1.38, .59),
        rib(24.8, -.53, 1.45, .56),
        rib(27.0, .45, 1.39, .55),
        rib(29.2, -.42, 1.29, .52),
        rib(31.4, .38, 1.14, .43),
        rib(33.1, -.35, .82, .36),
        rib(34.5, .63, .09, .065));
  }

  private static void boreal(IronHeldMesh mesh) throws IOException {
    mesh.field("grip", 6, 24, 9, 25);
    mesh.field("pommel", 2, 28, 5, 29);
    mesh.field("guard", 8, 19, 11, 20);
    mesh.field("guard_edge", 7, 17, 9, 18);
    mesh.field("blade", 25, 5, 28, 6);
    mesh.field("bevel", 26, 4, 29, 5);
    mesh.field("dark", 16, 14, 17, 15);
    hilt(mesh.obj, 3.0, 12.3, 1.20, 1.42);
    mesh.obj.plate(
        "guard",
        "guard_edge",
        List.of(
            new P(1.95, 11.85, 8),
            new P(3.05, 14.9, 8),
            new P(4.95, 14.0, 8),
            new P(6.05, 15.65, 8),
            new P(8.0, 14.2, 8),
            new P(9.85, 15.4, 8),
            new P(11.4, 13.75, 8),
            new P(12.95, 15.15, 8),
            new P(14.05, 11.55, 8),
            new P(11.75, 12.2, 8),
            new P(8.0, 11.75, 8),
            new P(4.25, 12.25, 8)),
        2.17);
    blade(
        mesh.obj,
        rib(12.3, 0, 1.9, 1.01),
        rib(16.0, -.12, 2.81, 1.09),
        rib(19.8, -.18, 2.21, .86),
        rib(24.7, .27, 2.03, .78),
        rib(28.5, -.07, 2.06, .8),
        rib(35.2, .05, 2.37, .74),
        rib(38.3, .28, 2.50, .65),
        rib(40.3, .52, .84, .3),
        rib(41, .70, .10, .06));
  }

  private static void scythe(IronHeldMesh mesh, boolean restored) throws IOException {
    mesh.field("grip", 15, 31, 18, 32);
    mesh.field("pommel", 6, 39, 9, 40);
    mesh.field("guard", 18, 27, 22, 28);
    mesh.field("blade", 21, 9, 24, 10);
    mesh.field("bevel", 20, 9, 22, 10);
    mesh.field("dark", 29, 10, 33, 11);
    mesh.field("socket", 32, 16, 36, 17);
    hilt(mesh.obj, -.4, 18.45, .92, 1.40);
    mesh.obj.box("guard", 6.4, 16.2, 6.45, 9.6, 18.5, 9.55);
    mesh.obj.plate(
        "grip",
        "socket",
        List.of(
            new P(7.05, 17.5, 8),
            new P(8.95, 17.5, 8),
            new P(8.95, 22, 8),
            new P(18, 30.4, 8),
            new P(15.2, 34, 8),
            new P(13.8, 32.9, 8),
            new P(15.55, 30.55, 8),
            new P(7.05, 23.15, 8)),
        1.65);
    mesh.obj.box("socket", 14.3, 28.6, 6.65, 17.3, 32.6, 9.35);
    if (restored) {
      mesh.obj.box("guard", 15.5, 29.5, 6.22, 17.2, 31.55, 9.78);
    }
    loft(
        mesh.obj,
        true,
        "blade",
        "bevel",
        "dark",
        List.of(
            rib(-13.3, 12.1, .07, .055),
            rib(-12.3, 15.0, .88, .27),
            rib(-9.2, 19.4, 1.35, .53),
            rib(-5.0, 22.6, 1.33, .68),
            rib(-.8, 24.5, 1.85, .72),
            rib(2.5, 24.7, 1.44, .67),
            rib(5.8, 25.2, 1.84, .72),
            rib(9.1, 24.3, 1.60, .81),
            rib(11.9, 22.05, 1.38, .67),
            rib(13.0, 19.2, .81, .45),
            rib(13.35, 17.1, .13, .08)));
  }

  private static void twilight(IronHeldMesh mesh) throws IOException {
    mesh.field("shaft", 28, 18, 30, 19);
    mesh.field("pommel", 4, 44, 7, 45);
    mesh.field("guard", 22, 22, 25, 23);
    mesh.field("guard_edge", 28, 20, 29, 21);
    mesh.field("vine", 23, 22, 25, 23);
    mesh.glow("vine", 5);
    mesh.field("blade", 39, 7, 41, 8);
    mesh.field("bevel", 39, 6, 41, 7);
    mesh.field("dark", 41, 9, 42, 10);
    mesh.obj.box("shaft", 7.28, -10.5, 7.28, 8.72, 34.2, 8.72);
    mesh.obj.plate(
        "pommel",
        "guard",
        List.of(
            new P(5.7, -10.5, 8),
            new P(7.1, -12.9, 8),
            new P(9.45, -12.9, 8),
            new P(10.4, -10.7, 8),
            new P(9.0, -8.0, 8),
            new P(8.6, -5.8, 8),
            new P(7.0, -6.2, 8)),
        2.35);
    twilightVine(mesh.obj);
    quillons(mesh.obj, 31.2, 3.45, 1.85, 1.38, true);
    blade(
        mesh.obj,
        rib(30.9, 0, .91, .79),
        rib(33.8, 0, 2.18, 1.5),
        rib(36.1, -.28, 2.36, 1.22),
        rib(39.6, -.16, 1.6, .99),
        rib(43.1, .30, .86, .54),
        rib(44.3, .20, .08, .055));
  }

  private static void twilightVine(EquipmentObjMesh mesh) {
    double[] heights = {
      -8.2, -5.8, -4.6, -3.5, -2.7, -1.9, -.3, 1.6,
      2.95, 4.5, 5.6, 7.5, 8.55, 10.5, 12.45, 14.2,
      15.3, 16.2, 17.1, 18.5, 20.2, 21.4, 22.1, 23.05,
      24.3, 26.45, 27.85, 29, 30.5, 31.4
    };
    P[][] stem = new P[heights.length][];
    for (int index = 0; index < heights.length; index++) {
      double angle = (index - 1) * Math.PI / 4;
      double x = Math.cos(angle), z = Math.sin(angle);
      // The centerline follows the square section just inside the shaft faces.
      double perimeter = .69 / Math.max(Math.abs(x), Math.abs(z));
      double y = heights[index];
      double radius = .24 + .035 * Math.sin(index * 1.4);
      if (y >= 5.6 && y <= 10.5) radius = .17;
      if (index == 0 || index == heights.length - 1) radius = .08;
      stem[index] =
          EquipmentObjMesh.octagonalRing(8 + x * perimeter, y, 8 + z * perimeter, radius, radius);
    }
    mesh.shell("vine", stem);

    twilightShoot(mesh, stem[5], 0, 2.0, .64);
    twilightShoot(mesh, stem[17], 0, 1.65, -.60);
    twilightShoot(mesh, stem[23], -.63, 2.45, 0);
  }

  private static void twilightShoot(
      EquipmentObjMesh mesh, P[] stem, double shiftX, double rise, double shiftZ) {
    P root = stem[0].add(stem[stem.length / 2]).scale(.5);
    mesh.shell(
        "vine",
        new P[][] {
          EquipmentObjMesh.octagonalRing(root.x(), root.y(), root.z(), .13, .13),
          EquipmentObjMesh.octagonalRing(
              root.x() + shiftX * .76, root.y() + rise * .48, root.z() + shiftZ * .76, .18, .18),
          EquipmentObjMesh.octagonalRing(
              root.x() + shiftX, root.y() + rise, root.z() + shiftZ, .055, .055)
        });
  }

  private static void crossbow(IronHeldMesh mesh, String state) throws IOException {
    String base = "irons_spellbooks:item/autoloader_crossbow_standby";
    mesh.field("stock", base, 8, 10, 13, 11);
    mesh.field("stock_edge", base, 9, 11, 13, 12);
    mesh.field("iron", base, 11, 4, 12, 5);
    mesh.field("brass", base, 2, 3, 5, 4);
    mesh.patch("string", base, 9, 6, 10, 7);
    boolean arrow = state.endsWith("_arrow");
    boolean firework = state.endsWith("_firework");
    double pull =
        arrow || firework
            ? 1
            : state.startsWith("autoloader_crossbow_pulling_")
                ? (1 + state.charAt(state.length() - 1) - '0') / 8.0
                : 0;

    loft(
        mesh.obj,
        false,
        "stock",
        "stock_edge",
        "stock_edge",
        List.of(
            rib(1.6, 0, 1.65, 1.13), rib(3.0, 0, 1.75, 1.31),
            rib(6.2, 0, 1.25, .98), rib(9.6, 0, 1.23, 1.0),
            rib(11.8, 0, 1.60, 1.16), rib(20.6, 0, 1.35, .82)));
    mesh.obj.box("iron", 7.22, 9.35, 8.74, 7.62, 20.65, 9.30);
    mesh.obj.box("iron", 8.38, 9.35, 8.74, 8.78, 20.65, 9.30);
    mesh.obj.box("brass", 6.36, 17.9, 6.92, 9.64, 19.1, 9.4);
    mesh.obj.box("iron", 7.62, 10.1, 6.1, 8.38, 10.78, 7.2);

    double tip = 13.9 - 2.1 * pull, width = 9.65 - .84 * pull;
    loft(
        mesh.obj,
        true,
        "stock",
        "stock_edge",
        "stock_edge",
        List.of(
            rib(-width, tip - 8, .47, .47), rib(-7.2, 5.55, .67, .69),
            rib(-4.25, 8.95, .92, .70), rib(-1.1, 10.5, 1.05, .71),
            rib(1.1, 10.5, 1.05, .71), rib(4.25, 8.95, .92, .70),
            rib(7.2, 5.55, .67, .69), rib(width, tip - 8, .47, .47)));
    P notch = new P(8, 13.2 - 5.5 * pull, 9.45 - .35 * pull);
    for (int side : new int[] {-1, 1}) {
      double x = 8 + side * (width - .28);
      mesh.obj.box("brass", x - .58, tip - .35, 7.40, x + .58, tip + .61, 9.12);
      mesh.obj.beam("string", new P(x, tip + .04, 9.08), notch, .145, 4);
    }

    if (arrow) {
      mesh.patch("arrowhead", 2, 2, 4, 3);
      mesh.field("arrowshaft", 7, 7, 10, 8);
      mesh.obj.beam("arrowshaft", notch, new P(8, 21, 9.45), .26, 4);
      loft(
          mesh.obj,
          false,
          "arrowhead",
          "arrowhead",
          "iron",
          List.of(rib(20.75, 0, .79, .45), rib(22.55, 0, .06, .05)),
          9.45);
    } else if (firework) {
      mesh.field("fire_red", 0, 0, 3, 1);
      mesh.patch("fire_wrap", 5, 3, 6, 4);
      mesh.field("fire_fin", 7, 7, 8, 8);
      mesh.obj.box("fire_fin", 7.6, notch.y(), 9.10, 8.4, 13.7, 9.75);
      loft(
          mesh.obj,
          false,
          "fire_red",
          "fire_red",
          "fire_red",
          List.of(rib(13.25, 0, .64, .65), rib(20.05, 0, .64, .65), rib(21.65, 0, .07, .07)),
          9.82);
      mesh.obj.box("fire_wrap", 7.33, 15.4, 9.15, 8.67, 16.34, 10.49);
      mesh.obj.box("fire_wrap", 7.33, 17.8, 9.15, 8.67, 18.75, 10.49);
    }
  }

  private static void hilt(
      EquipmentObjMesh mesh, double bottom, double top, double gripRadius, double pommelRadius) {
    mesh.box("grip", 8 - gripRadius, bottom, 8 - gripRadius, 8 + gripRadius, top, 8 + gripRadius);
    loft(
        mesh,
        false,
        "pommel",
        "pommel",
        "pommel",
        List.of(
            rib(bottom - 1.9, 0, pommelRadius * .65, pommelRadius * .62),
            rib(bottom - 1.05, 0, pommelRadius, pommelRadius * .86),
            rib(bottom + .6, 0, pommelRadius * .87, pommelRadius * .77)));
  }

  private static void quillons(
      EquipmentObjMesh mesh, double y, double span, double bend, double depth, boolean bladeWard) {
    double tip = y + (bladeWard ? bend : -bend);
    mesh.plate(
        "guard",
        "guard_edge",
        List.of(
            new P(8 - span, tip - .57, 8),
            new P(8 - span + .4, tip + .62, 8),
            new P(6.75, y + .66, 8),
            new P(8, y + 1.20, 8),
            new P(9.25, y + .66, 8),
            new P(8 + span - .4, tip + .62, 8),
            new P(8 + span, tip - .57, 8),
            new P(9.17, y - .75, 8),
            new P(8, y - 1.02, 8),
            new P(6.83, y - .75, 8)),
        depth);
  }

  private record Rib(double advance, double center, double width, double thickness) {}

  private static Rib rib(double advance, double center, double width, double thickness) {
    return new Rib(advance, center, width, thickness);
  }

  private static void blade(EquipmentObjMesh mesh, Rib... ribs) {
    loft(mesh, false, "blade", "bevel", "dark", List.of(ribs));
  }

  private static void loft(
      EquipmentObjMesh mesh,
      boolean transverse,
      String flat,
      String bevel,
      String edge,
      List<Rib> ribs) {
    loft(mesh, transverse, flat, bevel, edge, ribs, 8);
  }

  private static void loft(
      EquipmentObjMesh mesh,
      boolean transverse,
      String flat,
      String bevel,
      String edge,
      List<Rib> ribs,
      double centerZ) {
    for (int row = 1; row < ribs.size(); row++) {
      Rib lower = ribs.get(row - 1), upper = ribs.get(row);
      P[] base = section(lower, transverse, centerZ), tip = section(upper, transverse, centerZ);
      P origin = point(lower, transverse, 0, centerZ);
      boolean changingBevel =
          Math.abs(lower.width() * upper.thickness() - upper.width() * lower.thickness()) > 1e-7;
      for (int side = 0; side < base.length; side++) {
        int next = (side + 1) % base.length;
        P outward = base[side].add(base[next]).scale(.5).add(origin.scale(-1));
        String material =
            switch (side) {
              case 0, 4 -> flat;
              case 2, 6 -> edge;
              default -> bevel;
            };
        if (changingBevel && side % 2 != 0) {
          // A bevel that changes slope between sections is two planes.
          mesh.quad(
              material,
              new P[] {base[side], base[next], tip[next], tip[next]},
              outward,
              new double[][] {QUAD_UV[0], QUAD_UV[1], QUAD_UV[2], QUAD_UV[2]});
          mesh.quad(
              material,
              new P[] {base[side], tip[next], tip[side], tip[side]},
              outward,
              new double[][] {QUAD_UV[0], QUAD_UV[2], QUAD_UV[3], QUAD_UV[3]});
        } else {
          mesh.quad(
              material, new P[] {base[side], base[next], tip[next], tip[side]}, outward, QUAD_UV);
        }
      }
    }
    for (int end : new int[] {0, ribs.size() - 1}) {
      Rib rib = ribs.get(end);
      P center = point(rib, transverse, 0, centerZ);
      P[] cap = section(rib, transverse, centerZ);
      double sign = end == 0 ? -1 : 1;
      P normal = transverse ? new P(sign, 0, 0) : new P(0, sign, 0);
      for (int side = 0; side < cap.length; side++) {
        P next = cap[(side + 1) % cap.length];
        mesh.quad(
            edge,
            new P[] {center, cap[side], next, next},
            normal,
            new double[][] {{.5, .5}, {0, 0}, {1, 0}, {1, 0}});
      }
    }
  }

  private static P[] section(Rib rib, boolean transverse, double centerZ) {
    double rim = rib.width(), face = rim * .52, depth = rib.thickness();
    double lip = depth * .22;
    return new P[] {
      point(rib, transverse, -face, centerZ + depth),
      point(rib, transverse, face, centerZ + depth),
      point(rib, transverse, rim, centerZ + lip),
      point(rib, transverse, rim, centerZ - lip),
      point(rib, transverse, face, centerZ - depth),
      point(rib, transverse, -face, centerZ - depth),
      point(rib, transverse, -rim, centerZ - lip),
      point(rib, transverse, -rim, centerZ + lip),
    };
  }

  private static P point(Rib rib, boolean transverse, double across, double z) {
    return transverse
        ? new P(8 + rib.advance(), 8 + rib.center() + across, z)
        : new P(8 + rib.center() + across, rib.advance(), z);
  }
}
