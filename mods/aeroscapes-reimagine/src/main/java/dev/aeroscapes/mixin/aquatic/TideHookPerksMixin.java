package dev.aeroscapes.mixin.aquatic;

import com.li64.tide.registries.items.TideFishingRodItem;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TideFishingRodItem.class, remap = false)
public abstract class TideHookPerksMixin {
  @ModifyExpressionValue(
      method = "castHook",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/item/Item;toString()Ljava/lang/String;"),
      require = 3)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked at the three pinned native hook checks.
  private String aeroscapes$registeredHookName(String original) {
    return switch (original) {
      case "hybrid_aquatic:barbed_hook" -> "barbed_hook";
      case "hybrid_aquatic:glowing_hook" -> "glowing_hook";
      case "hybrid_aquatic:magnetic_hook" -> "magnetic_hook";
      default -> original;
    };
  }
}
