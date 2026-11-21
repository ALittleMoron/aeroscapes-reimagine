package dev.aeroscapes.aquatic;

import com.google.gson.JsonObject;
import dev.aeroscapes.aquatic.EquipmentObjMesh.P;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import org.joml.Vector3f;

/** Turned, cut and naturally crooked staves, with bindings drawn from their individual sprites. */
final class HexStaves {
  private HexStaves() {}

  static JsonObject display() {
    var grip = HexHeldMesh.point(0, 8.25, 0);
    var result = IronHeldMesh.display(grip, 1, 34, 12, -6);
    for (String hand : List.of("firstperson_righthand", "firstperson_lefthand")) {
      var pose = result.getAsJsonObject(hand);
      pose.add("rotation", IronHeldMesh.vector(-25, 15, 14));
      pose.add("scale", IronHeldMesh.vector(.78, .78, .78));
    }
    // Turn the asymmetric crown and loose cloth away from the standing left hip.
    result
        .getAsJsonObject("thirdperson_lefthand")
        .add("rotation", IronHeldMesh.vector(34, -168, 6));
    HeldItemModels.anchorHeld(result, new Vector3f(8, 8.25f, 8), new Vector3f(.12f, -.45f, .45f));
    return result;
  }

  static void build(HexHeldMesh builder, String wood) {
    var mesh = builder.mesh;
    for (var material :
        List.of(
            "wood",
            "twig",
            "wrap",
            "gem",
            "gem_shadow",
            "gem_flash",
            "shard",
            "shard_shadow",
            "shard_flash",
            "leaf")) mesh.projectedField(material);
    switch (wood) {
      case "oak" -> oak(mesh);
      case "spruce" -> spruce(mesh);
      case "birch" -> birch(mesh);
      case "jungle" -> jungle(mesh);
      case "acacia" -> acacia(mesh);
      case "dark_oak" -> darkOak(mesh);
      case "crimson" -> crimson(mesh);
      case "warped" -> warped(mesh);
      case "mangrove" -> mangrove(mesh);
      case "cherry" -> cherry(mesh);
      case "bamboo" -> bamboo(mesh);
      case "edified" -> edified(mesh);
      case "mindsplice" -> mindsplice(mesh);
      case "quenched_0" -> quenchedSpiral(mesh);
      case "quenched_1" -> quenchedFork(mesh);
      case "quenched_2" -> quenchedCrook(mesh);
      case "quenched_3" -> quenchedRod(mesh);
      default -> throw new IllegalArgumentException("Unknown staff " + wood);
    }
  }

