package dev.aeroscapes.atmosphere;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import traben.entity_model_features.models.IEMFModel;
import traben.entity_texture_features.features.state.ETFEntityRenderState;
import traben.entity_texture_features.features.state.ETFState;
import traben.entity_texture_features.utils.ETFEntity;
import virtuoel.pehkui.util.ScaleUtils;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class FirstPersonBody {
  private static final float PLAYER_RENDER_SCALE = 0.9375F;
  @Nullable private static RenderPass active;
  @Nullable private static BodyRenderer wideBody;
  @Nullable private static BodyRenderer slimBody;

  private FirstPersonBody() {}

  @SubscribeEvent
  public static void reload(EntityRenderersEvent.AddLayers event) {
    // Use the same layered model bake as third person, with models reserved for this camera pass.
    wideBody = new BodyRenderer(event.getContext(), false);
    slimBody = new BodyRenderer(event.getContext(), true);
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
    var client = Minecraft.getInstance();
    var player = client.player;
    var camera = event.getCamera();
    if (active != null
        || player == null
        || camera.getEntity() != player
        || camera.isDetached()
        || !client.options.getCameraType().isFirstPerson()
        || client.options.hideGui
        || client.gameRenderer.isPanoramicMode()
        || player.isSpectator()
        || player.isInvisible()
        || !player.isAlive()
        || player.isSleeping()
        || player.isScoping()
        || player.isAutoSpinAttack()
        || player.getPose() == Pose.SWIMMING) return;

    var body = player.getSkin().model() == PlayerSkin.Model.SLIM ? slimBody : wideBody;
    if (body == null) return;
    var dispatcher = client.getEntityRenderDispatcher();
    float partial = camera.getPartialTickTime();
    float width = player.getScale();
    float height = width;
    if (ModList.get().isLoaded("pehkui")) {
      width *= ScaleUtils.getModelWidthScale(player, partial);
      height *= ScaleUtils.getModelHeightScale(player, partial);
    }

    int light = dispatcher.getPackedLightCoords(player, partial);
    float yaw = Mth.rotLerp(partial, player.yRotO, player.getYRot());
    var buffers = client.renderBuffers().bufferSource();
    var poses = event.getPoseStack();
    poses.pushPose();
    try {
      place(poses, camera.rotation(), camera.getXRot(), width, height, player.isFallFlying());
      var pass = new RenderPass(body, player, new PoseStack(), poses);
      active = pass;
      Runnable draw =
          () -> body.renderer().render(player, yaw, partial, pass.nativePoses(), buffers, light);
      if (ModList.get().isLoaded("entity_texture_features")) renderWithEntityState(player, draw);
      else draw.run();
    } finally {
      active = null;
      poses.popPose();
    }
    buffers.endLastBatch();
  }

  private static void renderWithEntityState(AbstractClientPlayer player, Runnable draw) {
    // The dispatcher normally mounts this state before calling the player renderer.
    var state = ETFEntityRenderState.forEntity((ETFEntity) player);
    if (state == null || state == ETFState.state()) {
      draw.run();
      return;
    }
    ETFState.mount(state);
    try {
      draw.run();
    } finally {
      ETFState.stackVerify(state);
      ETFState.unMount();
    }
  }

  private record BodyRenderer(PlayerRenderer renderer, BodyArmor armor) {
    private BodyRenderer(EntityRendererProvider.Context context, boolean slim) {
      this(new PlayerRenderer(context, slim), context.getModelManager());
    }

    private BodyRenderer(PlayerRenderer renderer, ModelManager models) {
      this(renderer, new BodyArmor(renderer, models));
    }
  }

  private record RenderPass(
      BodyRenderer body, AbstractClientPlayer player, PoseStack nativePoses, PoseStack bodyPoses) {}

  public static boolean owns(Object renderer, Entity entity, PoseStack poses) {
    var pass = active;
    return pass != null
        && pass.body().renderer() == renderer
        && pass.player() == entity
        && pass.nativePoses() == poses;
  }

  static void place(
      PoseStack poses,
      Quaternionf camera,
      float pitch,
      float width,
      float height,
      boolean gliding) {
    // The world pass already applies the inverse camera rotation. Retain look-down pitch in view
    // space.
    poses.mulPose(camera);
    poses.mulPose(Axis.XP.rotationDegrees(pitch));
    poses.translate(0.0F, -0.22F * height, 0.32F * width);
    if (gliding) poses.mulPose(Axis.XP.rotationDegrees(-12.0F));
    poses.scale(
        -PLAYER_RENDER_SCALE * width, -PLAYER_RENDER_SCALE * height, PLAYER_RENDER_SCALE * width);
  }

  public static void renderBody(
      PlayerModel<?> model, VertexConsumer skin, int light, int overlay, int color) {
    var pass = active;
    if (pass == null) return;
    if (ModList.get().isLoaded("entity_model_features")
        && model instanceof IEMFModel emf
        && emf.emf$isEMFModel()) {
      var root = emf.emf$getEMFRootModel();
      // EMF normally evaluates the custom pose during the first part draw. The anchored
      // pass draws individual branches and keeps the renderer/root transform off the geometry.
      root.oneTimeRunnable();
      root.animate();
    }
    anchorTorso(pass.bodyPoses(), model.body);
    renderParts(model, pass.bodyPoses(), skin, light, overlay, color);
  }

  static void anchorTorso(PoseStack poses, ModelPart torso) {
    // Fresh positions the shoulder pivot around the hips. Place that pivot at the camera
    // anchor while keeping the same spacing between the torso, limbs, clothing and armor.
    poses.translate(-torso.x / 16.0F, -torso.y / 16.0F, -torso.z / 16.0F);
  }

  static void renderParts(
      PlayerModel<?> model,
      PoseStack poses,
      VertexConsumer skin,
      int light,
      int overlay,
      int color) {
    model.body.render(poses, skin, light, overlay, color);
    model.leftLeg.render(poses, skin, light, overlay, color);
    model.rightLeg.render(poses, skin, light, overlay, color);
    model.jacket.render(poses, skin, light, overlay, color);
    model.leftPants.render(poses, skin, light, overlay, color);
    model.rightPants.render(poses, skin, light, overlay, color);
  }

  public static void renderArmor(
      MultiBufferSource buffers,
      int light,
      AbstractClientPlayer player,
      float phase,
      float speed,
      float partial,
      float age,
      float headYaw,
      float headPitch) {
    var pass = active;
    if (pass == null) return;
    pass.body()
        .armor()
        .render(
            pass.bodyPoses(),
            buffers,
            light,
            player,
            phase,
            speed,
            partial,
            age,
            headYaw,
            headPitch);
  }

  private static final class BodyArmor
      extends HumanoidArmorLayer<
          AbstractClientPlayer,
          PlayerModel<AbstractClientPlayer>,
          HumanoidModel<AbstractClientPlayer>> {
    private BodyArmor(PlayerRenderer renderer, ModelManager models) {
      super(
          renderer,
          mesh(LayerDefinitions.INNER_ARMOR_DEFORMATION),
          mesh(LayerDefinitions.OUTER_ARMOR_DEFORMATION),
          models);
    }

    private static HumanoidModel<AbstractClientPlayer> mesh(CubeDeformation deformation) {
      return new BodyArmorMesh(
          LayerDefinition.create(HumanoidArmorModel.createBodyLayer(deformation), 64, 32)
              .bakeRoot());
    }

    @Override
    protected Model getArmorModelHook(
        AbstractClientPlayer player,
        ItemStack stack,
        EquipmentSlot slot,
        HumanoidModel<AbstractClientPlayer> model) {
      // Arbitrary replacement meshes can contain head and arm geometry outside the vanilla parts.
      return model;
    }
  }

  private static final class BodyArmorMesh extends HumanoidArmorModel<AbstractClientPlayer> {
    private BodyArmorMesh(ModelPart root) {
      super(root);
    }

    @Override
    protected Iterable<ModelPart> headParts() {
      return List.of();
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
      return List.of(body, rightLeg, leftLeg);
    }
  }
}
