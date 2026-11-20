package dev.aeroscapes.sky.fauna;

import dev.aeroscapes.sky.fauna.CloudJellyAnatomy.*;
import java.util.UUID;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One swimming frame for propulsion, visible anatomy and native contact. */
public final class CloudJellyMotion {
  private final Shape shape;
  private final double lip, phase, contraction, turn;
  private final Vec3 wake;
  public final Basis basis;
  public final Vec3 current;
  public final double thrust;
  public final double period;

  public static long seed(UUID uuid) {
    return uuid.getLeastSignificantBits() ^ Long.rotateLeft(uuid.getMostSignificantBits(), 27);
  }

  public static CloudJellyMotion sample(
      Form form, float diameter, long seed, double time, float heading, boolean rest) {
    return new CloudJellyMotion(form, diameter, seed, time, ignored -> heading, rest);
  }

  CloudJellyMotion(
      Form form,
      float diameter,
      long seed,
      double time,
      DoubleUnaryOperator heading,
      boolean rest) {
    shape = CloudJellyAnatomy.of(form);
    lip = shape.strands().getFirst().segments().getFirst().top();
    double individual = (seed & 65535) / 65536.;
    double bearing = Math.toRadians(heading.applyAsDouble(time));
    period = (27 + 11 * Math.sqrt(diameter)) * (.92 + .16 * ((seed >>> 16 & 65535) / 65535.));
    double cycles = time / period + individual;
    phase = cycles - Math.floor(cycles);
    contraction = rest ? 0 : squeeze(phase);
    double steer = time / (185 + diameter * 4) + individual * Math.TAU;
    double azimuth = bearing + .68 * Math.sin(steer) + .21 * Math.sin(steer * .53 + individual);
    turn = rest ? 0 : Math.cos(steer);
    // A prolonged lateral traverse alternates with upright strokes without a discontinuous turn.
    basis = rest ? Basis.UPRIGHT : orientation(azimuth, steer);
    double lag = period * .55 / (185 + diameter * 4);
    double oldSteer = steer - lag;
    double oldAzimuth =
        Math.toRadians(heading.applyAsDouble(time - period * .55))
            + .68 * Math.sin(oldSteer)
            + .21 * Math.sin(oldSteer * .53 + individual);
    Vec3 oldAxis = orientation(oldAzimuth, oldSteer).y;
    wake = rest ? Vec3.ZERO : basis.inverse(oldAxis).subtract(0, 1, 0);
    double breeze = bearing + .30 * Math.sin(time / 315 + bearing);
    current =
        new Vec3(
            -Math.sin(breeze) * .043,
            .005 * Math.sin(time / 117 + bearing),
            Math.cos(breeze) * .043);
    thrust =
        rest || phase >= .23
            ? 0
            : Math.sin(Math.PI * phase / .23) * (.026 + .003 * Math.sqrt(diameter));
  }

  private static Basis orientation(double azimuth, double steer) {
    double inclination = Math.toRadians(45 + 37 * Math.sin(steer * .79));
    return new Basis(Math.cos(azimuth), Math.sin(azimuth), inclination);
  }

  private static double squeeze(double phase) {
    if (phase < .23) return smooth(phase / .23);
    if (phase < .62) return 1 - smooth((phase - .23) / .39);
    return 0;
  }

  private static double smooth(double value) {
    return value * value * (3 - 2 * value);
  }

  public Vec3 velocity(Vec3 previous) {
    // FlyingMob.travel moves by this velocity, then applies air friction of .91.
    return previous.scale(.94).add(current.scale(1 - .91 * .94)).add(basis.y.scale(thrust));
  }

  public double phase() {
    return phase;
  }

  public Shape shape() {
    return shape;
  }

  private double radial(double y) {
    double depth = Mth.clamp(y / lip, 0, 1.22);
    return 1 - contraction * (.035 + .22 * depth * depth);
  }

  private double level(double y) {
    return y + contraction * (.17 * Math.min(y, lip) - .60 * Math.max(0, y - lip));
  }

