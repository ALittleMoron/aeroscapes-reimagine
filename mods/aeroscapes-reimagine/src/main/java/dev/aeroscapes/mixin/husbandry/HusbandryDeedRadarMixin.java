package dev.aeroscapes.mixin.husbandry;

import net.minecraft.world.entity.player.Player;
import org.athisworkshop.animalhusbandry.Animalhusbandry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Animalhusbandry.class, remap = false)
abstract class HusbandryDeedRadarMixin {
  @Inject(method = "lambda$init$2", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private static void aeroscapes$disableDeedLocator(Player player, CallbackInfo callback) {
    callback.cancel();
  }
}
