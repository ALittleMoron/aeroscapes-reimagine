package dev.aeroscapes.movement;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import tictim.paraglider.ParagliderUtils;
import tictim.paraglider.api.movement.Movement;
import tictim.paraglider.contents.ParagliderTags;
import traben.entity_model_features.EMFAnimationApi;
import traben.entity_model_features.models.IEMFModel;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class ParagliderAnimationCompatibility {
  private ParagliderAnimationCompatibility() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    if (!ModList.get().isLoaded("paraglider")
        || !ModList.get().isLoaded("entity_model_features")
        || !ModList.get().isLoaded("entity_texture_features")) return;
    event.enqueueWork(
        () -> {
          for (var expected :
              Map.of(
                      "paraglider", "21.1.5",
                      "entity_model_features", "3.3.12",
                      "entity_texture_features", "7.2.6",
                      "neoforge", "21.1.230")
                  .entrySet()) {
            String actual =
                ModList.get()
                    .getModContainerById(expected.getKey())
                    .orElseThrow()
                    .getModInfo()
                    .getVersion()
                    .toString();
            if (!expected.getValue().equals(actual))
              throw new IllegalStateException(
                  "Review paraglider animation compatibility for "
                      + expected.getKey()
                      + " "
                      + actual);
          }
          try {
            EMFAnimationApi.registerAnimationHook(new ParaglidingPose());
          } catch (Exception failure) {
            throw new IllegalStateException(
                "Cannot register paraglider animation compatibility", failure);
          }
        });
  }

  private static final class ParaglidingPose extends EMFAnimationApi.EMFAnimationHook {
    @Override
    public void onAnimationEnd(AnimationContext context, boolean wasCancelledByHook) {
      var state = context.activeState();
      if (wasCancelledByHook
          || context.error() != null
          || state == null
          || state.isFirstPersonHand()) return;
      @SuppressWarnings(
          "deprecation") // ETF 7.2.6 exposes only entity() for the live 1.21.1 entity.
      var entity = state.entity();
      if (!(entity instanceof AbstractClientPlayer player)) return;
      var stack = player.getMainHandItem();
      if (!stack.is(ParagliderTags.PARAGLIDERS)
          || !ParagliderUtils.getCaps(stack).isParagliding(stack)
          || !Movement.get(player).state().paragliding()) return;
      if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player)
          instanceof PlayerRenderer renderer)) return;
      var model = renderer.getModel();
      if (!(model instanceof IEMFModel emf)
          || emf.emf$getEMFRootModel() != context.animatingModelRoot()) return;
      var pose =
          IClientItemExtensions.of(stack).getArmPose(player, InteractionHand.MAIN_HAND, stack);
      if (pose == null) return;

      // EMF evaluates FA's arm expressions after the provider's ordinary setupAnim pose.
      PartPose head = model.head.storePose();
      PartPose leftLeg = model.leftLeg.storePose();
      PartPose rightLeg = model.rightLeg.storePose();
      try {
        pose.applyTransform(model, player, player.getMainArm());
      } finally {
        restoreRotation(model.head, head);
        restoreRotation(model.leftLeg, leftLeg);
        restoreRotation(model.rightLeg, rightLeg);
      }
      model.leftSleeve.copyFrom(model.leftArm);
      model.rightSleeve.copyFrom(model.rightArm);
    }
  }

  private static void restoreRotation(ModelPart part, PartPose pose) {
    part.xRot = pose.xRot;
    part.yRot = pose.yRot;
    part.zRot = pose.zRot;
  }
}
