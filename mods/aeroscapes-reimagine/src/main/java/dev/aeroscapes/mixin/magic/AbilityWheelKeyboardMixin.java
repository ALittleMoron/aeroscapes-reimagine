package dev.aeroscapes.mixin.magic;

import dev.aeroscapes.magic.client.AbilityKeys;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class AbilityWheelKeyboardMixin {
  @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$selectWheel(
      long window, int key, int scanCode, int action, int modifiers, CallbackInfo callback) {
    if (window == Minecraft.getInstance().getWindow().getWindow()
        && AbilityKeys.wheelKey(key, action)) callback.cancel();
  }
}
