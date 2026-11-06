package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.ConstructionLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
    targets = "team.creative.littletiles.common.structure.registry.gui.LittleBedGui",
    remap = false)
public abstract class LittleBedDirectionLanguageMixin {
  @ModifyArg(
      method =
          "lambda$create$1(Lteam/creative/creativecore/common/util/math/base/Facing;)Lnet/minecraft/network/chat/Component;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0,
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The native enum callback supplies cardinal names.
  private static String aeroscapes$translateDirection(String text) {
    return ConstructionLanguage.literal(text);
  }
}
