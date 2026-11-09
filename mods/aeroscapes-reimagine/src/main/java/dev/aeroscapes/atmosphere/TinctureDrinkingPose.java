package dev.aeroscapes.atmosphere;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

/** Keeps NEA's drinking arm when the player pack later evaluates its own poses. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class TinctureDrinkingPose {
  private static final ResourceLocation TINCTURE =
      ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "netherward_tincture");
  private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

  private TinctureDrinkingPose() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    var mods = ModList.get();
    if (!mods.isLoaded("irons_spellbooks")
        || !mods.isLoaded("notenoughanimations")
        || !mods.isLoaded("entity_model_features")
        || !mods.isLoaded("entity_texture_features")) return;
    event.enqueueWork(
        () -> {
          try {
            EMFAnimationApi.registerAnimationHook(new DrinkingHook());
          } catch (Exception failure) {
            throw new IllegalStateException(
                "Cannot register Netherward drinking pose compatibility", failure);
          }
        });
  }

  public static boolean active(AbstractClientPlayer player) {
    return player.getUseItemRemainingTicks() > 0
        && TINCTURE.equals(BuiltInRegistries.ITEM.getKey(player.getUseItem().getItem()));
  }

  public static void render(AbstractClientPlayer player, Runnable render) {
    var previous = ACTIVE.get();
    var frame = new Frame(player);
    ACTIVE.set(frame);
    try {
      render.run();
    } finally {
      frame.restore();
      if (previous == null) ACTIVE.remove();
      else ACTIVE.set(previous);
    }
  }

  public static void usedNativePose(AbstractClientPlayer player, PlayerModel<?> model) {
    var frame = ACTIVE.get();
    if (frame != null && frame.player == player) frame.model = model;
  }

  private static final class Frame {
    final AbstractClientPlayer player;
    PlayerModel<?> model;
    ModelPart arm;
    ModelPart sleeve;
    PartPose nativeRotation;
    PartPose beforeArm;
    PartPose beforeSleeve;
    EMFEntityRenderState state;
    EMFBipedPose beforeBiped;

    Frame(AbstractClientPlayer player) {
      this.player = player;
    }

    void capture() {
      boolean right =
          (player.getMainArm() == HumanoidArm.RIGHT)
              == (player.getUsedItemHand() == InteractionHand.MAIN_HAND);
      arm = right ? model.rightArm : model.leftArm;
      sleeve = right ? model.rightSleeve : model.leftSleeve;
      nativeRotation = arm.storePose();
    }

    void apply(EMFEntityRenderState activeState) {
      if (beforeArm == null) {
        beforeArm = arm.storePose();
        beforeSleeve = sleeve.storePose();
        state = activeState;
        beforeBiped = state.getBipedPose();
      }
      for (var part : new ModelPart[] {arm, sleeve}) {
        part.xRot = nativeRotation.xRot;
        part.yRot = nativeRotation.yRot;
        part.zRot = nativeRotation.zRot;
      }
      activeState.setBipedPose(new EMFBipedPose(model));
    }

    void restore() {
      if (beforeArm == null) return;
      arm.loadPose(beforeArm);
      sleeve.loadPose(beforeSleeve);
      state.setBipedPose(beforeBiped);
    }
  }

  private static final class DrinkingHook extends EMFAnimationApi.EMFAnimationHook {
    @Override
    public boolean onAnimationStart(AnimationContext context, boolean wasCancelledByOtherHook) {
      var frame = current(context);
      if (frame != null && frame.nativeRotation == null) frame.capture();
      return true;
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
      var frame = current(context);
      if (frame != null
          && frame.nativeRotation != null
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
