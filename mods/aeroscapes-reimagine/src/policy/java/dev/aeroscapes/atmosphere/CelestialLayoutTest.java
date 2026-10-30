package dev.aeroscapes.atmosphere;

import java.io.IOException;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Player-visible direction, visibility and time-continuity contracts, without a client. */
public final class CelestialLayoutTest {
  private CelestialLayoutTest() {}

  public static void main(String[] arguments) throws IOException {
    skyRenderContract();
    var north = CelestialLayout.BODIES.getLast();
    var reference = CelestialLayout.direction(north, 0.5, 18000, 0);
    require(reference.z() < 0 && reference.y() > 0 && reference.x() == 0, "north is north");
    for (double angle : new double[] {0, 0.25, 0.5, 0.75, 1}) {
      require(
          reference.equals(CelestialLayout.direction(north, angle, Long.MAX_VALUE, 0.7)),
          "guide star moves with time");
    }
    for (var body : CelestialLayout.BODIES) {
      for (long ticks : new long[] {-1, 0, 18000, 24000, Long.MAX_VALUE}) {
        var direction = CelestialLayout.direction(body, 0.5, ticks, 0.5);
        near(
            direction.x() * direction.x()
                + direction.y() * direction.y()
                + direction.z() * direction.z(),
            1,
            "sky direction changes distance");
      }
      if (body.driftDays() == 0) continue;
      long period = body.driftDays() * 24000L;
      var start = CelestialLayout.direction(body, 0.5, 0, 0);
      var wrapped = CelestialLayout.direction(body, 0.5, period, 0);
      near(distance(start, wrapped), 0, "planet jumps at its period boundary");
      var preceding = CelestialLayout.direction(body, 0.5, period - 1, 0.999);
      require(distance(start, preceding) < 1.0E-6, "planet motion is discontinuous");
      var old = CelestialLayout.direction(body, 0.5, Long.MAX_VALUE, 0.5);
      var reduced =
          CelestialLayout.direction(body, 0.5, Math.floorMod(Long.MAX_VALUE, period), 0.5);
      near(distance(old, reduced), 0, "old world loses orbit precision");
      var midnight = CelestialLayout.direction(body, 0.5, 18000, 0);
      require(
          CelestialLayout.opacity(midnight, 0.5, 0.5F, 0, 0) > 0.99,
          "initial midnight hides a planet");
    }
    var accent = CelestialLayout.BODIES.get(3);
    var midnight = CelestialLayout.direction(accent, 0.5, 18000, 0);
    var quarterTurn = CelestialLayout.direction(accent, 0.75, 18000, 0);
    near(quarterTurn.x(), -midnight.y(), "accent star does not follow native sky rotation");
    near(quarterTurn.y(), midnight.x(), "accent star rotates on the wrong axis");
    near(quarterTurn.z(), midnight.z(), "accent star changes celestial latitude");
    require(CelestialLayout.opacity(reference, 0.5, 0, 0, 0) == 0, "visible in daylight");
    require(CelestialLayout.opacity(reference, 0.5, 0.5F, 1, 0) == 0, "visible in full rain");
    require(CelestialLayout.opacity(reference, 0.5, 0.5F, 0, 1) == 0, "visible in storm");
    float previous = 1;
    for (int step = 0; step <= 100; step++) {
      float opacity = CelestialLayout.opacity(reference, 0.5, 0.5F, step / 100F, 0);
      require(opacity <= previous, "rain makes a star brighter");
      previous = opacity;
    }
    for (double height : new double[] {-1, -0.1, 0}) {
      require(
          CelestialLayout.opacity(new CelestialLayout.Direction(0, height, -1), 0.5, 0.5F, 0, 0)
              == 0,
          "draws below horizon");
    }
    for (double angle : new double[] {0, 0.2, 0.5, 0.8}) {
      var sun =
          new CelestialLayout.Direction(-Math.sin(angle * Math.TAU), Math.cos(angle * Math.TAU), 0);
      var moon = new CelestialLayout.Direction(-sun.x(), -sun.y(), 0);
      require(CelestialLayout.opacity(sun, angle, 0.5F, 0, 0) == 0, "draws over sun");
      require(CelestialLayout.opacity(moon, angle, 0.5F, 0, 0) == 0, "draws over moon");
    }
    System.out.println(
        "Celestials: north, unit directions, midnight planets, time wraps and fades passed");
  }

  private static void skyRenderContract() throws IOException {
    ClassNode type = new ClassNode();
    try (var input =
        CelestialLayoutTest.class.getResourceAsStream(
            "/net/minecraft/client/renderer/LevelRenderer.class")) {
      if (input == null) throw new AssertionError("Missing mapped sky renderer");
      new ClassReader(input).accept(type, ClassReader.SKIP_DEBUG);
    }
    var method =
        type.methods.stream()
            .filter(
                candidate ->
                    candidate.name.equals("renderSky")
                        && candidate.desc.equals(
                            "(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Sky render arguments changed"));
    int boundaries = 0;
    boolean stars = false;
    for (var instruction : method.instructions) {
      if (!(instruction instanceof MethodInsnNode call)) continue;
      if (call.name.equals("getStarBrightness")) stars = true;
      if (call.owner.equals("com/mojang/blaze3d/systems/RenderSystem")
          && call.name.equals("disableBlend")) {
        boundaries++;
        require(stars, "sky hook moved before the native star phase");
      }
    }
    require(boundaries == 1, "custom sky no longer has one blended sky boundary");
  }

  private static double distance(
      CelestialLayout.Direction first, CelestialLayout.Direction second) {
    return Math.sqrt(
        Math.pow(first.x() - second.x(), 2)
            + Math.pow(first.y() - second.y(), 2)
            + Math.pow(first.z() - second.z(), 2));
  }

  private static void near(double actual, double expected, String message) {
    require(Math.abs(actual - expected) < 1.0E-10, message);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