  // Keep the long, adjoining chord faces of each bell tier joined while the margin rolls inward.
  public AABB shell(int i) {
    ShellBox b = shape.bell().get(i);
    double a = radial(b.top()), c = radial(b.bottom());
    return new AABB(
        Math.min(edgeX(b.minX(), b.top()), edgeX(b.minX(), b.bottom())),
        level(b.top()),
        Math.min(b.minZ() * a, b.minZ() * c),
        Math.max(edgeX(b.maxX(), b.top()), edgeX(b.maxX(), b.bottom())),
        level(b.bottom()),
        Math.max(b.maxZ() * a, b.maxZ() * c));
  }

  private double edgeX(double x, double y) {
    if (basis == Basis.UPRIGHT) return x;
    double side = Mth.clamp(x / 8, -1, 1) * turn;
    double localPhase = phase + .045 * side;
    double stroke = squeeze(localPhase - Math.floor(localPhase));
    double depth = Mth.clamp(y / lip, 0, 1.22);
    return x * (1 - stroke * (.035 + .22 * depth * depth));
  }

  public AABB segment(int i, int j) {
    Strand strand = shape.strands().get(i);
    Segment b = strand.segments().get(j), root = strand.segments().getFirst();
    double y = root.top(), radius = radial(y);
    double x = b.x() + edgeX(root.x(), y) - root.x(), z = b.z() + root.z() * (radius - 1);
    double top = b.top() - y, bottom = b.bottom() - y;
    double waveA = wave(top, i, strand.ribbon()), waveB = wave(bottom, i, strand.ribbon());
    double dx1 = bendX(top, waveA), dx2 = bendX(bottom, waveB);
    double dz1 = bendZ(top, waveA, i), dz2 = bendZ(bottom, waveB, i);
    // Project the endpoints into one cuboid per original voxel section. Successive ends overlap.
    return new AABB(
        x + Math.min(dx1, dx2) - b.width() / 2,
        level(y) + top,
        z + Math.min(dz1, dz2) - b.depth() / 2,
        x + Math.max(dx1, dx2) + b.width() / 2,
        level(y) + bottom,
        z + Math.max(dz1, dz2) + b.depth() / 2);
  }

  private double wave(double depth, int i, boolean ribbon) {
    if (basis == Basis.UPRIGHT) return 0;
    double flex = depth / 30.;
    return flex
        * (ribbon ? .8 : 1.15)
        * Math.sin(Math.TAU * (phase - depth * (ribbon ? .024 : .017)) + i * .43);
  }

  private double bendX(double depth, double wave) {
    return -wake.x * depth * depth / 28 + wave;
  }

  private double bendZ(double depth, double wave, int i) {
    return -wake.z * depth * depth / 28
        + wave * Math.cos(i * 1.3)
        + (basis == Basis.UPRIGHT
            ? 0
            : depth * depth * .0018 * Math.sin(Math.TAU * phase - depth * .07));
  }

  /**
   * Orthogonal basis of the actual animal. Rotation is about a fixed crown above its native feet.
   */
  public static final class Basis {
    static final Basis UPRIGHT = new Basis(1, 0, 0);
    public final Vec3 x, y, z;
    public final double axisX, axisZ, angle;

    Basis(double ax, double az, double angle) {
      this.axisX = ax;
      this.axisZ = az;
      this.angle = angle;
      double s = Math.sin(angle), c = Math.cos(angle), v = 1 - c;
      x = new Vec3(c + ax * ax * v, az * s, ax * az * v);
      y = new Vec3(-az * s, c, ax * s);
      z = new Vec3(ax * az * v, -ax * s, c + az * az * v);
    }

    public Vec3 apply(Vec3 v) {
      return x.scale(v.x).add(y.scale(v.y)).add(z.scale(v.z));
    }

    public Vec3 inverse(Vec3 v) {
      return new Vec3(x.dot(v), y.dot(v), z.dot(v));
    }

    Vec3 radii(Vec3 h) {
      return new Vec3(
          Math.abs(x.x) * h.x + Math.abs(y.x) * h.y + Math.abs(z.x) * h.z,
          Math.abs(x.y) * h.x + Math.abs(y.y) * h.y + Math.abs(z.y) * h.z,
          Math.abs(x.z) * h.x + Math.abs(y.z) * h.y + Math.abs(z.z) * h.z);
    }
  }
}
