package dev.aeroscapes.mixin.fauna;

import dev.aeroscapes.fauna.FaunaLanguage;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.neoforge.NaturalistNeoForge", remap = false)
public abstract class NaturalistPackTitleMixin {
  @Redirect(
      method = "*",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The provider's optional built-in pack title is a literal.
  private static MutableComponent aeroscapes$translateTitle(String source) {
    return FaunaLanguage.literal(source);
  }
}
