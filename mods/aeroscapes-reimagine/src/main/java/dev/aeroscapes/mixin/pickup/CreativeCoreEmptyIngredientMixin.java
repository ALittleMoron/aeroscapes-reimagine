package dev.aeroscapes.mixin.pickup;

import dev.aeroscapes.pickup.CreativeCoreLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(
    targets = "team.creative.creativecore.common.util.ingredient.CreativeIngredient$1",
    remap = false)
public abstract class CreativeCoreEmptyIngredientMixin {
  @ModifyArg(
      method = {"description", "descriptionDetail"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Empty ingredients display provider literals.
  private String aeroscapes$translateEmptyIngredient(String text) {
    return CreativeCoreLanguage.translate(text);
  }
}
