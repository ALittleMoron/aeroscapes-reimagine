package dev.aeroscapes.mixin.ecology;

import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vectorwing.farmersdelight.common.Configuration;

@Mixin(targets = "com.ninni.spawn.compat.farmersdelight.FarmersDelightCompat")
public abstract class SpawnFoodConfigurationMixin {
  @Redirect(
      method = "addEffectTooltip",
      at =
          @At(
              value = "FIELD",
              target =
                  "Lvectorwing/farmersdelight/common/Configuration;ENABLE_FOOD_EFFECT_TOOLTIP:Ljava/util/function/Supplier;"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The pinned provider uses the older field name/type.
  private static Supplier<Boolean> aeroscapes$foodTooltip() {
    return Configuration.FOOD_EFFECT_TOOLTIP;
  }

  @Redirect(
      method = {"applyEffects", "addEffectTooltip"},
      at =
          @At(
              value = "FIELD",
              target =
                  "Lvectorwing/farmersdelight/common/Configuration;ENABLE_VANILLA_SOUP_EXTRA_EFFECTS:Ljava/util/function/Supplier;"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Preserve the live common setting in both paths.
  private static Supplier<Boolean> aeroscapes$soupEffects() {
    return Configuration.VANILLA_SOUP_EXTRA_EFFECTS;
  }
}
