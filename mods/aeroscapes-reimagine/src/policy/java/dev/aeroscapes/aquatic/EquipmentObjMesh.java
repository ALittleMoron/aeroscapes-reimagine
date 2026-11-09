package dev.aeroscapes.aquatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.joml.Vector3d;

/** Writes ordinary OBJ surfaces with transported rope sections and atlas-safe UV seams. */
final class EquipmentObjMesh {
  record P(double x, double y, double z) {
    P add(P other) {
      return new P(x + other.x, y + other.y, z + other.z);
    }

    P scale(double value) {
      return new P(x * value, y * value, z * value);
    }

    Vector3d vector() {
      return new Vector3d(x, y, z);
    }
  }

  private final StringBuilder data;

  EquipmentObjMesh() {
    this("equipment.mtl");
  }

  EquipmentObjMesh(String library) {
    data = new StringBuilder("mtllib " + library + "\n");
  }

  private int count;
  private final Map<String, double[]> regions = new HashMap<>();
  private final Set<String> projectedFields = new HashSet<>();
  private final Map<String, String> materials = new HashMap<>();

  void material(String original, String replacement) {
    materials.put(original, replacement);
  }

  void region(String material, double u0, double v0, double u1, double v1) {
    regions.put(material, new double[] {u0, v0, u1, v1});
  }

  void projectedField(String material) {
    projectedFields.add(material);
  }

  String obj() {
    return data.toString();
  }

  void group(String name) {
    data.append("g ").append(name).append('\n');
  }

  static P[] octagonalRing(double x, double y, double z, double width, double depth) {
    P[] points = new P[8];
    for (int side = 0; side < points.length; side++) {
      double angle = side * Math.PI / 4;
      points[side] = new P(x + width * Math.cos(angle), y, z + depth * Math.sin(angle));
    }
    return points;
  }

  void shell(String material, P[][] rings) {
    int sides = rings[0].length;
    for (int row = 1; row < rings.length; row++) {
      P[] lower = rings[row - 1], upper = rings[row];
      double bottomV = 1 - (row - 1.0) / (rings.length - 1);
      double topV = 1 - row / (rings.length - 1.0);
      for (int side = 0; side < sides; side++) {
        int next = (side + 1) % sides;
        P outward = lower[side].add(lower[next]).scale(.5).add(ringCenter(lower).scale(-1));
        double u = (side % 4) / 4.0, nextU = u + .25;
        quad(
            material,
            new P[] {lower[side], lower[next], upper[next], upper[next]},
            outward,
            new double[][] {{u, bottomV}, {nextU, bottomV}, {nextU, topV}, {nextU, topV}});
        quad(
            material,
            new P[] {lower[side], upper[next], upper[side], upper[side]},
            outward,
            new double[][] {{u, bottomV}, {nextU, topV}, {u, topV}, {u, topV}});
      }
    }
    for (int end : new int[] {0, rings.length - 1}) {
      P[] rim = rings[end];
      P center = ringCenter(rim);
      int adjacent = end == 0 ? 1 : end - 1;
      P outward = center.add(ringCenter(rings[adjacent]).scale(-1));
      for (int side = 0; side < sides; side++) {
        int next = (side + 1) % sides;
        double angle = side * 2 * Math.PI / sides, nextAngle = next * 2 * Math.PI / sides;
        double[] firstUv = {.5 + .5 * Math.cos(angle), .5 + .5 * Math.sin(angle)};
        double[] nextUv = {.5 + .5 * Math.cos(nextAngle), .5 + .5 * Math.sin(nextAngle)};
        quad(
            material,
            new P[] {center, rim[side], rim[next], rim[next]},
            outward,
            new double[][] {{.5, .5}, firstUv, nextUv, nextUv});
      }
    }
  }

  private static P ringCenter(P[] ring) {
    return ring[0].add(ring[ring.length / 2]).scale(.5);
  }

