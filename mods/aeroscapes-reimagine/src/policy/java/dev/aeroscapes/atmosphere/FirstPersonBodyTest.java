package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceTraits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class FirstPersonBodyTest {
  private static final float RADIANS = (float) (Math.PI / 180.0);
  private static final float NEAR_PLANE = 0.05F;
  private static final float HALF_VIEW_HEIGHT = (float) Math.tan(35 * RADIANS);

  private FirstPersonBodyTest() {}

  static void verify() {
    var skin = playerModel(false);
    var armor =
        LayerDefinition.create(
                HumanoidArmorModel.createBodyLayer(LayerDefinitions.OUTER_ARMOR_DEFORMATION),
                64,
                32)
            .bakeRoot();
    for (var race : Race.values()) {
      float width = RaceTraits.width(race);
      float height = RaceTraits.height(race);
      for (boolean gliding : List.of(false, true)) {
        var lookingAhead = cameraSpace(0, 0, 0, width, height, gliding);
        check(
            positions(skin.body, lookingAhead).stream().noneMatch(FirstPersonBodyTest::visible),
            "Skin fills the view ahead for " + race);
        var lookingDown = cameraSpace(90, 0, 0, width, height, gliding);
        var turning = cameraSpace(90, 127, 14, width, height, gliding);
        var feet = positions(armor.getChild("left_leg"), lookingDown);
        check(
            feet.stream().anyMatch(FirstPersonBodyTest::visible),
            "Boot not visible looking down for " + race);
        var movedFeet = positions(armor.getChild("left_leg"), turning);
        for (int point = 0; point < feet.size(); point++) {
          check(
              feet.get(point).distance(movedFeet.get(point)) < 1.0e-5F,
              "Camera motion shifted the body for " + race);
        }
        check(
            positions(armor.getChild("body"), lookingDown).stream()
                .allMatch(position -> position.z >= -NEAR_PLANE || position.y < 0.1F * position.z),
            "Chest intersects the center view looking down for " + race);
      }
    }
    verifyBodyGeometry(false);
    verifyBodyGeometry(true);
  }

  private static PlayerModel<?> playerModel(boolean slim) {
    return new PlayerModel<>(
        LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64)
            .bakeRoot(),
        slim);
  }

  private static void verifyBodyGeometry(boolean slim) {
    var skin = playerModel(slim);
    var baseline = positions(skin);
    check(baseline.size() == 6 * 24, "Only the torso and legs and their overlays render");

    for (var excluded :
        List.of(
            skin.head, skin.hat, skin.leftArm, skin.rightArm, skin.leftSleeve, skin.rightSleeve)) {
      excluded.setPos(32, 64, 128);
    }
    check(positions(skin).equals(baseline), "Head and arm transforms have no effect");

    skin.leftLeg.setRotation(0.53F, 0.1F, 0.25F);
    skin.leftLeg.setPos(2.1F, 12.6F, -0.6F);
    skin.leftLeg.yScale = 0.88F;
    skin.leftPants.copyFrom(skin.leftLeg);
    var animated = positions(skin);
    check(!animated.equals(baseline), "The renderer preserves transforms from the animated model");

    checkTorsoAnchor(skin);
    skin.jacket.visible = false;
    skin.rightPants.visible = false;
    check(positions(skin).size() == 4 * 24, "Disabled skin overlays are omitted");
  }

  private static void checkTorsoAnchor(PlayerModel<?> skin) {
    Matrix4f view = cameraSpace(90, 0, 0, 1, 1, false);
    skin.body.xRot = 0.6F;
    skin.jacket.copyFrom(skin.body);
    var baseline = positions(skin, view);

    for (var part :
        List.of(
            skin.body, skin.leftLeg, skin.rightLeg, skin.jacket, skin.leftPants, skin.rightPants)) {
      part.setPos(part.x + 1.0F, part.y + 2.0F, part.z - 5.0F);
    }
    var moved = positions(skin, view);
    for (int point = 0; point < baseline.size(); point++) {
      check(
          baseline.get(point).distance(moved.get(point)) < 1.0e-5F,
          "Torso translation moved the camera anchor");
    }

    var poses = new PoseStack();
    poses.mulPose(view);
    FirstPersonBody.anchorTorso(poses, skin.body);
    check(
        positions(skin.body, poses.last().pose()).stream()
            .allMatch(position -> position.z >= -NEAR_PLANE || position.y < 0.1F * position.z),
        "Animated crouch torso intersects the center view looking down");
  }

  private static List<Vector3f> positions(PlayerModel<?> skin) {
    return positions(skin, new Matrix4f());
  }

  private static List<Vector3f> positions(PlayerModel<?> skin, Matrix4f view) {
    var poses = new PoseStack();
    poses.mulPose(view);
    FirstPersonBody.anchorTorso(poses, skin.body);
    var capture = new Capture();
    FirstPersonBody.renderParts(skin, poses, capture, 0, 0, -1);
    return capture.vertices;
  }

  private static Matrix4f cameraSpace(
      float pitch, float yaw, float roll, float width, float height, boolean gliding) {
    var camera =
        new Quaternionf().rotationYXZ((180 - yaw) * RADIANS, -pitch * RADIANS, -roll * RADIANS);
    var poses = new PoseStack();
    FirstPersonBody.place(poses, camera, pitch, width, height, gliding);
    return new Matrix4f().rotation(camera.conjugate(new Quaternionf())).mul(poses.last().pose());
  }

  private static List<Vector3f> positions(ModelPart part, Matrix4f view) {
    var poses = new PoseStack();
    poses.mulPose(view);
    var capture = new Capture();
    part.render(poses, capture, 0, 0);
    return capture.vertices;
  }

  private static boolean visible(Vector3f position) {
    return position.z < -NEAR_PLANE
        && Math.abs(position.y / position.z) < HALF_VIEW_HEIGHT
        && Math.abs(position.x / position.z) < HALF_VIEW_HEIGHT;
  }

  private static void check(boolean pass, String message) {
    if (!pass) throw new AssertionError(message);
  }

  public static void main(String[] args) {
    verify();
    System.out.println("First-person body projections and animated geometry are valid");
  }

  private static final class Capture implements VertexConsumer {
    private final List<Vector3f> vertices = new ArrayList<>();

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
      vertices.add(new Vector3f(x, y, z));
      return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
      return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
      return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
      return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
      return this;
    }
  }
}
