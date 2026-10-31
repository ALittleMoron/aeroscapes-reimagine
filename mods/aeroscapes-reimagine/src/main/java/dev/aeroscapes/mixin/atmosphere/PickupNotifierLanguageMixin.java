package dev.aeroscapes.mixin.atmosphere;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "fuzs.pickupnotifier.client.commands.ModReloadCommand", remap = false)
public abstract class PickupNotifierLanguageMixin {
  @Redirect(
      method = "reload",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The command's sole feedback literal.
  private static MutableComponent aeroscapes$reloadFeedback(String source) {
    return Component.translatable("aeroscapes.qol.pickupnotifier.reload");
  }
}
