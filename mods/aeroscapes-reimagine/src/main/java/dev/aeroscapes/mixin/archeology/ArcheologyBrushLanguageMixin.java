package dev.aeroscapes.mixin.archeology;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.Pandarix.item.BetterBrushItem", remap = false)
public abstract class ArcheologyBrushLanguageMixin {
  @Redirect(
      method = "appendHoverText",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Preserve the provider's live speed calculation.
  private MutableComponent aeroscapes$brushSpeed(String source) {
    String suffix = " Brushing Speed";
    return source.endsWith(suffix)
        ? Component.translatable(
            "aeroscapes_reimagine.archeology.brush_speed",
            source.substring(0, source.length() - suffix.length()))
        : Component.literal(source);
  }
}
