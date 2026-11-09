package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.TinctureDrinkingPose;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.tr7zw.notenoughanimations.animations.hands.EatDrinkAnimation", remap = false)
abstract class TinctureNativeDrinkingMixin {
  @Inject(method = "apply", at = @At("RETURN"), require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Mark only when the provider actually chose to animate.
  private void aeroscapes$nativeDrinking(
      AbstractClientPlayer player,
      @Coerce Object data,
      PlayerModel<?> model,
      @Coerce Object arm,
      float delta,
      float swing,
      CallbackInfo info) {
    TinctureDrinkingPose.usedNativePose(player, model);
  }
}
