package dev.aeroscapes.sky.fauna.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aeroscapes.sky.fauna.CloudJellyAnatomy.Form;
import dev.aeroscapes.sky.fauna.CloudJellyMotion;
import java.io.IOException;
import java.util.HashSet;
import javax.imageio.ImageIO;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class CloudJellyGlowTest {
  private CloudJellyGlowTest() {}

  public static void verify() throws IOException {
    var colors = new HashSet<Integer>();
    var model = new CloudJellyModel(CloudJellyModel.layer().bakeRoot());
    try (var resource =
        CloudJellyGlowTest.class.getResourceAsStream(
            "/assets/aeroscapes_reimagine/" + CloudJellyGlow.TEXTURE.getPath())) {
      if (resource == null) throw new IOException("Missing cloud jelly glow atlas");
      var atlas = ImageIO.read(resource);
      for (Form form : Form.values()) {
        for (boolean pulse : new boolean[] {false, true}) {
          var uv = CloudJellyGlow.sprite(form, pulse);
          int x = Math.round((uv.u0() + uv.u1()) * atlas.getWidth() / 2);
          int y = Math.round((uv.v0() + uv.v1()) * atlas.getHeight() / 2);
          int width = Math.round((uv.u1() - uv.u0()) * atlas.getWidth());
          int center = atlas.getRGB(x, y), rim = atlas.getRGB(x + width / 4, y);
          if (center >>> 24 != 255
              || (center & 255) <= (rim & 255)
              || (rim & 255) == 0
              || atlas.getRGB(x + width / 2 - 1, y) != 0)
            throw new IllegalStateException("Glow must blend from bell to clear sky: " + form);
          colors.add(center);
        }
        for (int tick = 0; tick < 240; tick += 7) {
          var pose = CloudJellyMotion.sample(form, 4, 17, tick, 32, false);
          var crown = new Vec3(0, pose.shape().height() / 16D, 0);
          var center = CloudJellyGlow.center(pose);
          var local = pose.basis.inverse(center.subtract(crown));
          if (Math.abs(local.x) > 1E-6
              || Math.abs(local.z) > 1E-6
              || local.y >= 0
              || local.y <= -pose.bellDepth() / 16D)
            throw new IllegalStateException("Glow must follow the posed bell: " + form);
          model.pose(form, pose);
          var body = new PoseStack();
          body.mulPose(Axis.YP.rotationDegrees(180));
          body.scale(-1, -1, 1);
          body.translate(0, -1.501F, 0);
          model.root().translateAndRotate(body);
          var posed =
              body.last().pose().transformPosition(0, (float) (-4 * local.y), 0, new Vector3f());
          if (posed.distance((float) center.x, (float) center.y, (float) center.z) > .002)
            throw new IllegalStateException("Glow drifts from the native model: " + form);
        }
      }
    }
    if (colors.size() != 5 || CloudJellyGlow.radius(.23) <= CloudJellyGlow.radius(.73))
      throw new IllegalStateException("Four glow colors, a sting flash and a swim pulse");
  }
}
