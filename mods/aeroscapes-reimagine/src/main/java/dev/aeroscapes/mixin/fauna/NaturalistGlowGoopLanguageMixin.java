package dev.aeroscapes.mixin.fauna;

import dev.aeroscapes.fauna.FaunaLanguage;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.world.item.GlowGoopItem", remap = false)
public abstract class NaturalistGlowGoopLanguageMixin {
  @Redirect(
      method = "appendHoverText",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Keep the native Shift-dependent text and formatting.
  private static MutableComponent aeroscapes$translateLiteral(String source) {
    return FaunaLanguage.literal(source);
  }
}