  private static void oak(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {-.67, -6.90, .57},
          {.07, -5.3, .72},
          {.83, -3.47, .78},
          {.40, -.83, .71},
          {-.32, 1.04, .75},
          {0, 4.65, .74},
          {0, 11.32, .78},
          {-.70, 13.33, .82},
          {-.81, 15.42, 1.03},
          {.02, 16.85, 1.21},
          {.55, 18.20, 1.35}
        });
    tine(mesh, "twig", .04, .55, new double[][] {{.05, 16.68}, {1.47, 17.66}, {1.81, 19.74}});
    gem(mesh, Cut.SHARD, .43, 16.99, 0, 1.81, 7.43, -.61);
    binding(mesh, 0, 5.25, .91, 5, 3.55, 1);
  }

  private static void mindsplice(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {-.23, -7.05, 1.05},
          {.21, -6.20, .97},
          {.19, -4.43, .57},
          {-.45, -2.36, .78},
          {.08, 2.14, .67},
          {0, 6.10, .73},
          {0, 11.63, .78},
          {-.98, 14.62, 1.17},
          {-.49, 16.82, 1.39}
        });
    tine(
        mesh,
        "wood",
        -.12,
        1.28,
        new double[][] {
          {-.64, 14.66},
          {1.48, 16.71},
          {2.83, 19.37},
          {2.69, 21.87},
          {1.31, 23.67},
          {-.68, 24.69},
          {-1.73, 23.68}
        });
    tine(
        mesh,
        "twig",
        .18,
        1.00,
        new double[][] {{-.80, 15.57}, {-1.76, 17.21}, {-2.71, 19.30}, {-2.09, 20.84}});
    mineral(mesh, Cut.SHARD, -.78, 18.70, 0, 1.75, 4.10, -.18, "gem_flash", "gem_shadow", "gem");
    binding(mesh, 0, 4.56, .83, 5, 0, -1);
  }

  private static void spruce(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {0, -6.6, 1.12},
          {0, -5.49, 1.25},
          {0, -4.53, .68},
          {0, 7.7, .70},
          {0, 15.3, .81},
          {.09, 18.7, .90},
          {0, 19.72, 1.36}
        });
    gem(mesh, Cut.BUD, 0, 18.96, 0, 2.12, 5.03, -.12);
    binding(mesh, 0, 3.00, .83, 2, 0, 1);
    binding(mesh, 0, 9.45, .88, 2, 0, 1);
  }

  private static void birch(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {0, -6.58, .67},
          {.08, -2.4, .81},
          {0, 5.1, .74},
          {0, 10.9, .77},
          {-.16, 15.44, .98},
          {-.12, 17.01, 1.11},
          {.17, 19.32, 1.50}
        });
    tine(mesh, "twig", -.02, .60, new double[][] {{.21, 17.31}, {1.18, 18.62}, {1.49, 20.64}});
    gem(mesh, Cut.SHARD, .09, 18.98, 0, 1.68, 5.21, -.31);
    binding(mesh, 0, 4.66, .89, 3, 2.02, 1);
    binding(mesh, -.12, 13.83, 1.08, 2, 0, -1);
  }

  private static void jungle(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {0, -6.67, .75},
          {-.12, 2.4, .72},
          {0, 7.9, .77},
          {0, 13.4, .79},
          {.28, 16.21, .92},
          {0, 17.86, 1.74},
          {0, 19.74, 1.80},
          {0, 20.63, 1.25}
        });
    gem(mesh, Cut.BUD, 0, 19.84, -.11, 1.83, 4.24, .07);
    mineral(mesh, Cut.BUD, .06, 17.98, 1.47, .83, 1.69, -.09, "gem_flash", "gem", "gem_flash");
    tine(
        mesh,
        "gem_shadow",
        -.11,
        .61,
        new double[][] {{-.98, 19.73}, {-2.24, 20.26}, {-3.25, 19.38}, {-3.75, 17.92}});
    tine(
        mesh,
        "gem",
        -.04,
        .54,
        new double[][] {{1.06, 19.66}, {2.22, 20.17}, {3.19, 19.54}, {3.97, 18.01}});
    gem(mesh, Cut.SPLINTER, -2.72, 18.99, -.17, .75, 1.79, .52);
    gem(mesh, Cut.SPLINTER, 2.80, 19.13, .08, .68, 1.63, -.35);
    binding(mesh, 0, 4.52, .87, 5, 0, 1);
  }

  private static void acacia(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {.32, -6.68, .67},
          {-.47, -3.64, .80},
          {.18, .03, .85},
          {0, 5.01, .78},
          {0, 10.22, .82},
          {-1.29, 13.87, 1.13},
          {-1.76, 15.83, 1.22},
          {-1.00, 17.72, 1.42}
        });
    tine(
        mesh,
        "wood",
        -.03,
        1.46,
        new double[][] {
          {-1.45, 16.01}, {-.16, 18.26}, {1.57, 19.80}, {2.02, 21.72}, {1.31, 23.4}, {.04, 24.16}
        });
    gem(mesh, Cut.SPLINTER, -1.07, 18.56, .10, 1.38, 4.25, .88);
    binding(mesh, 0, 4.74, .94, 4, 2.58, 1);
  }

  private static void darkOak(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {1.16, -6.76, .69},
          {.25, -4.21, .77},
          {-.83, -1.83, .89},
          {-.66, .36, .76},
          {0, 4.89, .77},
          {0, 9.53, .80},
          {.90, 12.16, .87},
          {-.10, 14.83, .99},
          {-.6, 16.08, 1.10}
        });
    tine(
        mesh,
        "wood",
        -.11,
        1.29,
        new double[][] {
          {-.42, 15.31},
          {1.20, 17.49},
          {2.51, 19.32},
          {2.44, 21.34},
          {.84, 23.22},
          {-.68, 24.01},
          {-1.44, 23.51}
        });
    gem(mesh, Cut.SHARD, -.36, 18.13, .11, 1.72, 3.91, -.69);
    binding(mesh, 0, 5.46, .91, 3, 2.72, 1);
  }

  private static void crimson(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {.05, -6.64, .62}, {0, -1.21, .70}, {0, 8.12, .74}, {0, 14.73, .79}, {-.22, 16.36, .92}
        });
    tine(
        mesh,
        "wood",
        -.10,
        1.15,
        new double[][] {
          {-.26, 15.98}, {1.24, 17.43}, {3.30, 19.90}, {1.31, 22.36}, {-.28, 24.0}, {-1.15, 23.12}
        });
    gem(mesh, Cut.BUD, -.67, 18.44, .04, 1.82, 3.96, -.31);
    tine(mesh, "twig", .09, .59, new double[][] {{-.05, 16.06}, {-.93, 17.31}, {-1.45, 19.05}});
    binding(mesh, 0, 4.65, .87, 4, 2.37, 1);
  }

  private static void warped(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {-.28, -6.58, .67}, {.01, -3.30, .82}, {0, 5.05, .75}, {0, 13.91, .78}, {.29, 16.69, .96}
        });
    tine(
        mesh,
        "wood",
        -.06,
        1.24,
        new double[][] {
          {.36, 15.85},
          {-.83, 17.15},
          {-2.71, 18.8},
          {-3.33, 20.20},
          {-1.52, 22.63},
          {.13, 24.11},
          {1.02, 23.34}
        });
    gem(mesh, Cut.SHARD, .71, 18.18, .14, 1.64, 4.08, .27);
    tine(mesh, "twig", .07, .51, new double[][] {{.17, 16.29}, {1.14, 17.55}, {1.37, 19.27}});
    binding(mesh, 0, 4.72, .88, 4, 2.49, 1);
  }

  private static void mangrove(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {-.19, -6.61, .63},
          {.28, -3.13, .81},
          {-.22, .27, .70},
          {0, 4.85, .77},
          {0, 9.91, .78},
          {.44, 12.32, .96},
          {.14, 16.0, 1.12},
          {-.65, 19.7, 1.03},
          {-.58, 20.79, 1.23}
        });
    gem(mesh, Cut.SHARD, -.57, 20.02, 0, 1.45, 4.21, -.14);
    tine(
        mesh,
        "wood",
        -.05,
        .70,
        new double[][] {
          {.09, 11.53}, {-1.11, 13.17}, {-3.05, 14.10}, {-4.41, 13.60}, {-5.43, 12.51}
        });
    gem(mesh, Cut.BUD, -2.56, 10.08, -.03, 1.23, 2.97, .42);
    tine(mesh, "twig", .19, .46, new double[][] {{-3.15, 13.56}, {-2.61, 12.82}, {-2.17, 12.33}});
    tine(
        mesh,
        "twig",
        -.15,
        .57,
        new double[][] {{.41, 11.72}, {1.93, 13.35}, {3.00, 15.28}, {3.69, 17.26}});
    mineral(mesh, Cut.SPLINTER, 2.56, 13.43, -.15, 1.10, 5.78, 1.44, "leaf", "twig", "leaf");
    binding(mesh, 0, 4.75, .87, 3, 2.44, 1);
  }

  private static void cherry(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {.04, -6.54, .66},
          {-.10, .61, .70},
          {0, 6.28, .72},
          {0, 12.55, .71},
          {0, 16.34, .78},
          {0, 18.59, .97}
        });
    gem(mesh, Cut.BUD, -.13, 17.56, -.10, 1.88, 6.45, .06);
    gem(mesh, Cut.SPLINTER, -1.13, 17.97, -.08, 1.31, 3.42, -1.29);
    gem(mesh, Cut.SPLINTER, 1.14, 17.92, .04, 1.26, 3.26, 1.49);
    gem(mesh, Cut.BUD, .03, 18.12, .67, 1.26, 2.64, .04);
    binding(mesh, 0, 4.19, .84, 5, 0, 1);
  }

  private static void bamboo(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {-.02, -6.66, .65},
          {0, -3.16, .68},
          {0, -2.94, .85},
          {0, -2.40, .74},
          {0, 6.10, .71},
          {0, 11.89, .77},
          {0, 12.26, .97},
          {0, 12.74, .85},
          {-.19, 16.45, 1.12},
          {-.03, 18.04, 1.41},
          {.03, 19.17, 1.12}
        });
    tine(
        mesh,
        "twig",
        -.02,
        .65,
        new double[][] {{-.08, 15.2}, {-1.27, 16.6}, {-1.64, 18.8}, {-1.41, 20.08}});
    tine(
        mesh,
        "twig",
        .12,
        .77,
        new double[][] {{.09, 16.38}, {1.12, 17.68}, {1.38, 19.53}, {.89, 20.36}});
    gem(mesh, Cut.SHARD, -.09, 18.61, -.06, 1.49, 5.62, .29);
    binding(mesh, 0, 4.41, .87, 3, 2.5, 1);
  }

  private static void edified(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "wood",
        new double[][] {
          {.46, -6.53, .82},
          {-.55, -4.72, .88},
          {-1.09, -2.24, .78},
          {.12, .50, .87},
          {.57, 2.12, .81},
          {0, 5.1, .76},
          {0, 10.80, .77},
          {.81, 12.89, .93},
          {1.45, 14.20, 1.12},
          {.19, 16.22, .98},
          {-1.02, 18.36, 1.06},
          {-.62, 19.63, .89}
        });
    tine(
        mesh,
        "twig",
        -.09,
        .85,
        new double[][] {{-.04, 16.38}, {-1.27, 18.48}, {-1.58, 20.05}, {-1.13, 21.25}});
    gem(mesh, Cut.SHARD, -.22, 18.94, .12, 1.69, 5.14, .36);
    binding(mesh, 0, 5.19, .90, 4, 0, -1);
  }

  private static void quenchedSpiral(EquipmentObjMesh mesh) {
    // Blue allay crystal and violet media grow through the same twisted, faceted body.
    double[][] sections = {
      {.18, -6.82, .34},
      {.05, -5.97, 1.34},
      {-.45, -3.91, 1.39},
      {-.18, -1.39, 1.18},
      {.42, .80, 1.32},
      {.17, 3.09, 1.10},
      {0, 4.98, .81},
      {0, 8.34, .79},
      {0, 10.55, .84},
      {.39, 12.81, 1.23},
      {.62, 15.21, 1.44},
      {-.12, 17.61, 1.52},
      {-.41, 19.97, 1.47},
      {.24, 22.37, 1.45},
      {.03, 24.39, .40}
    };
    int sides = 7;
    P[][] rings = new P[sections.length][sides];
    for (int i = 0; i < sections.length; i++) {
      var section = sections[i];
      for (int j = 0; j < sides; j++) {
        double angle = Math.toRadians(22 + i * 39 + j * 360.0 / sides);
        rings[i][j] =
            HexHeldMesh.point(
                section[0] + Math.cos(angle) * section[2],
                section[1],
                Math.sin(angle) * section[2] * .92);
      }
    }
    String[] seams = {
      "gem_shadow", "gem", "gem_flash", "shard_shadow", "shard", "shard_flash", "shard"
    };
    for (int i = 0; i < rings.length - 1; i++) {
      for (int j = 0; j < sides; j++) {
        int n = (j + 1) % sides;
        P a = rings[i][j], b = rings[i][n], c = rings[i + 1][n], d = rings[i + 1][j];
        double direction = Math.toRadians(22 + (i + .5) * 39 + (j + .5) * 360.0 / sides);
        P normal = new P(Math.cos(direction), 0, Math.sin(direction));
        mesh.quad(seams[j], new P[] {a, b, c, c}, normal, null);
        mesh.quad(seams[j], new P[] {a, c, d, d}, normal, null);
      }
    }
    for (int i : new int[] {0, rings.length - 1}) {
      P center = HexHeldMesh.point(sections[i][0], sections[i][1], 0);
      for (int j = 0; j < sides; j++) {
        P n = rings[i][(j + 1) % sides];
        mesh.quad(
            i == 0 ? "gem" : "shard_flash",
            new P[] {center, rings[i][j], n, n},
            new P(0, i == 0 ? -1 : 1, 0),
            null);
      }
    }
    binding(mesh, 0, 5.15, .94, 4, 0, -1);
  }

  private static void quenchedFork(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "shard",
        new double[][] {{0, -5.15, .72}, {0, -1.87, .68}, {0, 5.13, .73}, {0, 8.41, .71}});
    double[] heights = {8.32, 10.55, 15.04, 18.45, 20.83};
    double[] radii = {.72, .73, .69, .71, .79};
    P[][] upper = new P[heights.length][];
    for (int i = 0; i < upper.length; i++)
      upper[i] = EquipmentObjMesh.octagonalRing(8, heights[i], 8, radii[i], radii[i] * .92);
    // The long, pale cleavage faces are part of the purple crystal shaft itself.
    for (int side = 0; side < 8; side++) {
      String material =
          switch (side) {
            case 1, 5 -> "gem_flash";
            case 3, 7 -> "gem_shadow";
            default -> "gem";
          };
      int next = (side + 1) % 8;
      double normal = (side + .5) * Math.PI / 4;
      for (int i = 1; i < upper.length; i++)
        mesh.quad(
            material,
            new P[] {upper[i - 1][side], upper[i - 1][next], upper[i][next], upper[i][side]},
            new P(Math.cos(normal), 0, Math.sin(normal)),
            null);
      for (int end : new int[] {0, upper.length - 1}) {
        P n = upper[end][next];
        mesh.quad(
            material,
            new P[] {HexHeldMesh.point(0, heights[end], 0), upper[end][side], n, n},
            new P(0, end == 0 ? -1 : 1, 0),
            null);
      }
    }
    gem(mesh, Cut.BUD, .07, -6.64, 0, 1.40, 2.97, -.08);
    tine(
        mesh, "gem_shadow", .04, .38, new double[][] {{-.39, -4.9}, {-1.04, -5.54}, {-1.43, -6.8}});
    tine(mesh, "gem", -.05, .36, new double[][] {{.23, -4.92}, {1.05, -5.59}, {1.31, -6.55}});
    blueShard(mesh, Cut.BUD, 0, 20.04, .06, 1.38, 3.89, 0);
    tine(
        mesh,
        "shard_shadow",
        -.09,
        .38,
        new double[][] {{-.59, 21.08}, {-1.02, 22.42}, {-1.39, 24.09}});
    tine(mesh, "shard", .11, .42, new double[][] {{.55, 21.22}, {1.12, 22.57}, {1.36, 24.12}});
    binding(mesh, 0, 6.68, .85, 3, 0, 1);
  }

  private static void quenchedCrook(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "shard",
        new double[][] {
          {1.05, -6.79, .61},
          {-.35, -4.92, .75},
          {-1.17, -2.77, .81},
          {-1.08, -.39, .77},
          {-.41, 2.30, .72},
          {0, 5.13, .73},
          {0, 10.45, .75},
          {.95, 12.12, .86},
          {1.89, 13.87, .96}
        });
    tine(
        mesh,
        "gem",
        -.03,
        .97,
        new double[][] {
          {1.55, 13.28},
          {2.30, 15.82},
          {2.49, 18.20},
          {1.84, 20.50},
          {.32, 22.67},
          {-1.12, 23.51},
          {-2.36, 22.65}
        });
    gem(mesh, Cut.SHARD, 1.91, 16.98, .11, 1.19, 4.02, -1.13);
    blueShard(mesh, Cut.BUD, -1.55, 19.82, -.01, 1.54, 3.33, -.62);
    binding(mesh, 0, 5.23, .88, 4, 0, 1);
  }

  private static void quenchedRod(EquipmentObjMesh mesh) {
    pole(
        mesh,
        "shard",
        new double[][] {
          {0, -5.92, .67}, {0, -1.16, .73}, {0, 6.75, .70}, {0, 14.92, .72}, {0, 18.7, .94}
        });
    gem(mesh, Cut.BUD, 0, -6.61, 0, 1.20, 2.32, .04);
    blueShard(mesh, Cut.BUD, 0, 16.96, 0, 1.57, 4.28, -.14);
    gem(mesh, Cut.BUD, 0, 19.29, .05, 2.09, 4.91, .08);
    binding(mesh, 0, 5.25, .81, 4, 0, 1);
  }

  /** A square section follows the changing centre and tapers through each (x, y, radius). */
  private static void pole(EquipmentObjMesh mesh, String material, double[][] path) {
    P[][] rings = new P[path.length][];
    for (int i = 0; i < rings.length; i++) {
      double[] station = path[i];
      rings[i] = rectangle(station[0], station[1], 0, station[2], station[2] * .91, 0);
    }
    mesh.shell(material, rings);
  }

  private static P[] rectangle(double x, double y, double z, double r, double d, double tilt) {
    return new P[] {
      HexHeldMesh.point(x - r, y - tilt, z - d),
      HexHeldMesh.point(x + r, y + tilt, z - d),
      HexHeldMesh.point(x + r, y + tilt, z + d),
      HexHeldMesh.point(x - r, y - tilt, z + d)
    };
  }

  // A socket and crescent can cross successive heights in either direction.
  private static void tine(
      EquipmentObjMesh mesh, String material, double z, double width, double[][] path) {
    int count = path.length;
    P[][] rings = new P[count][4];
    for (int i = 0; i < count; i++) {
      double[] before = path[Math.max(0, i - 1)], after = path[Math.min(count - 1, i + 1)];
      double dx = after[0] - before[0], dy = after[1] - before[1], length = Math.hypot(dx, dy);
      double w = width * (i == count - 1 ? .23 : i == count - 2 ? .82 : 1.0),
          d = width * (i == count - 1 ? .44 : 1.04);
      dx *= w / length;
      dy *= w / length;
      double x = path[i][0], y = path[i][1];
      rings[i] =
          new P[] {
            HexHeldMesh.point(x - dy, y + dx, z - d),
            HexHeldMesh.point(x + dy, y - dx, z - d),
            HexHeldMesh.point(x + dy, y - dx, z + d),
            HexHeldMesh.point(x - dy, y + dx, z + d)
          };
    }
    mesh.shell(material, rings);
  }

  private enum Cut {
    SHARD,
    BUD,
    SPLINTER
  }

  private static void gem(
      EquipmentObjMesh mesh,
      Cut cut,
      double x,
      double y,
      double z,
      double w,
      double h,
      double lean) {
    mineral(mesh, cut, x, y, z, w, h, lean, "gem", "gem_shadow", "gem_flash");
  }

  private static void blueShard(
      EquipmentObjMesh mesh,
      Cut cut,
      double x,
      double y,
      double z,
      double w,
      double h,
      double lean) {
    mineral(mesh, cut, x, y, z, w, h, lean, "shard", "shard_shadow", "shard_flash");
  }

  private static void mineral(
      EquipmentObjMesh mesh,
      Cut cut,
      double x,
      double y,
      double z,
      double w,
      double h,
      double lean,
      String face,
      String edge,
      String flash) {
    double[][] corners =
        switch (cut) {
          case SHARD ->
              new double[][] {
                {-.66, 0},
                {.56, .03},
                {1.00, .27},
                {.93, .67},
                {.41, .86},
                {.04, 1},
                {-.39, .98},
                {-.97, .65},
                {-.89, .24}
              };
          case BUD ->
              new double[][] {
                {-.26, 0},
                {.42, .03},
                {1, .32},
                {1.03, .52},
                {.64, .79},
                {.13, 1},
                {-.42, .89},
                {-1.03, .58},
                {-.90, .29}
              };
          case SPLINTER ->
              new double[][] {
                {-.48, 0},
                {.33, .02},
                {.83, .19},
                {.75, .52},
                {.48, .79},
                {-.12, 1},
                {-.43, .74},
                {-.80, .44},
                {-.85, .18}
              };
        };
    int count = corners.length;
    for (int side : new int[] {-1, 1}) {
      P ridge = HexHeldMesh.point(x + lean * .45 - w * .12, y + h * .52, z + side * w * .91);
      P[] rim = new P[count], field = new P[count], other = new P[count];
      for (int i = 0; i < count; i++) {
        double dx = corners[i][0], rise = corners[i][1];
        rim[i] = HexHeldMesh.point(x + lean * rise + w * dx, y + h * rise, z + side * w * .32);
        other[i] = HexHeldMesh.point(x + lean * rise + w * dx, y + h * rise, z - side * w * .32);
        field[i] =
            HexHeldMesh.point(
                x + lean * rise + w * dx * .52, y + h * (.19 + rise * .62), z + side * w * .77);
      }
      for (int i = 0; i < count; i++) {
        int n = (i + 1) % count;
        P outward = new P(0, 0, side);
        String outer = i < 2 || i > 6 ? edge : face;
        mesh.quad(outer, new P[] {rim[i], rim[n], field[n], field[n]}, outward, null);
        mesh.quad(outer, new P[] {rim[i], field[n], field[i], field[i]}, outward, null);
        mesh.quad(
            i >= 3 && i <= 6 ? flash : face,
            new P[] {field[i], field[n], ridge, ridge},
            outward,
            null);
        if (side > 0)
          mesh.quad(
              edge,
              new P[] {rim[i], rim[n], other[n], other[i]},
              new P(rim[n].y() - rim[i].y(), rim[i].x() - rim[n].x(), 0),
              null);
      }
    }
  }

  /** Overlapping bias-cut turns, with each external ridge leading diagonally round the pole. */
  private static void binding(
      EquipmentObjMesh mesh,
      double x,
      double bottom,
      double radius,
      int turns,
      double tail,
      int side) {
    double pitch = 1.13;
    double top = bottom + turns * pitch;
    mesh.shell(
        "wrap",
        new P[][] {
          rectangle(x, bottom, 0, radius * .85, radius * .80, 0),
          rectangle(x, top - .08, 0, radius * .85, radius * .80, 0)
        });
    // The same ribbon continues across the back and returns one pitch higher on the front.
    double[][] rim = {
      {.78, 1}, {-.78, 1}, {-1, .78}, {-1, -.78}, {-.78, -1}, {.78, -1}, {1, -.78}, {1, .78}
    };
    double corner = (Math.sqrt(2) * .22) / (4 * (1.56 + Math.sqrt(2) * .22));
    P[][] turnsOfRibbon = new P[turns * rim.length + 1][];
    for (int i = 0; i < turnsOfRibbon.length; i++) {
      int index = i % rim.length;
      double travelled = (index / 2) * .25 + (index % 2 == 0 ? 0 : .25 - corner);
      double low = bottom - .55 + (i / rim.length + travelled) * pitch;
      double high = low + pitch * 1.13;
      double dx = side * rim[index][0], dz = rim[index][1] * .94;
      turnsOfRibbon[i] =
          new P[] {
            HexHeldMesh.point(x + dx * (radius + .015), low, dz * (radius + .015)),
            HexHeldMesh.point(x + dx * (radius + .12), high, dz * (radius + .12)),
            HexHeldMesh.point(x + dx * (radius - .12), high - .05, dz * (radius - .12)),
            HexHeldMesh.point(x + dx * (radius - .13), low + .05, dz * (radius - .13))
          };
    }
    mesh.shell("ribbon", turnsOfRibbon);
    if (tail == 0) return;
    double y = top - .15;
    mesh.shell(
        "wrap",
        new P[][] {
          rectangle(x + side * (radius - .18), y, .65, .36, .25, side * .31),
          rectangle(x + side * (radius + .72), y - .63, .58, .70, .13, side * .51),
          rectangle(x + side * (radius + tail * .9), y - 2.09, .98, .78, .13, side * .38),
          rectangle(x + side * (radius + tail + .49), y - 3.01, 1.48, .58, .11, side * .40)
        });
    mesh.shell(
        "wrap",
        new P[][] {
          rectangle(x + side * (radius - .19), y - .33, .72, .34, .22, side * .27),
          rectangle(x + side * (radius + .17), y - 1.44, 1.03, .54, .11, side * .38),
          rectangle(x + side * (radius + tail * .36), y - 3.12, 1.46, .58, .10, side * .32),
          rectangle(x + side * (radius + tail * .48), y - 5.33, 1.69, .49, .09, side * .31)
        });
  }

  static JsonObject textures(
      String sprite, String wood, Set<String> materials, Path provider, Map<Path, byte[]> output)
      throws IOException {
    var result = new JsonObject();
    result.addProperty("particle", sprite);
    for (var material : materials) {
      int[] colors =
          switch (material) {
            case "wood" -> grain(wood);
            case "twig" ->
                wood.equals("mangrove") ? new int[] {0x3a4c26, 0x50692c, 0x70922d} : grain(wood);
            case "leaf" -> new int[] {0x50692c, 0x6c8031, 0x70922d};
            case "wrap", "ribbon" ->
                switch (wood) {
                  case "mindsplice" -> new int[] {0x191425, 0x382b44, 0x55455e};
                  case "edified" -> new int[] {0x43225b, 0x7f418d, 0x914b97};
                  case "quenched_0", "quenched_1" -> new int[] {0x54398a, 0x8d6acc, 0xcfa0f3};
                  case "quenched_2", "quenched_3" -> new int[] {0x09539b, 0x1b90c0, 0x2db6d0};
                  default -> new int[] {0x893b25, 0xc65c35, 0xd76b43};
                };
            case "gem" -> new int[] {0x6f4fab, 0x8d6acc, 0xcfa0f3};
            case "gem_shadow" -> new int[] {0x54398a, 0x54398a, 0x6f4fab};
            case "gem_flash" -> new int[] {0xcfa0f3, 0xfecbe6, 0xfffdd5};
            case "shard" -> new int[] {0x09539b, 0x1b90c0, 0x2db6d0};
            case "shard_shadow" -> new int[] {0x00337e, 0x09539b, 0x1b90c0};
            case "shard_flash" -> new int[] {0x2db6d0, 0x6fe4d3, 0x6fe4d3};
            default -> throw new IllegalArgumentException("Unknown staff material " + material);
          };
      String id = "item/equipment/hex/staff/" + material + "_" + Integer.toHexString(colors[1]);
      Path file = provider.resolve("textures/" + id + ".png");
      if (!output.containsKey(file)) output.put(file, field(material, colors));
      result.addProperty(material, "hexcasting:" + id);
    }
    return result;
  }

  private static int[] grain(String wood) {
    return switch (wood) {
      case "oak" -> new int[] {0x5f4a2b, 0x967441, 0xaf8f55};
      case "spruce" -> new int[] {0x553a1f, 0x70522e, 0x886539};
      case "birch" -> new int[] {0xae9f76, 0xd7c185, 0xd7cb8d};
      case "jungle" -> new int[] {0x7d5d26, 0xaa7954, 0xb88764};
      case "acacia" -> new int[] {0x7a3820, 0xad5d32, 0xc26d3f};
      case "dark_oak" -> new int[] {0x1f180d, 0x3a2411, 0x53381a};
      case "crimson" -> new int[] {0x4b2737, 0x863e5a, 0x924160};
      case "warped" -> new int[] {0x1f5752, 0x398382, 0x369d91};
      case "mangrove" -> new int[] {0x410e12, 0x642423, 0x7f4234};
      case "cherry" -> new int[] {0xcd8580, 0xdd9d97, 0xe7beb4};
      case "bamboo" -> new int[] {0x5a672a, 0x758333, 0xb9db5f};
      case "edified" -> new int[] {0x43225b, 0x69367c, 0x914b97};
      case "mindsplice" -> new int[] {0x191425, 0x44354e, 0x55455e};
      default -> throw new IllegalArgumentException("Unknown wood " + wood);
    };
  }

  private static byte[] field(String material, int[] colors) throws IOException {
    int size = 96;
    var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    var random = new SplittableRandom(colors[1]);
    int base = colors[1];
    for (int y = 0; y < size; y++)
      for (int x = 0; x < size; x++)
        image.setRGB(x, y, EquipmentModels.shaded(base, random.nextInt(-3, 4)));
    boolean grain = material.equals("wood") || material.equals("twig");
    for (int i = 0; i < (grain ? 680 : 430); i++) {
      int x = random.nextInt(size), y = random.nextInt(size);
      int color = mix(base, colors[random.nextBoolean() ? 0 : 2], grain ? .60 : .26);
      int length = grain ? random.nextInt(2, 7) : random.nextInt(1, 4);
      for (int j = 0; j < length; j++) image.setRGB(x, (y + j) % size, color);
    }
    if (material.equals("ribbon")) {
      // The first UV quarter spans the broad outer face. Its hem continues onto the folded edge.
      for (int x = size * 19 / 96; x < size / 2; x++)
        for (int y = 0; y < size; y++) image.setRGB(x, y, mix(image.getRGB(x, y), colors[0], .77));
    }
    return EquipmentModels.png(image);
  }

  private static int mix(int a, int b, double fraction) {
    int value = 0;
    for (int shift : new int[] {0, 8, 16})
      value |=
          (int) Math.round((a >> shift & 255) * (1 - fraction) + (b >> shift & 255) * fraction)
              << shift;
    return value;
  }
}
