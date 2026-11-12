package dev.aeroscapes.mixin.engineering.controls;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(
    targets = "net.liukrast.deployer.lib.logistics.board.ScrollOptionPanelBehaviour",
    remap = false)
public abstract class ScrollOptionLabelMixin {
  @Redirect(
      method = "createBoard",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the Mixin injector.
  private MutableComponent aeroscapes$selectionLabel(String source) {
    return Component.translatable("deployer.select");
  }
}
