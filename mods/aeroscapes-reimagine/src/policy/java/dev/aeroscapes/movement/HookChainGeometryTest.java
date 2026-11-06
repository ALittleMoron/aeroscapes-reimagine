package dev.aeroscapes.movement;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Captures the same emitted vertices and shader normals as the native crossed chain. */
final class HookChainGeometryTest {
  private HookChainGeometryTest() {}

  static void verify(Path texture) throws IOException {
    float length = 2.3f;
    int light = 0x00FA00BC;
    for (float angle : new float[] {0, .7f}) {
      PoseStack poses = new PoseStack();
      poses.translate(3, -2, 5);
      poses.mulPose(new Quaternionf().rotationXYZ(angle, -angle, .3f));
      Recording consumer = new Recording();
      float[][] vertices = {
        {-.1f, length, 0, .5f, 5 * length - 1},
        {-.1f, 0, 0, .5f, -1},
        {.1f, 0, 0, 0, -1},
        {.1f, length, 0, 0, 5 * length - 1},
        {0, length, .1f, 1, 5 * length - 1},
        {0, 0, .1f, 1, -1},
        {0, 0, -.1f, .5f, -1},
        {0, length, -.1f, .5f, 5 * length - 1}
      };
      for (float[] point : vertices)
        HookChainGeometry.vertex(
            consumer, poses.last(), point[0], point[1], point[2], point[3], point[4], light);
      for (int index = 0; index < vertices.length; index++) {
        Sample actual = consumer.samples.get(index);
        float[] original = vertices[index];
        Vector3f expected =
            poses
                .last()
                .pose()
                .transformPosition(
                    new Vector3f(original[0] * .25f, original[1], original[2] * .25f));
        require(
            actual.position.distance(expected) < .00001f,
            "Only the chain's transverse width changes");
        require(
            actual.red == 255 && actual.green == 255 && actual.blue == 255 && actual.alpha == 255,
            "Native white material tint is preserved");
        require(
            actual.light == light && actual.overlay == OverlayTexture.NO_OVERLAY,
            "Native world light and overlay are preserved");
        require(
            Math.abs(actual.u - original[3]) < .00001f
                && Math.abs(actual.v - (original[4] + 1) * 4 + 1) < .0001f,
            "Link length follows the smaller width without stretching");
      }
      for (int start : new int[] {0, 4}) {
        Sample a = consumer.samples.get(start),
            b = consumer.samples.get(start + 1),
            c = consumer.samples.get(start + 2);
        Vector3f normal =
            new Vector3f(b.position)
                .sub(a.position)
                .cross(new Vector3f(c.position).sub(a.position))
                .normalize();
        require(
            normal.dot(a.normal) > .9999f,
            "The shader normal matches the actual rotated chain plane");
        require(
            Math.abs(b.position.distance(c.position) - .05f) < .00001f,
            "The full ribbon is .05 blocks wide");
      }
    }
    BufferedImage changed = ImageIO.read(texture.toFile());
    try (var stream =
        HookChainGeometryTest.class.getResourceAsStream(
            "/assets/yo_hooks/textures/entity/hook_rope.png")) {
      if (stream == null) throw new IOException("Missing native chain texture");
      BufferedImage original = ImageIO.read(stream);
      require(
          changed.getWidth() == original.getWidth() && changed.getHeight() == original.getHeight(),
          "Keep native chain UV layout");
      for (int y = 0; y < original.getHeight(); y++)
        for (int x = 0; x < original.getWidth(); x++) {
          int before = original.getRGB(x, y), after = changed.getRGB(x, y);
          require(before >>> 24 == after >>> 24, "Keep every transparent opening in the chain");
          if (before >>> 24 != 0)
            require(
                lightness(after) > lightness(before),
                "The metal palette is brighter without emissive lighting");
        }
    }
  }

  private static int lightness(int color) {
    return (color >> 16 & 255) + (color >> 8 & 255) + (color & 255);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static final class Sample {
    private final Vector3f position;
    private final Vector3f normal = new Vector3f();
    private float u, v;
    private int red, green, blue, alpha, light, overlay;

    private Sample(float x, float y, float z) {
      position = new Vector3f(x, y, z);
    }
  }

  private static final class Recording implements VertexConsumer {
    private final List<Sample> samples = new ArrayList<>();

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      samples.add(new Sample(x, y, z));
      return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
      var sample = samples.getLast();
      sample.red = red;
      sample.green = green;
      sample.blue = blue;
      sample.alpha = alpha;
      return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      var sample = samples.getLast();
      sample.u = u;
      sample.v = v;
      return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      samples.getLast().overlay = u | (v << 16);
      return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      samples.getLast().light = u | (v << 16);
      return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      samples.getLast().normal.set(x, y, z);
      return this;
    }
  }
}
