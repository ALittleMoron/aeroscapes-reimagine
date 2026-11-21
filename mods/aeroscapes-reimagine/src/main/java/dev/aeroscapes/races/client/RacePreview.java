package dev.aeroscapes.races.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceTraits;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelPart;

final class RacePreview {
  private final ModelPart features = RaceFeatures.makeHair();
  private ModelPart model;
  private PlayerSkin.Model skinType;

  static ModelPart standingSkin(boolean slim) {
    // The registered player layers contain FA+Player's animated EMF roots.
    var root =
        LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64)
            .bakeRoot();
    root.getChild("ear").visible = false;
    root.getChild("cloak").visible = false;
    root.getChild("right_arm").zRot = 0.065f;
    root.getChild("left_arm").zRot = -0.065f;
    root.getChild("right_sleeve").copyFrom(root.getChild("right_arm"));
    root.getChild("left_sleeve").copyFrom(root.getChild("left_arm"));
    return root;
  }

  void draw(
      GuiGraphics graphics,
      AbstractClientPlayer player,
      Race race,
      int style,
      int tint,
      float yaw,
      int x,
      int y,
      int availableHeight,
      boolean closeUp) {
    var skin = player.getSkin();
    if (skin.model() != skinType) {
      skinType = skin.model();
      model = standingSkin(skinType == PlayerSkin.Model.SLIM);
    }
    model.getChild("hat").visible = player.isModelPartShown(PlayerModelPart.HAT);
    model.getChild("jacket").visible = player.isModelPartShown(PlayerModelPart.JACKET);
    model.getChild("right_sleeve").visible = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
    model.getChild("left_sleeve").visible = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
    model.getChild("right_pants").visible =
        player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
    model.getChild("left_pants").visible = player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
    float scale = availableHeight / (closeUp ? 1.1f : 2.26f);
    float width = scale * RaceTraits.width(race);
    float height = scale * RaceTraits.height(race);
    var pose = graphics.pose();
    graphics.flush();
    pose.pushPose();
    Lighting.setupForEntityInInventory();
    try {
      pose.translate(x, y, 80);
      pose.scale(width, height, -width);
      pose.mulPose(Axis.YP.rotationDegrees(yaw));
      pose.translate(0, closeUp ? 0.25f : -1.5f, 0);
      var buffers = graphics.bufferSource();
      model.render(
          pose,
          buffers.getBuffer(RenderType.entityTranslucent(skin.texture())),
          LightTexture.FULL_BRIGHT,
          OverlayTexture.NO_OVERLAY);
      RaceFeatures.renderDetails(
          pose,
          buffers,
          LightTexture.FULL_BRIGHT,
          OverlayTexture.NO_OVERLAY,
          skin.texture(),
          race,
          style,
          tint,
          features,
          false,
          false);
      graphics.flush();
    } finally {
      pose.popPose();
      Lighting.setupFor3DItems();
    }
  }
}
