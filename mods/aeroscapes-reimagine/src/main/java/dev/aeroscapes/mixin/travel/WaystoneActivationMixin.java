package dev.aeroscapes.mixin.travel;

import dev.aeroscapes.travel.TravelIntegration;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.core.PlayerWaystoneManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerWaystoneManager.class, remap = false)
public abstract class WaystoneActivationMixin {
  @Inject(method = "activateWaystone", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native calls include automatic placement activation.
  private static void aeroscapes$payActivation(
      Player player, Waystone stone, CallbackInfo callback) {
    if (!TravelIntegration.canActivate(player, stone, true)) callback.cancel();
  }
}
