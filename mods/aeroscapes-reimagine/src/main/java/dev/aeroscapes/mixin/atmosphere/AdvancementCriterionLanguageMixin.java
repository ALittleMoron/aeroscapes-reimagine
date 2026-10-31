package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.AdvancementCriterionTranslations;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "betteradvancements.common.util.CriterionGrid", remap = false)
public abstract class AdvancementCriterionLanguageMixin {
  @Redirect(
      method = "findOptimalCriterionGrid",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;translatableWithFallback(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Both obtained and spoiler criterion labels.
  private static MutableComponent aeroscapes$criterion(String key, String fallback) {
    return Component.translatableWithFallback(
        AdvancementCriterionTranslations.resolve(key, fallback, Language.getInstance()::has),
        fallback);
  }
}
