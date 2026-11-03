package dev.aeroscapes.mixin.movement;

import dev.aeroscapes.movement.HookInputCompatibility;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class HookSprintMixin {
  @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned LocalPlayer mixin injection.
  private void aeroscapes$ropeSprint(CallbackInfoReturnable<Boolean> callback) {
    if (HookInputCompatibility.consumesSprint((LocalPlayer) (Object) this))
      callback.setReturnValue(false);
  }

  @Inject(method = "isShiftKeyDown", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned LocalPlayer mixin injection.
  private void aeroscapes$ropeSneak(CallbackInfoReturnable<Boolean> callback) {
    if (HookInputCompatibility.consumesSneak((LocalPlayer) (Object) this))
      callback.setReturnValue(false);
  }
}