  void box(String material, double x0, double y0, double z0, double x1, double y1, double z1) {
    P a = new P(x0, y0, z0),
        b = new P(x1, y0, z0),
        c = new P(x1, y1, z0),
        d = new P(x0, y1, z0),
        e = new P(x0, y0, z1),
        f = new P(x1, y0, z1),
        g = new P(x1, y1, z1),
        h = new P(x0, y1, z1);
    double[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
    quad(material, new P[] {a, b, c, d}, new P(0, 0, -1), uv);
    quad(material, new P[] {e, f, g, h}, new P(0, 0, 1), uv);
    quad(material, new P[] {a, e, h, d}, new P(-1, 0, 0), uv);
    quad(material, new P[] {b, f, g, c}, new P(1, 0, 0), uv);
    quad(material, new P[] {d, c, g, h}, new P(0, 1, 0), uv);
    quad(material, new P[] {a, b, f, e}, new P(0, -1, 0), uv);
  }

  void beam(String material, P start, P end, double radius, int sides) {
    Vector3d axis = end.vector().sub(start.vector()).normalize();
    Vector3d across =
        new Vector3d(axis)
            .cross(Math.abs(axis.z) < .9 ? new Vector3d(0, 0, 1) : new Vector3d(0, 1, 0))
            .normalize();
    Vector3d up = new Vector3d(across).cross(axis).normalize();
    P[][] rings = new P[2][sides];
    for (int ring = 0; ring < 2; ring++)
      for (int side = 0; side < sides; side++) {
        double angle = side * 2 * Math.PI / sides;
        Vector3d offset =
            new Vector3d(across)
                .mul(radius * Math.cos(angle))
                .add(new Vector3d(up).mul(radius * Math.sin(angle)));
        rings[ring][side] = (ring == 0 ? start : end).add(new P(offset.x, offset.y, offset.z));
      }
    for (int side = 0; side < sides; side++) {
      int next = (side + 1) % sides;
      P outward = rings[0][side].add(rings[0][next]).scale(.5).add(start.scale(-1));
      quad(
          material,
          new P[] {rings[0][side], rings[1][side], rings[1][next], rings[0][next]},
          outward,
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
      double a = side * 2 * Math.PI / sides, b = (side + 1) * 2 * Math.PI / sides;
      double[] ua = {.5 + .5 * Math.cos(a), .5 + .5 * Math.sin(a)},
          ub = {.5 + .5 * Math.cos(b), .5 + .5 * Math.sin(b)};
      for (int ring = 0; ring < 2; ring++)
        quad(
            material,
            new P[] {
              ring == 0 ? start : end, rings[ring][side], rings[ring][next], rings[ring][next]
            },
            new P(axis.x, axis.y, axis.z).scale(ring == 0 ? -1 : 1),
            new double[][] {{.5, .5}, ua, ub, ub});
    }
  }

  void plate(String front, String edge, List<P> outline, double depth) {
    double signed = 0;
    for (int index = 0; index < outline.size(); index++) {
      P a = outline.get(index), b = outline.get((index + 1) % outline.size());
      signed += a.x * b.y - b.x * a.y;
    }
    List<P> points = signed > 0 ? outline : outline.reversed();
    List<int[]> triangles = triangulate(points);
    double minX = points.stream().mapToDouble(P::x).min().orElseThrow(),
        maxX = points.stream().mapToDouble(P::x).max().orElseThrow();
    double minY = points.stream().mapToDouble(P::y).min().orElseThrow(),
        maxY = points.stream().mapToDouble(P::y).max().orElseThrow();
    for (int side : new int[] {-1, 1})
      for (int[] triangle : triangles) {
        P a = points.get(triangle[0]).add(new P(0, 0, side * depth / 2)),
            b = points.get(triangle[1]).add(new P(0, 0, side * depth / 2)),
            c = points.get(triangle[2]).add(new P(0, 0, side * depth / 2));
        double[] ua = {(a.x - minX) / (maxX - minX), (maxY - a.y) / (maxY - minY)},
            ub = {(b.x - minX) / (maxX - minX), (maxY - b.y) / (maxY - minY)},
            uc = {(c.x - minX) / (maxX - minX), (maxY - c.y) / (maxY - minY)};
        quad(front, new P[] {a, b, c, c}, new P(0, 0, side), new double[][] {ua, ub, uc, uc});
      }
    for (int index = 0; index < points.size(); index++) {
      P a = points.get(index), b = points.get((index + 1) % points.size());
      quad(
          edge,
          new P[] {
            a.add(new P(0, 0, -depth / 2)),
            b.add(new P(0, 0, -depth / 2)),
            b.add(new P(0, 0, depth / 2)),
            a.add(new P(0, 0, depth / 2))
          },
          new P(b.y - a.y, a.x - b.x, 0),
          new double[][] {{0, 0}, {1, 0}, {1, 1}, {0, 1}});
    }
  }

  private static List<int[]> triangulate(List<P> points) {
    List<Integer> remaining = new ArrayList<>();
    for (int index = 0; index < points.size(); index++) remaining.add(index);
    List<int[]> triangles = new ArrayList<>();
    while (remaining.size() > 3) {
      boolean clipped = false;
      for (int index = 0; index < remaining.size(); index++) {
        int a = remaining.get((index + remaining.size() - 1) % remaining.size()),
            b = remaining.get(index),
            c = remaining.get((index + 1) % remaining.size());
        if (turn(points.get(a), points.get(b), points.get(c)) <= .000001) continue;
        boolean contains = false;
        for (int other : remaining) {
          if (other == a || other == b || other == c) continue;
          P point = points.get(other);
          contains |=
              turn(points.get(a), points.get(b), point) >= -.000001
                  && turn(points.get(b), points.get(c), point) >= -.000001
                  && turn(points.get(c), points.get(a), point) >= -.000001;
        }
        if (contains) continue;
        triangles.add(new int[] {a, b, c});
        remaining.remove(index);
        clipped = true;
        break;
      }
      if (!clipped) throw new IllegalArgumentException("Self-intersecting plate contour");
    }
    triangles.add(new int[] {remaining.get(0), remaining.get(1), remaining.get(2)});
    return triangles;
  }

  private static double turn(P a, P b, P c) {
    return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
  }

  void quad(String material, P[] points, P outward, double[][] uv) {
    material = materials.getOrDefault(material, material);
    P[] vertices = points.clone();
    Vector3d normal =
        vertices[1]
            .vector()
            .sub(vertices[0].vector())
            .cross(vertices[2].vector().sub(vertices[0].vector()))
            .normalize();
    double[][] mapping =
        projectedFields.contains(material)
            ? projectField(points, normal)
            : uv == null
                ? new double[][] {
                  {points[0].x / 16, 1 - points[0].y / 16},
                  {points[1].x / 16, 1 - points[1].y / 16},
                  {points[2].x / 16, 1 - points[2].y / 16},
                  {points[3].x / 16, 1 - points[3].y / 16}
                }
                : uv.clone();
    double[] region = regions.get(material);
    if (region != null)
      for (int index = 0; index < 4; index++)
        mapping[index] =
            new double[] {
              region[0] + mapping[index][0] * (region[2] - region[0]),
              region[1] + mapping[index][1] * (region[3] - region[1])
            };
    if (normal.dot(outward.vector()) < 0) {
      vertices = new P[] {points[2], points[1], points[0], points[3]};
      mapping = new double[][] {mapping[2], mapping[1], mapping[0], mapping[3]};
      normal.negate();
    }
    data.append("usemtl ").append(material).append('\n');
    for (int vertex = 0; vertex < 4; vertex++) {
      P point = vertices[vertex];
      data.append(
          String.format(
              Locale.ROOT,
              "v %.7f %.7f %.7f\nvt %.7f %.7f\nvn %.7f %.7f %.7f\n",
              point.x / 16,
              point.y / 16,
              point.z / 16,
              mapping[vertex][0],
              mapping[vertex][1],
              normal.x,
              normal.y,
              normal.z));
    }
    data.append("f");
    for (int vertex = 1; vertex <= 4; vertex++) {
      int index = count + vertex;
      data.append(' ').append(index).append('/').append(index).append('/').append(index);
    }
    data.append('\n');
    count += 4;
  }

  private static double[][] projectField(P[] points, Vector3d normal) {
    // All fields share model-space texel density; adjacent faces on the same plane share phase.
    int axis = Math.abs(normal.x) > Math.abs(normal.z) ? 0 : 2;
    if (Math.abs(normal.y) > Math.abs(normal.get(axis))) axis = 1;
    double[][] uv = new double[4][2];
    for (int index = 0; index < 4; index++) {
      P point = points[index];
      double u = axis == 0 ? point.z : point.x;
      double v = axis == 1 ? point.z : -point.y;
      double x = u + 16, y = v + 48;
      if (x <= 0 || x >= 64 || y <= 0 || y >= 64)
        throw new IllegalArgumentException("Surface exceeds equipment material field: " + point);
      uv[index][0] = x / 64;
      uv[index][1] = y / 64;
    }
    return uv;
  }

  private record Sweep(P point, Vector3d tangent, double length) {}

  private static Vector3d tangent(List<P> source, int index) {
    boolean closed = source.getFirst().vector().distance(source.getLast().vector()) < .000001;
    int actual = closed && index == source.size() - 1 ? 0 : index;
    int before = actual == 0 ? (closed ? source.size() - 2 : 0) : actual - 1;
    int after = actual == source.size() - 1 ? actual : actual + 1;
    Vector3d incoming = source.get(actual).vector().sub(source.get(before).vector());
    Vector3d outgoing = source.get(after).vector().sub(source.get(actual).vector());
    if (incoming.lengthSquared() < .000001) return outgoing.normalize();
    if (outgoing.lengthSquared() < .000001) return incoming.normalize();
    return incoming.normalize().add(outgoing.normalize()).normalize();
  }

  private static List<Sweep> centerline(List<P> source, double repeat, double radius) {
    List<Sweep> dense = new ArrayList<>();
    dense.add(new Sweep(source.getFirst(), tangent(source, 0), 0));
    double length = 0;
    P previous = source.getFirst();
    for (int index = 1; index < source.size(); index++) {
      P start = source.get(index - 1), end = source.get(index);
      double distance = start.vector().distance(end.vector());
      Vector3d first = tangent(source, index - 1).mul(distance),
          last = tangent(source, index).mul(distance);
      int steps = (int) Math.ceil(distance / Math.min(.45, radius * 1.2));
      for (int step = 1; step <= steps; step++) {
        double t = step / (double) steps, t2 = t * t, t3 = t2 * t;
        Vector3d position =
            start
                .vector()
                .mul(2 * t3 - 3 * t2 + 1)
                .add(new Vector3d(first).mul(t3 - 2 * t2 + t))
                .add(end.vector().mul(-2 * t3 + 3 * t2))
                .add(new Vector3d(last).mul(t3 - t2));
        Vector3d direction =
            start
                .vector()
                .mul(6 * t2 - 6 * t)
                .add(new Vector3d(first).mul(3 * t2 - 4 * t + 1))
                .add(end.vector().mul(-6 * t2 + 6 * t))
                .add(new Vector3d(last).mul(3 * t2 - 2 * t))
                .normalize();
        P point = new P(position.x, position.y, position.z);
        length += point.vector().distance(previous.vector());
        dense.add(new Sweep(point, direction, length));
        previous = point;
      }
    }
    List<Sweep> result = new ArrayList<>();
    result.add(dense.getFirst());
    for (int index = 1; index < dense.size(); index++) {
      Sweep start = dense.get(index - 1), end = dense.get(index);
      double seam = (Math.floor((start.length + .000001) / repeat) + 1) * repeat;
      if (seam < end.length - .000001) {
        double along = (seam - start.length) / (end.length - start.length);
        result.add(
            new Sweep(
                start.point.scale(1 - along).add(end.point.scale(along)),
                new Vector3d(start.tangent).lerp(end.tangent, along).normalize(),
                seam));
      }
      result.add(end);
    }
    return result;
  }

  void tube(String material, List<P> source, double radius, int sides, double repeat) {
    List<Sweep> path = centerline(source, repeat, radius);
    boolean closed = source.getFirst().vector().distance(source.getLast().vector()) < .000001;
    List<Vector3d> frames = new ArrayList<>();
    Vector3d firstTangent = path.getFirst().tangent;
    Vector3d reference =
        Math.abs(firstTangent.z) < .9 ? new Vector3d(0, 0, 1) : new Vector3d(0, 1, 0);
    Vector3d across = new Vector3d(firstTangent).cross(reference).normalize();
    Vector3d previous = firstTangent;
    for (Sweep point : path) {
      new org.joml.Quaterniond().rotationTo(previous, point.tangent).transform(across);
      across.sub(new Vector3d(point.tangent).mul(across.dot(point.tangent))).normalize();
      frames.add(new Vector3d(across));
      previous = point.tangent;
    }
    if (closed) {
      Vector3d start = frames.getFirst(), end = frames.getLast();
      double correction =
          Math.atan2(firstTangent.dot(new Vector3d(end).cross(start)), end.dot(start));
      for (int index = 0; index < path.size(); index++) {
        Vector3d axis = path.get(index).tangent;
        new org.joml.Quaterniond()
            .rotationAxis(
                correction * path.get(index).length / path.getLast().length, axis.x, axis.y, axis.z)
            .transform(frames.get(index));
      }
    }
    List<P[]> rings = new ArrayList<>();
    for (int index = 0; index < path.size(); index++) {
      Sweep point = path.get(index);
      Vector3d axis = frames.get(index), up = new Vector3d(axis).cross(point.tangent).normalize();
      P[] ring = new P[sides];
      for (int side = 0; side < sides; side++) {
        double angle = side * 2 * Math.PI / sides;
        Vector3d offset =
            new Vector3d(axis)
                .mul(Math.cos(angle) * radius)
                .add(new Vector3d(up).mul(Math.sin(angle) * radius));
        ring[side] = point.point.add(new P(offset.x, offset.y, offset.z));
      }
      rings.add(ring);
    }
    if (closed) rings.set(rings.size() - 1, rings.getFirst());
    for (int index = 1; index < path.size(); index++)
      for (int side = 0; side < sides; side++) {
        int next = (side + 1) % sides;
        P a = rings.get(index - 1)[side],
            b = rings.get(index)[side],
            c = rings.get(index)[next],
            d = rings.get(index - 1)[next];
        P outward =
            a.add(b)
                .add(c)
                .add(d)
                .scale(.25)
                .add(path.get(index - 1).point.add(path.get(index).point).scale(-.5));
        double before = path.get(index - 1).length / repeat,
            after = path.get(index).length / repeat;
        double start = Math.abs(before - Math.rint(before)) < .000001 ? 0 : before % 1;
        double end = Math.abs(after - Math.rint(after)) < .000001 ? 1 : after % 1;
        double[] ua = {side / (double) sides, start},
            ub = {side / (double) sides, end},
            uc = {(side + 1.0) / sides, end},
            ud = {(side + 1.0) / sides, start};
        // Curved strips are triangulated explicitly so each lighting normal belongs to a plane.
        quad(material, new P[] {a, b, c, c}, outward, new double[][] {ua, ub, uc, uc});
        quad(material, new P[] {a, c, d, d}, outward, new double[][] {ua, uc, ud, ud});
      }
    if (!closed)
      for (int end : new int[] {0, path.size() - 1})
        for (int side = 0; side < sides; side++) {
          P center = path.get(end).point,
              a = rings.get(end)[side],
              b = rings.get(end)[(side + 1) % sides];
          P outward =
              new P(path.get(end).tangent.x, path.get(end).tangent.y, path.get(end).tangent.z)
                  .scale(end == 0 ? -1 : 1);
          double first = side * 2 * Math.PI / sides, second = (side + 1) * 2 * Math.PI / sides;
          double[] ua = {.5 + .5 * Math.cos(first), .5 + .5 * Math.sin(first)},
              ub = {.5 + .5 * Math.cos(second), .5 + .5 * Math.sin(second)};
          quad(material, new P[] {center, a, b, b}, outward, new double[][] {{.5, .5}, ua, ub, ub});
        }
  }
}
