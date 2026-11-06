package dev.aeroscapes.movement;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.IEMFModel;
import traben.entity_model_features.models.animation.state.EMFBipedPose;
import traben.entity_model_features.models.animation.state.EMFEntityRenderState;
import traben.entity_model_features.models.animation.state.EMFState;
import traben.entity_model_features.models.parts.EMFModelPartRoot;

/** Keeps the per-render overlay out of shared player models and EMF's armor pose. */
public final class HookPoseSnapshot {
  private static final ThreadLocal<Deque<HookPoseSnapshot>> ACTIVE =
      ThreadLocal.withInitial(ArrayDeque::new);
  private HookModelPose parts;
  private AbstractClientPlayer player;
  private PlayerModel<?> model;
  private EMFModelPartRoot root;
  private float partialTick;
  private EMFEntityRenderState state;
  private EMFBipedPose bipedPose;

  public HookPoseSnapshot() {
    ACTIVE.get().push(this);
  }

  public void apply(AbstractClientPlayer player, PlayerModel<?> model, float partialTick) {
    // Remounting EMF's API entity state resets the player and invalidates its render guard.
    if (model instanceof IEMFModel emf && emf.emf$isEMFModel()) {
      root = emf.emf$getEMFRootModel();
      if (root != null) root.animate();
    }
    this.player = player;
    this.model = model;
    this.partialTick = partialTick;
    state = EMFState.state();
    if (state != null) bipedPose = state.getBipedPose();
    capture();
    overlay();
  }

  private void capture() {
    parts =
        new HookModelPose(
            List.of(
                model.rightArm,
                model.leftArm,
                model.body,
                model.leftLeg,
                model.rightLeg,
                model.rightSleeve,
                model.leftSleeve,
                model.leftPants,
                model.rightPants,
                model.jacket));
  }

  private void overlay() {
    HookHangingPose.thirdPerson(player, model, partialTick);
    if (state != null) state.setBipedPose(new EMFBipedPose(model));
  }

  public void restore() {
    restoreParts();
    if (state != null) state.setBipedPose(bipedPose);
    if (ACTIVE.get().pop() != this) throw new IllegalStateException("Unbalanced hook render");
    if (ACTIVE.get().isEmpty()) ACTIVE.remove();
  }

  private void restoreParts() {
    if (parts != null) parts.restore();
  }

  public static final class AnimationHook extends EMFAnimationApi.EMFAnimationHook {
    @Override
    public boolean onAnimationStart(AnimationContext context, boolean wasCancelledByOtherHook) {
      HookPoseSnapshot saved = current(context);
      if (saved != null) {
        saved.restoreParts();
        saved.state.setBipedPose(saved.bipedPose);
      }
      return true;
    }

    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
      HookPoseSnapshot saved = current(context);
      if (saved == null) return;
      saved.capture();
      if (context.error() == null) saved.overlay();
    }

    @SuppressWarnings("deprecation") // Exact ETF 7.2.6 live entity accessor.
    private static HookPoseSnapshot current(AnimationContext context) {
      HookPoseSnapshot saved = ACTIVE.get().peek();
      var state = context.activeState();
      return saved != null
              && saved.model != null
              && state != null
              && !state.isFirstPersonHand()
              && state.entity() == saved.player
              && context.animatingModelRoot() == saved.root
          ? saved
          : null;
    }
  }
}
