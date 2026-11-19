package dev.aeroscapes.races.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aeroscapes.races.Race;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

final class RaceEars {
  // Each outline runs from the lower cheek around the tip to the temple.
  private static final List<List<Face>> GOBLIN =
      List.of(
          ears(
              0.24f, 1.25f, 0, 0, -1.45f, 1.6f, -1.05f, 3.3f, -0.1f, 4.3f, 1.45f, 2.15f, 1.5f, 0,
              1.25f),
          ears(
              0.27f, 1.1f, 0, 0, -1.4f, 2.2f, -0.85f, 3.7f, 0.05f, 2.7f, 0.45f, 4.0f, 1.8f, 1.8f,
              1.4f, 0, 1.3f),
          ears(
              0.43f, 1.3f, -0.4f, 0, -1.2f, 1.65f, -1.65f, 2.9f, -2.45f, 3.35f, -1.9f, 2.95f, 0.3f,
              1.45f, 1.35f, 0, 1.15f));
  private static final List<List<Face>> ELF =
      List.of(
          ears(0.83f, 0.78f, 0.2f, 0, -1.15f, 1.5f, -0.1f, 3.2f, 2.8f, 1.3f, 1.75f, 0, 1.15f),
          ears(0.43f, 0.66f, 0.7f, 0, -1.2f, 1.2f, 0.15f, 1.9f, 4.25f, 0.55f, 2.0f, 0, 1.15f),
          ears(
              0.62f, 0.95f, 0.3f, 0, -1.25f, 1.75f, -0.25f, 2.65f, 1.2f, 2.9f, 2.8f, 1.25f, 2.15f,
              0, 1.05f));

  private RaceEars() {}

  private record Face(
      Vector3f a, Vector3f b, Vector3f c, Vector3f d, Vector3f normal, int color, float u) {
    void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f p, int light, int overlay) {
      buffer
          .addVertex(pose, p.x, p.y, p.z)
          .setColor(color)
          .setUv(u, 13.5f / 64)
          .setOverlay(overlay)
          .setLight(light)
          .setNormal(pose, normal.x, normal.y, normal.z);
    }
  }

  static void render(
      PoseStack stack, VertexConsumer buffer, int light, int overlay, Race race, int style) {
    for (var face : (race == Race.ELF ? ELF : GOBLIN).get(style)) {
      var pose = stack.last();
      face.vertex(pose, buffer, face.a, light, overlay);
      face.vertex(pose, buffer, face.b, light, overlay);
      face.vertex(pose, buffer, face.c, light, overlay);
      face.vertex(pose, buffer, face.d, light, overlay);
    }
  }

  private static List<Face> ears(float sweep, float centerX, float centerY, float... outline) {
    var faces = new ArrayList<Face>();
    for (int side : new int[] {-1, 1}) {
      var center = point(side, sweep, centerX, centerY, -0.1f);
      var rear = point(side, sweep, centerX, centerY, 0.75f);
      var rim = new Vector3f[outline.length / 2];
      var inset = new Vector3f[rim.length];
      var back = new Vector3f[rim.length];
      for (int i = 0; i < rim.length; i++) {
        float x = outline[2 * i], y = outline[2 * i + 1];
        rim[i] = point(side, sweep, x, y, -0.45f);
        inset[i] =
            point(
                side,
                sweep,
                centerX + (x - centerX) * 0.65f,
                centerY + (y - centerY) * 0.65f,
                -0.28f);
        back[i] = point(side, sweep, x, y, 0.28f);
      }
      for (int i = 0; i < rim.length; i++) {
        int j = (i + 1) % rim.length;
        face(faces, side, rim[i], rim[j], inset[j], inset[i], 0xffffffff);
        face(faces, side, inset[i], inset[j], center, center, 0xffd7a699);
        face(faces, side, rim[j], rim[i], back[i], back[j], 0xffedddd1);
        face(faces, side, back[j], back[i], rear, rear, 0xffefdcd0);
      }
    }
    return List.copyOf(faces);
  }

  private static Vector3f point(int side, float sweep, float x, float rise, float depth) {
    return new Vector3f(
        side * (3.8f + x) / 16, (-3.65f - rise) / 16, (0.35f + sweep * x + depth) / 16);
  }

  private static void face(
      List<Face> faces, int side, Vector3f a, Vector3f b, Vector3f c, Vector3f d, int color) {
    var normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize().mul(side);
    // Sample the cheek on this side, including on custom or high-resolution skins.
    float u = (side > 0 ? 14.5f : 9.5f) / 64;
    faces.add(
        side > 0 ? new Face(a, b, c, d, normal, color, u) : new Face(d, c, b, a, normal, color, u));
  }
}
