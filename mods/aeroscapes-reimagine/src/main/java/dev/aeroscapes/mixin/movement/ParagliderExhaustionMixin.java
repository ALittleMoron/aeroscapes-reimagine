package dev.aeroscapes.mixin.movement;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.aeroscapes.movement.ParagliderExhaustion;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import tictim.paraglider.config.Cfg;
import tictim.paraglider.impl.movement.ClientPlayerMovement;

@Mixin(value = ClientPlayerMovement.class, remap = false)
public abstract class ParagliderExhaustionMixin {
  @Shadow private boolean wasParagliding;

  @WrapWithCondition(
      method = "update",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/player/LocalPlayer;setSprinting(Z)V",
              ordinal = 0),
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Only the depleted-reserve cancellation, not the glider pose.
  private boolean aeroscapes$stopRunning(LocalPlayer player, boolean sprinting) {
    return aeroscapes$movementUsesStamina();
  }

  @WrapWithCondition(
      method = "update",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/player/LocalPlayer;setSwimming(Z)V"),
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The same native running option also governs swimming costs.
  private boolean aeroscapes$stopSwimming(LocalPlayer player, boolean swimming) {
    return aeroscapes$movementUsesStamina();
  }

  private boolean aeroscapes$movementUsesStamina() {
    return ParagliderExhaustion.shouldStopMovement(
        Cfg.get().runningConsumesStamina(),
        ((ClientPlayerMovement) (Object) this).state().paragliding(),
        wasParagliding);
  }
}
