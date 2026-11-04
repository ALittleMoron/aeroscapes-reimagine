package dev.aeroscapes.mixin.fauna;

import dev.aeroscapes.fauna.FaunaLanguage;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(
    targets = {
      "io.github.bonsaistudi0s.crittersandcompanions.common.config.CACCommonConfig",
      "io.github.bonsaistudi0s.crittersandcompanions.common.config.CACCommonConfig$SpawningConfig",
      "io.github.bonsaistudi0s.crittersandcompanions.common.config.CACCommonConfig$NecklaceConfig",
      "io.github.bonsaistudi0s.crittersandcompanions.common.config.CACCommonConfig$GrapplingHookConfig",
      "io.github.bonsaistudi0s.crittersandcompanions.neoforge.CrittersAndCompanionsNeoForge"
    },
    remap = false)
public abstract class CrittersConfigurationLanguageMixin {
  @Redirect(
      method = "*",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Display aliases leave parser syntax, saved settings and bindings
  // intact.
  private static MutableComponent aeroscapes$translateLiteral(String source) {
    return FaunaLanguage.literal(source);
  }
}
