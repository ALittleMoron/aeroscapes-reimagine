package dev.aeroscapes.aquatic;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * CPU rasterizer for native baked quads. Coordinates are blocks and UVs normalized within a sprite.
 */
public final class PreviewRaster {
  private PreviewRaster() {}

  public record Vertex(Vector3f position, float u, float v) {
    Vertex between(Vertex end, float fraction) {
      return new Vertex(
          new Vector3f(position).lerp(end.position, fraction),
          u + (end.u - u) * fraction,
          v + (end.v - v) * fraction);
    }
  }

  public record Quad(List<Vertex> vertices, BufferedImage texture, int color) {
    Quad transformed(Matrix4fc matrix) {
      return new Quad(
          vertices.stream()
              .map(v -> new Vertex(matrix.transformPosition(v.position, new Vector3f()), v.u, v.v))
              .toList(),
          texture,
          color);
    }
  }

  private record Screen(float x, float y, float inverseDepth, float uOverDepth, float vOverDepth) {}

  private record Triangle(
      Screen a, Screen b, Screen c, BufferedImage image, int color, float shade, float depth) {}

  static BufferedImage render(List<Quad> surfaces, int width, int height, double fov) {
    int[] rgb = new int[width * height];
    Arrays.fill(rgb, 0xdce3ea);
    float[] depth = new float[rgb.length];
    double focal = height * .5 / Math.tan(Math.toRadians(fov * .5));
    var transparent = new ArrayList<Triangle>();
    for (var face : surfaces) {
      var polygon = clip(face.vertices);
      if (polygon.size() < 3) continue;
      var normal =
          new Vector3f(face.vertices.get(1).position)
              .sub(face.vertices.getFirst().position)
              .cross(
                  new Vector3f(face.vertices.get(2).position)
                      .sub(face.vertices.getFirst().position));
      if (normal.lengthSquared() < 1e-13f) continue;
      normal.normalize();
      if (normal.dot(face.vertices.getFirst().position) > 0) continue;
      float shade = .76f + .24f * Math.max(0, normal.dot(new Vector3f(-.35f, .65f, .67f)));
      var first = project(polygon.getFirst(), width, height, focal);
      for (int i = 1; i + 1 < polygon.size(); i++) {
        var b = project(polygon.get(i), width, height, focal);
        var c = project(polygon.get(i + 1), width, height, focal);
        var t =
            new Triangle(
                first,
                b,
                c,
                face.texture,
                face.color,
                shade,
                (1 / first.inverseDepth + 1 / b.inverseDepth + 1 / c.inverseDepth) / 3);
        draw(t, width, height, rgb, depth, false);
        if ((face.color >>> 24) < 255 || face.texture.getColorModel().hasAlpha())
          transparent.add(t);
      }
    }
    transparent.sort(Comparator.comparingDouble(Triangle::depth).reversed());
    for (var t : transparent) draw(t, width, height, rgb, depth, true);
    var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    result.setRGB(0, 0, width, height, rgb, 0, width);
    return result;
  }

  private static List<Vertex> clip(List<Vertex> vertices) {
    float plane = -.05f;
    var inside = new ArrayList<Vertex>();
    var previous = vertices.getLast();
    for (var current : vertices) {
      boolean was = previous.position.z <= plane, now = current.position.z <= plane;
      if (now != was)
        inside.add(
            previous.between(
                current,
                (plane - previous.position.z) / (current.position.z - previous.position.z)));
      if (now) inside.add(current);
      previous = current;
    }
    return inside;
  }

  private static Screen project(Vertex v, int width, int height, double focal) {
    float iz = -1 / v.position.z;
    return new Screen(
        (float) (width * .5 + focal * v.position.x * iz),
        (float) (height * .5 - focal * v.position.y * iz),
        iz,
        v.u * iz,
        v.v * iz);
  }

  private static float edge(Screen a, Screen b, float x, float y) {
    return (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x);
  }

  private static void draw(
      Triangle t, int width, int height, int[] pixels, float[] depths, boolean translucent) {
    Screen a = t.a, b = t.b, c = t.c;
    float area = edge(a, b, c.x, c.y);
    if (Math.abs(area) < 1e-6f) return;
    boolean ownsA = ownsEdge(b, c, area),
        ownsB = ownsEdge(c, a, area),
        ownsC = ownsEdge(a, b, area);
    int x0 = Math.max(0, (int) Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
    int x1 = Math.min(width - 1, (int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
    int y0 = Math.max(0, (int) Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
    int y1 = Math.min(height - 1, (int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
    for (int y = y0; y <= y1; y++)
      for (int x = x0; x <= x1; x++) {
        float wa = edge(b, c, x + .5f, y + .5f) / area,
            wb = edge(c, a, x + .5f, y + .5f) / area,
            wc = edge(a, b, x + .5f, y + .5f) / area;
        if (wa < 0
            || wb < 0
            || wc < 0
            || (wa == 0 && !ownsA)
            || (wb == 0 && !ownsB)
            || (wc == 0 && !ownsC)) continue;
        float iz = wa * a.inverseDepth + wb * b.inverseDepth + wc * c.inverseDepth;
        int index = y * width + x;
        if (iz <= depths[index]) continue;
        float u = (wa * a.uOverDepth + wb * b.uOverDepth + wc * c.uOverDepth) / iz,
            v = (wa * a.vOverDepth + wb * b.vOverDepth + wc * c.vOverDepth) / iz;
        int texel =
            t.image.getRGB(
                Math.clamp((int) (u * t.image.getWidth()), 0, t.image.getWidth() - 1),
                Math.clamp((int) (v * t.image.getHeight()), 0, t.image.getHeight() - 1));
        int alpha = (texel >>> 24) * (t.color >>> 24) / 255;
        if (alpha == 0 || (alpha < 255) != translucent) continue;
        int old = pixels[index], color = 0;
        for (int shift : CHANNELS) {
          int channel =
              Math.round(((texel >> shift) & 255) * ((t.color >> shift) & 255) / 255f * t.shade);
          color |= ((channel * alpha + ((old >> shift) & 255) * (255 - alpha)) / 255) << shift;
        }
        pixels[index] = color;
        if (!translucent) depths[index] = iz;
      }
  }

  private static boolean ownsEdge(Screen a, Screen b, float area) {
    boolean rising = a.y < b.y || (a.y == b.y && a.x > b.x);
    return area > 0 ? rising : !rising;
  }

  private static final int[] CHANNELS = {16, 8, 0};
}
