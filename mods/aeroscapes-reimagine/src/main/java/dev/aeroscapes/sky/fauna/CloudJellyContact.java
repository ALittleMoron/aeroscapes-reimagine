package dev.aeroscapes.sky.fauna;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Exact posed boxes grouped by native part. Coordinates are relative to the entity's feet. */
public final class CloudJellyContact {
  static final double MARGIN = .125;
  private final List<Group> groups;
  private final List<AABB> targets;
  private final AABB envelope;
  private final Vec3 bellCenter;

  public CloudJellyContact(CloudJellyAnatomy.Form form, double diameter) {
    this(CloudJellyMotion.sample(form, (float) diameter, 0, 0, 0, true), diameter);
  }

  public CloudJellyContact(CloudJellyMotion pose, double diameter) {
    double unit = diameter / CloudJellyAnatomy.SIZE_UNITS;
    var shape = pose.shape();
    Vec3 crown = new Vec3(0, shape.height() * unit, 0);
    var built = new ArrayList<Group>();
    var shell = new ArrayList<AABB>();
    for (int i = 0; i < shape.bell().size(); i++) shell.add(local(pose.shell(i), unit));
    var bell = CloudJellyTarget.of(shell, pose.basis, crown);
    bellCenter = bell.getCenter();
    built.add(new Group(bell, bellCenter));
    AABB all = bell;
    for (int i = 0; i < shape.strands().size(); i++) {
      var parts = new ArrayList<AABB>();
      for (int j = 0; j < shape.strands().get(i).segments().size(); j++)
        parts.add(local(pose.segment(i, j), unit));
      var group = CloudJellyTarget.of(parts, pose.basis, crown);
      var first = parts.getFirst();
      built.add(
          new Group(group, group.world(first.getCenter().with(Direction.Axis.Y, first.maxY))));
      all = all.minmax(group);
    }
    envelope = all;
    groups = List.copyOf(built);
    targets = built.stream().<AABB>map(Group::target).toList();
  }

  private static AABB local(AABB b, double unit) {
    return new AABB(
        b.minX * unit, -b.maxY * unit, b.minZ * unit, b.maxX * unit, -b.minY * unit, b.maxZ * unit);
  }

  Vec3 bellCenter() {
    return bellCenter;
  }

  AABB envelope() {
    return envelope;
  }

  List<AABB> targetBounds() {
    return targets;
  }

  @FunctionalInterface
  public interface Exposure {
    boolean exposed(double time, Vec3 root, Vec3 skin, Vec3 victimCenter);
  }

  public boolean touches(Vec3 previous, Vec3 current, AABB victimBox, Exposure exposure) {
    return touches(this, previous, current, victimBox, exposure);
  }

  public boolean touches(
      CloudJellyContact old, Vec3 previous, Vec3 current, AABB victimBox, Exposure exposure) {
    Vec3 half = CloudJellyTarget.half(victimBox).add(MARGIN, MARGIN, MARGIN);
    AABB sweep = new AABB(previous, current).inflate(half.x, half.y, half.z);
    if (!old.envelope.minmax(envelope).intersects(sweep)) return false;
    var first = old.groups.getFirst().target;
    var last = groups.getFirst().target;
    Vec3 from = first.local(previous), to = last.local(current);
    // OBB against an axis-aligned victim: the three axes of each box and their nine cross products.
    // Project both tick endpoints; animating edges and a ship crossing between ticks both count.
    var axes =
        CloudJellyTarget.separatingAxes(last.basis).stream()
            .map(n -> axis(n, first, last, half))
            .toList();
    for (int i = 0; i < groups.size(); i++) {
      Group a = old.groups.get(i), b = groups.get(i);
      if (!a.target.minmax(b.target).intersects(sweep)) continue;
      for (int j = 0; j < b.target.boxes.size(); j++) {
        AABB box0 = a.target.boxes.get(j), box1 = b.target.boxes.get(j);
        Vec3 offset0 = from.subtract(box0.getCenter()), offset1 = to.subtract(box1.getCenter());
        Vec3 size0 = CloudJellyTarget.half(box0), size1 = CloudJellyTarget.half(box1);
        double enter = 0, exit = 1;
        for (Axis axis : axes) {
          double p = axis.old.dot(offset0), q = axis.now.dot(offset1);
          double h = axis.victim + CloudJellyTarget.project(axis.old, size0);
          double k = axis.victim + CloudJellyTarget.project(axis.now, size1);
          // Each signed separating distance must be <= 0 at the same instant.
          for (int sign = -1; sign <= 1; sign += 2) {
            double u = sign * p - h, v = sign * q - k;
            if (u > 0 && v > 0) {
              enter = 2;
              break;
            }
            if (u > 0) enter = Math.max(enter, u / (u - v));
            else if (v > 0) exit = Math.min(exit, u / (u - v));
          }
          if (enter > exit) break;
        }
        if (enter > exit) continue;
        if (exposed(exit, a, b, box0, box1, from, to, previous, current, exposure)
            || (exit - enter > 1e-6
                && (exposed(enter, a, b, box0, box1, from, to, previous, current, exposure)
                    || exposed(
                        (enter + exit) / 2,
                        a,
                        b,
                        box0,
                        box1,
                        from,
                        to,
                        previous,
                        current,
                        exposure)))) return true;
      }
    }
    return false;
  }

  private static Axis axis(Vec3 n, CloudJellyTarget first, CloudJellyTarget last, Vec3 half) {
    return new Axis(
        first.basis.inverse(n), last.basis.inverse(n), CloudJellyTarget.project(n, half));
  }

  private static boolean exposed(
      double t,
      Group a,
      Group b,
      AABB box0,
      AABB box1,
      Vec3 from,
      Vec3 to,
      Vec3 previous,
      Vec3 current,
      Exposure exposure) {
    Vec3 p = from.lerp(to, t);
    Vec3 skin =
        new Vec3(
            Mth.clamp(p.x, Mth.lerp(t, box0.minX, box1.minX), Mth.lerp(t, box0.maxX, box1.maxX)),
            Mth.clamp(p.y, Mth.lerp(t, box0.minY, box1.minY), Mth.lerp(t, box0.maxY, box1.maxY)),
            Mth.clamp(p.z, Mth.lerp(t, box0.minZ, box1.minZ), Mth.lerp(t, box0.maxZ, box1.maxZ)));
    return exposure.exposed(
        t,
        a.root.lerp(b.root, t),
        a.target.world(skin).lerp(b.target.world(skin), t),
        previous.lerp(current, t));
  }

  private record Axis(Vec3 old, Vec3 now, double victim) {}

  private record Group(CloudJellyTarget target, Vec3 root) {}
}
