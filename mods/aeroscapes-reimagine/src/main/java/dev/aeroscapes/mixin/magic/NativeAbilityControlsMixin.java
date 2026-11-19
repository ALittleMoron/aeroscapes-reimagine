package dev.aeroscapes.mixin.magic;

import dev.aeroscapes.magic.client.AbilityKeys;
import io.redspace.ironsspellbooks.player.ClientInputEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientInputEvents.class, remap = false)
public abstract class NativeAbilityControlsMixin {
  @Inject(method = "handleKeybinds", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$preparedControls(CallbackInfo callback) {
    AbilityKeys.forwardNativeBindings();
  }

  @Inject(method = "handleSpellBarScrollModifier", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$preparedSelection(
      int delta, CallbackInfoReturnable<Boolean> callback) {
    callback.setReturnValue(AbilityKeys.scroll(delta));
  }
}
