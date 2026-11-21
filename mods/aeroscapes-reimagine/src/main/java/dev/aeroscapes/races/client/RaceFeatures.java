package dev.aeroscapes.races.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aeroscapes.races.Race;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;

final class RaceFeatures
    extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
  private static final ResourceLocation HAIR =
      ResourceLocation.parse("aeroscapes_race_visuals:textures/entity/hair.png");
  private static final ResourceLocation TINTABLE_HAIR =
      ResourceLocation.parse("aeroscapes_race_visuals:textures/entity/tintable_hair.png");
  private static final ResourceLocation WHITE =
      ResourceLocation.parse("aeroscapes_reimagine:textures/entity/white.png");
  private final ModelPart hair;

  RaceFeatures(PlayerRenderer renderer) {
    super(renderer);
    hair = makeHair();
  }

  @Override
  public void render(
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      AbstractClientPlayer player,
      float limbSwing,
      float limbAmount,
      float partialTick,
      float age,
      float yaw,
      float pitch) {
    var profile = RaceClient.profile(player.getUUID());
    var head = getParentModel().head;
    if (profile == null
        || profile.race() == null
        || player.isInvisible()
        || !head.visible
        || head.skipDraw) return;
    var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
    boolean openHelmet =
        helmet.getItem() instanceof ArmorItem
            && BuiltInRegistries.ITEM.getKey(helmet.getItem()).getNamespace().equals("minecraft");
    pose.pushPose();
    head.translateAndRotate(pose);
    renderDetails(
        pose,
        buffers,
        light,
        LivingEntityRenderer.getOverlayCoords(player, 0),
        player.getSkin().texture(),
        profile.race(),
        profile.style(),
        profile.tint(),
        hair,
        !helmet.isEmpty(),
        openHelmet);
    pose.popPose();
  }

  static void renderDetails(
      PoseStack pose,
      MultiBufferSource buffers,
      int light,
      int overlay,
      ResourceLocation skin,
      Race race,
      int style,
      int tint,
      ModelPart hair,
      boolean covered,
      boolean openHelmet) {
    if (!race.validStyle(style)) return;
    if (race.hasEars()) {
      if (!covered)
        RaceEars.render(
            pose,
            buffers.getBuffer(
                RenderType.entityCutoutNoCull(tint == Race.DEFAULT_TINT ? skin : WHITE)),
            light,
            overlay,
            race,
            style,
            tint);
      return;
    }
    if (style == 0 || covered && !openHelmet) return;
    if (!race.hasBeard(style)) {
      if (!covered)
        hair.getChild("hair")
            .render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(HAIR)), light, overlay);
      return;
    }
    var material =
        buffers.getBuffer(
            RenderType.entityCutoutNoCull(tint == Race.DEFAULT_TINT ? HAIR : TINTABLE_HAIR));
    int argb = tint == Race.DEFAULT_TINT ? 0xffffffff : 0xff000000 | tint;
    if (!covered) hair.getChild("beard").render(pose, material, light, overlay, argb);
    if (race == Race.DWARF) {
      hair.getChild(style == 1 ? "fork" : "braid").render(pose, material, light, overlay, argb);
      if (style == 2)
        hair.getChild("braid_band")
            .render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(HAIR)), light, overlay);
    }
  }

  static ModelPart makeHair() {
    var mesh = new MeshDefinition();
    var root = mesh.getRoot();
    var tied = root.addOrReplaceChild("hair", CubeListBuilder.create(), PartPose.ZERO);
    lock(tied, "parting", -2.4f, -8.7f, -0.8f, 4.8f, 1.3f, 5.7f, -0.13f);
    lock(tied, "knot", -1.6f, -6.1f, 4.1f, 3.2f, 2.8f, 2.5f, 0);
    lock(tied, "tie", -1.15f, -3.65f, 4.4f, 2.3f, 0.9f, 1.9f, 0, 32);
    lock(tied, "tail", -1.35f, -2.95f, 4.2f, 2.7f, 3.8f, 2.1f, 0.14f);
    lock(tied, "ends", -0.8f, 0.55f, 4.75f, 1.65f, 1.3f, 1.5f, 0.14f);
    var beard = root.addOrReplaceChild("beard", CubeListBuilder.create(), PartPose.ZERO);
    lock(beard, "right_cheek", -3.95f, -2.65f, -4.65f, 1.35f, 2.9f, 1.8f, 0);
    lock(beard, "left_cheek", 2.6f, -2.65f, -4.65f, 1.35f, 2.9f, 1.8f, 0);
    lock(beard, "jaw", -3.2f, -0.95f, -4.8f, 6.4f, 1.65f, 1.75f, 0);
    lock(beard, "chin", -1.9f, 0.52f, -4.6f, 3.8f, 0.85f, 1.5f, 0);
    var fork = root.addOrReplaceChild("fork", CubeListBuilder.create(), PartPose.ZERO);
    lock(fork, "right", -3.12f, 0.35f, -4.5f, 2.65f, 3.1f, 1.55f, 0.05f);
    lock(fork, "left", 0.47f, 0.35f, -4.5f, 2.65f, 3.1f, 1.55f, 0.05f);
    lock(fork, "right_tip", -2.86f, 3.42f, -4.37f, 1.7f, 1.45f, 1.3f, 0.09f);
    lock(fork, "left_tip", 1.16f, 3.42f, -4.37f, 1.7f, 1.45f, 1.3f, 0.09f);
    var braid = root.addOrReplaceChild("braid", CubeListBuilder.create(), PartPose.ZERO);
    lock(braid, "chin", -2.45f, 0.5f, -4.9f, 4.9f, 2.2f, 2.05f, 0.04f);
    lock(braid, "upper", -1.85f, 2.52f, -4.72f, 3.7f, 2.25f, 1.85f, 0.07f);
    lock(root, "braid_band", -1.75f, 4.55f, -4.71f, 3.5f, 0.95f, 1.95f, 0, 32);
    lock(braid, "tassel", -1.35f, 5.48f, -4.5f, 2.7f, 1.8f, 1.55f, 0.12f);
    return LayerDefinition.create(mesh, 64, 16).bakeRoot();
  }

  private static void lock(
      PartDefinition parent,
      String name,
      float x,
      float y,
      float z,
      float width,
      float height,
      float depth,
      float tilt) {
    lock(parent, name, x, y, z, width, height, depth, tilt, 0);
  }

  private static void lock(
      PartDefinition parent,
      String name,
      float x,
      float y,
      float z,
      float width,
      float height,
      float depth,
      float tilt,
      int textureX) {
    parent.addOrReplaceChild(
        name,
        CubeListBuilder.create().texOffs(textureX, 0).addBox(0, 0, 0, width, height, depth),
        PartPose.offsetAndRotation(x, y, z, tilt, 0, 0));
  }
}
