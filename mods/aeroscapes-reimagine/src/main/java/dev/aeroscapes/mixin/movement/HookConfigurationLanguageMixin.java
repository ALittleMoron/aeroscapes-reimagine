package dev.aeroscapes.mixin.movement;

import dev.aeroscapes.movement.MovementLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "com.yori3o.yo_hooks.common.config.client.ConfigScreenFactory", remap = false)
public abstract class HookConfigurationLanguageMixin {
  @ModifyArg(
      method = "create",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native category literals bypass language resources.
  private static String aeroscapes$translateCategory(String text) {
    return MovementLanguage.hookCategory(text);
  }
}
