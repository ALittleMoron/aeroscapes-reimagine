package dev.aeroscapes.sky.fauna.client;

import dev.aeroscapes.sky.fauna.SkyRay;
import dev.aeroscapes.sky.fauna.SkyRayWings;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

final class SkyRayModel extends HierarchicalModel<SkyRay> {
  private final ModelPart root;
  private final ModelPart body;
  private final ModelPart left;
  private final ModelPart right;
  private final ModelPart leftTip;
  private final ModelPart rightTip;
  private final ModelPart leftEdge;
  private final ModelPart rightEdge;
  private final ModelPart tail;
  private final ModelPart whip;

  SkyRayModel(ModelPart root) {
    this.root = root;
    body = root.getChild("body");
    left = body.getChild("left");
    right = body.getChild("right");
    leftTip = left.getChild("tip");
    rightTip = right.getChild("tip");
    leftEdge = leftTip.getChild("edge");
    rightEdge = rightTip.getChild("edge");
    tail = body.getChild("tail");
    whip = tail.getChild("whip");
  }

  @Override
  public ModelPart root() {
    return root;
  }

  static LayerDefinition layer() {
    MeshDefinition mesh = new MeshDefinition();
    PartDefinition body =
        mesh.getRoot()
            .addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                    .texOffs(0, 0)
                    .addBox(-12, -3, -20, 24, 9, 37)
                    .texOffs(128, 0)
                    .addBox(-10, -2, -25, 20, 7, 5)
                    .texOffs(184, 0)
                    .addBox(-10.5F, -1.75F, 17, 21, 7, 8)
                    .texOffs(244, 0)
                    .addBox(-6, -1, 24, 12, 5, 7)
                    .texOffs(296, 0)
                    .addBox(-9, 6, -16, 18, 2, 28),
                PartPose.offset(0, 7, 0));
    for (int sign : new int[] {-1, 1}) {
      PartDefinition wing =
          body.addOrReplaceChild(
              sign > 0 ? "left" : "right",
              wingStrips(sign, 0, 5, 0),
              PartPose.offset(sign * 11, 0, 0));
      var tip =
          wing.addOrReplaceChild(
              "tip", wingStrips(sign, 5, 8, 25), PartPose.offset(sign * 25, 0, 0));
      tip.addOrReplaceChild("edge", wingStrips(sign, 8, 12, 40), PartPose.offset(sign * 15, 0, 0));
      body.addOrReplaceChild(
          sign > 0 ? "left_lobe" : "right_lobe",
          CubeListBuilder.create()
              .texOffs(402, 0)
              .mirror(sign < 0)
              .addBox(-2, -1, -9, 4, 4, 10)
              .texOffs(436, 0)
              .addBox(-1.5F, -1.5F, -13, 3, 3, 4),
          PartPose.offsetAndRotation(sign * 8, 1, -24, -0.08F, -sign * 0.22F, 0));
      body.addOrReplaceChild(
          sign > 0 ? "left_eye" : "right_eye",
          CubeListBuilder.create().texOffs(472, 0).mirror(sign < 0).addBox(-2, -2, -2, 4, 3, 5),
          PartPose.offset(sign * 10.5F, -2.1F, -17));
    }
    PartDefinition tail =
        body.addOrReplaceChild(
            "tail",
            CubeListBuilder.create()
                .texOffs(0, 50)
                .addBox(-2.5F, -1.5F, -1, 5, 4, 17)
                .texOffs(48, 50)
                .addBox(-1, -4, 2, 2, 3, 6),
            PartPose.offset(0, 1, 30));
    tail.addOrReplaceChild(
        "whip",
        CubeListBuilder.create()
            .texOffs(72, 50)
            .addBox(-1.5F, -1, -1, 3, 2, 15)
            .texOffs(118, 50)
            .addBox(-0.5F, -0.5F, 14, 1, 1, 11),
        PartPose.offset(0, 0.5F, 15));
    return LayerDefinition.create(mesh, 512, 256);
  }

  private static CubeListBuilder wingStrips(int sign, int first, int end, int hinge) {
    // Each run is one cross-section of the tapered disc, measured from the wing root.
    int[][] strips = {
      {-1, 5, -21, 24, 6}, {5, 10, -19, 22, 5}, {10, 15, -16, 21, 5},
      {15, 20, -13, 20, 4}, {20, 25, -10, 20, 4}, {24, 30, -7, 19, 3},
      {30, 35, -4, 20, 3}, {35, 40, 0, 21, 2}, {40, 44, 4, 22, 2},
      {44, 48, 8, 23, 2}, {48, 51, 13, 24, 1}, {51, 53, 18, 25, 1}
    };
    CubeListBuilder cubes = CubeListBuilder.create().mirror(sign < 0);
    for (int i = first; i < end; i++) {
      int[] strip = strips[i];
      int x = sign > 0 ? strip[0] - hinge : hinge - strip[1];
      cubes
          .texOffs(i % 4 * 112, 80 + i / 4 * 54)
          .addBox(
              x,
              1 - strip[4] * 0.5F + i * 0.08F,
              strip[2],
              strip[1] - strip[0],
              strip[4],
              strip[3] - strip[2]);
    }
    return cubes;
  }

  @Override
  public void setupAnim(SkyRay ray, float stride, float speed, float age, float yaw, float pitch) {
    pose(ray.flightPose(age - ray.tickCount), ray.isDefending(), ray.isCalf());
  }

  public void pose(SkyRayWings.Pose pose, boolean defending, boolean calf) {
    left.xScale = calf ? SkyRayWings.CALF_WING_SPAN : 1;
    right.xScale = left.xScale;
    tail.zScale = calf ? SkyRayWings.CALF_TAIL_LENGTH : 1;
    left.zRot = pose.shoulder();
    right.zRot = -left.zRot;
    leftTip.zRot = pose.middle();
    rightTip.zRot = -leftTip.zRot;
    leftEdge.zRot = pose.tip();
    rightEdge.zRot = -leftEdge.zRot;
    body.xRot = pose.pitch();
    body.zRot = pose.bank();
    tail.yRot = pose.bank() * 0.35F + Mth.sin(pose.phase() - 0.8F) * 0.055F * pose.power();
    tail.xRot = defending ? 0.40F : -0.035F;
    whip.yRot = pose.bank() * 0.30F + Mth.sin(pose.phase() - 1.8F) * 0.09F * pose.power();
    whip.xRot = defending ? 0.48F : 0.06F;
  }
}
