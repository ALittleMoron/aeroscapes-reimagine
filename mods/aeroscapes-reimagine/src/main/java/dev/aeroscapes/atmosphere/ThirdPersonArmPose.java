package dev.aeroscapes.atmosphere;

import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.IEMFModel;
import traben.entity_model_features.models.animation.state.EMFBipedPose;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;

/** Preserves the chosen action pose across the player pack's later pose evaluation. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class ThirdPersonArmPose {
  private static final ResourceLocation TINCTURE =
      ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "netherward_tincture");
  private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

  private ThirdPersonArmPose() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    var mods = ModList.get();
    if (!mods.isLoaded("entity_model_features") || !mods.isLoaded("entity_texture_features"))
      return;
    event.enqueueWork(
        () -> {
          try {
            EMFAnimationApi.registerAnimationHook(new ArmHook());
          } catch (Exception failure) {
            throw new IllegalStateException(
                "Cannot register the third-person action arm pose hook", failure);
          }
        });
  }

  public static HumanoidArm drinkingArm(AbstractClientPlayer player) {
    if (player.getUseItemRemainingTicks() <= 0
        || !TINCTURE.equals(BuiltInRegistries.ITEM.getKey(player.getUseItem().getItem())))
      return null;
    return player.getUsedItemHand() == InteractionHand.OFF_HAND
        ? player.getMainArm().getOpposite()
        : player.getMainArm();
  }

  public static void render(
      AbstractClientPlayer player,
      HumanoidArm arm,
      float attackWeight,
      boolean nativeCombatAttack,
      float partialTick,
      Runnable render) {
    var previous = ACTIVE.get();
    var frame = new Frame(player, arm, attackWeight, nativeCombatAttack, partialTick);
    ACTIVE.set(frame);
    try {
      render.run();
    } finally {
      frame.restore();
      if (previous == null) ACTIVE.remove();
      else ACTIVE.set(previous);
    }
  }

  public static void usedNativePose(
      AbstractClientPlayer player, PlayerModel<?> model, boolean attacking) {
    var frame = ACTIVE.get();
    if (frame != null && frame.player == player && frame.attacking() == attacking) {
      frame.model = model;
      if (attacking) frame.capture();
    }
  }

  public static boolean hasAttackPose(AbstractClientPlayer player) {
    var frame = ACTIVE.get();
    return frame != null && frame.player == player && frame.attacking();
  }

  private static final class Frame {
    final AbstractClientPlayer player;
    final HumanoidArm side;
    final float attackWeight;
    final boolean nativeCombatAttack;
    final float partialTick;
    PlayerModel<?> model;
    List<Part> nativePose = List.of();
    List<Part> beforePose = List.of();
    EMFEntityRenderState state;
    EMFBipedPose beforeBiped;

    Frame(
        AbstractClientPlayer player,
        HumanoidArm side,
        float attackWeight,
        boolean nativeCombatAttack,
        float partialTick) {
      this.player = player;
      this.side = side;
      this.attackWeight = attackWeight;
      this.nativeCombatAttack = nativeCombatAttack;
      this.partialTick = partialTick;
    }

    boolean attacking() {
      return nativeCombatAttack || attackWeight > 0;
    }

    void capture() {
      if (nativeCombatAttack) {
        nativePose = captureUpperBody(model);
      } else if (attacking()) {
        // NEA smooths the model arms during setupAnim. Sample the action layer directly so
        // that its sharpest movement uses the same clock as the held item's grip rotation.
        var pose = ShortBladeAttackPresentation.samplePose(player, partialTick);
        if (pose == null) return;
        nativePose =
            List.of(
                new Part(model.body, pose.torso()),
                new Part(model.jacket, pose.torso()),
                new Part(model.rightArm, pose.rightArm()),
                new Part(model.rightSleeve, pose.rightArm()),
                new Part(model.leftArm, pose.leftArm()),
                new Part(model.leftSleeve, pose.leftArm()));
      } else {
        boolean right = side == HumanoidArm.RIGHT;
        ModelPart arm = right ? model.rightArm : model.leftArm;
        ModelPart sleeve = right ? model.rightSleeve : model.leftSleeve;
        PartPose pose = arm.storePose();
        nativePose = List.of(new Part(arm, pose), new Part(sleeve, pose));
      }
    }

    void apply(EMFEntityRenderState activeState) {
      beforePose = nativePose.stream().map(part -> part.captureCurrent()).toList();
      state = activeState;
      beforeBiped = state.getBipedPose();
      if (nativeCombatAttack) {
        for (Part part : nativePose) part.restore();
      } else if (attacking()) {
        // Keep the current torso height and translation from locomotion. Move the shoulders
        // with it while taking over the arm and torso angles from the action clip.
        for (Part part : nativePose) {
          if (part.part() == model.body || part.part() == model.jacket) part.rotate(attackWeight);
          else part.blend(attackWeight, model.body.x, model.body.y, model.body.z);
        }
      } else {
        for (Part part : nativePose) part.rotate(1);
      }
      activeState.setBipedPose(new EMFBipedPose(model));
    }

    void restore() {
      if (beforePose.isEmpty()) return;
      for (Part part : beforePose) part.restore();
      state.setBipedPose(beforeBiped);
      beforePose = List.of();
    }
  }

  static List<Part> captureUpperBody(PlayerModel<?> model) {
    var body = model.body.storePose();
    var right = model.rightArm.storePose();
    var left = model.leftArm.storePose();
    return List.of(
        new Part(model.body, body),
        new Part(model.jacket, body),
        new Part(model.rightArm, right),
        new Part(model.rightSleeve, right),
        new Part(model.leftArm, left),
        new Part(model.leftSleeve, left));
  }

  record Part(ModelPart part, PartPose pose) {
    Part captureCurrent() {
      return new Part(part, part.storePose());
    }

    void blend(float weight, float offsetX, float offsetY, float offsetZ) {
      part.setPos(
          Mth.lerp(weight, part.x, pose.x + offsetX),
          Mth.lerp(weight, part.y, pose.y + offsetY),
          Mth.lerp(weight, part.z, pose.z + offsetZ));
      rotate(weight);
    }

    void rotate(float weight) {
      part.setRotation(
          blendAngle(weight, part.xRot, pose.xRot),
          blendAngle(weight, part.yRot, pose.yRot),
          blendAngle(weight, part.zRot, pose.zRot));
    }

    void restore() {
      part.setPos(pose.x, pose.y, pose.z);
      part.setRotation(pose.xRot, pose.yRot, pose.zRot);
    }

    private static float blendAngle(float weight, float from, float to) {
      return from + weight * Mth.wrapDegrees((to - from) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
    }
  }

  private static final class ArmHook extends EMFAnimationApi.EMFAnimationHook {
    @Override
    public boolean onAnimationStart(AnimationContext context, boolean wasCancelledByOtherHook) {
      var frame = current(context);
      if (frame != null) {
        frame.restore();
        if (frame.nativePose.isEmpty()) frame.capture();
      }
      return true;
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
      var frame = current(context);
      if (frame != null
          && !frame.nativePose.isEmpty()
          && !wasCancelledByHook
          && context.error() == null) frame.apply(context.activeState());
    }

    @SuppressWarnings("deprecation") // ETF 7.2.6 exposes the live 1.21.1 entity here.
    private static Frame current(AnimationContext context) {
      var frame = ACTIVE.get();
      var state = context.activeState();
      return frame != null
              && state != null
              && !state.isFirstPersonHand()
              && state.entity() == frame.player
              && frame.model instanceof IEMFModel emf
              && emf.emf$getEMFRootModel() == context.animatingModelRoot()
          ? frame
          : null;
    }
  }
}
