package dev.aeroscapes.sky.fauna;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** A native multipart envelope whose actual strike surfaces are the rotated component boxes. */
final class CloudJellyTarget extends AABB {
  final List<AABB> boxes;
  final CloudJellyMotion.Basis basis;
  final Vec3 crown;
  private final Vec3 padding;

  private CloudJellyTarget(
      AABB bounds, List<AABB> boxes, CloudJellyMotion.Basis basis, Vec3 crown, Vec3 padding) {
    super(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
    this.boxes = boxes;
    this.basis = basis;
    this.crown = crown;
    this.padding = padding;
  }

  static CloudJellyTarget of(List<AABB> boxes, CloudJellyMotion.Basis basis, Vec3 crown) {
    AABB bounds = null;
    for (AABB box : boxes) {
      Vec3 center = basis.apply(box.getCenter()).add(crown);
      Vec3 h = basis.radii(half(box));
      AABB occupied = new AABB(center.subtract(h), center.add(h));
      bounds = bounds == null ? occupied : bounds.minmax(occupied);
    }
    return new CloudJellyTarget(bounds, List.copyOf(boxes), basis, crown, Vec3.ZERO);
  }

  static Vec3 half(AABB box) {
    return new Vec3(box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2);
  }

  Vec3 world(Vec3 local) {
    return basis.apply(local).add(crown);
  }

  Vec3 local(Vec3 world) {
    return basis.inverse(world.subtract(crown));
  }

  @Override
  public AABB move(double x, double y, double z) {
    return new CloudJellyTarget(super.move(x, y, z), boxes, basis, crown.add(x, y, z), padding);
  }

  @Override
  public AABB inflate(double x, double y, double z) {
    if (x == 0 && y == 0 && z == 0) return this;
    return new CloudJellyTarget(super.inflate(x, y, z), boxes, basis, crown, padding.add(x, y, z));
  }

  static double project(Vec3 a, Vec3 h) {
    return Math.abs(a.x) * h.x + Math.abs(a.y) * h.y + Math.abs(a.z) * h.z;
  }

  static List<Vec3> separatingAxes(CloudJellyMotion.Basis basis) {
    Vec3[] world = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
    List<Vec3> axes = new ArrayList<>(15);
    for (Vec3 w : world) axes.add(w);
    for (Vec3 a : List.of(basis.x, basis.y, basis.z)) {
      axes.add(a);
      for (Vec3 w : world) {
        Vec3 normal = a.cross(w);
        if (normal.lengthSqr() > 1e-9) axes.add(normal);
      }
    }
    return axes;
  }

  /**
   * Native projectiles expand targets along world axes. Keep that rectangular sweep exact at any
   * bank.
   */
  private double paddedEntry(Vec3 start, Vec3 end) {
    if (!super.contains(start.x, start.y, start.z) && super.clip(start, end).isEmpty())
      return Double.POSITIVE_INFINITY;
    var axes =
        separatingAxes(basis).stream()
            .map(n -> new PickAxis(basis.inverse(n), project(n, padding)))
            .toList();
    Vec3 from = local(start), delta = local(end).subtract(from);
    double earliest = Double.POSITIVE_INFINITY;
    for (AABB box : boxes) {
      Vec3 h = half(box), offset = from.subtract(box.getCenter());
      double enter = 0, exit = 1;
      for (PickAxis axis : axes) {
        double half = project(axis.local, h) + axis.padding;
        double p = axis.local.dot(offset), d = axis.local.dot(delta);
        if (Math.abs(d) < 1e-12) {
          if (Math.abs(p) > half) {
            enter = 2;
            break;
          }
        } else {
          double lo = (-half - p) / d, hi = (half - p) / d;
          enter = Math.max(enter, Math.min(lo, hi));
          exit = Math.min(exit, Math.max(lo, hi));
          if (enter > exit) break;
        }
      }
      if (enter <= exit) earliest = Math.min(enter, earliest);
    }
    return earliest;
  }

  private record PickAxis(Vec3 local, double padding) {}

  @Override
  public boolean contains(double x, double y, double z) {
    if (!super.contains(x, y, z)) return false;
    Vec3 point = new Vec3(x, y, z);
    if (padding != Vec3.ZERO) return Double.isFinite(paddedEntry(point, point));
    point = local(point);
    for (AABB box : boxes) if (box.contains(point)) return true;
    return false;
  }

  @Override
  public Optional<Vec3> clip(Vec3 start, Vec3 end) {
    if (padding != Vec3.ZERO) {
      double t = paddedEntry(start, end);
      return Double.isFinite(t) ? Optional.of(start.lerp(end, t)) : Optional.empty();
    }
    Vec3 from = local(start), to = local(end), nearest = null;
    double distance = Double.POSITIVE_INFINITY;
    for (AABB box : boxes) {
      var hit = box.clip(from, to);
      if (hit.isEmpty()) continue;
      double next = from.distanceToSqr(hit.get());
      if (next < distance) {
        distance = next;
        nearest = hit.get();
      }
    }
    return nearest == null ? Optional.empty() : Optional.of(world(nearest));
  }
}
