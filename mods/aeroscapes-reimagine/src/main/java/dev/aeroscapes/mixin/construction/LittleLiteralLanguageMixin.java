package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.ConstructionLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets = "team.creative.littletiles.common.gui.tool.blueprint.GuiBlueprint", remap = false)
public abstract class LittleLiteralLanguageMixin {
  @ModifyArg(
      method = "create()V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0,
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Applied to exact LittleTiles display call sites.
  private static String aeroscapes$translateLiteral(String text) {
    return ConstructionLanguage.literal(text);
  }
}
