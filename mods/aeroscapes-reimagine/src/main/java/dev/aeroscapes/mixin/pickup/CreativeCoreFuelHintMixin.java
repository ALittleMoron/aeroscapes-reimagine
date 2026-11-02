package dev.aeroscapes.mixin.pickup;

import dev.aeroscapes.pickup.CreativeCoreLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(
    targets = "team.creative.creativecore.common.util.ingredient.GuiCreativeIngredientHandler$4",
    remap = false)
public abstract class CreativeCoreFuelHintMixin {
  @ModifyArg(
      method = "createControls",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // This anonymous provider widget has one literal hint.
  private String aeroscapes$translateFuelHint(String text) {
    return CreativeCoreLanguage.translate(text);
  }
}
