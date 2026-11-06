package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.ConstructionLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
    targets = "team.creative.littletiles.common.structure.registry.gui.LittleStorageGui",
    remap = false)
public abstract class LittleStorageLanguageMixin {
  @ModifyArg(
      method = "create(Lteam/creative/littletiles/common/structure/LittleStructure;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lteam/creative/creativecore/common/util/text/TextBuilder;text(Ljava/lang/String;)Lteam/creative/creativecore/common/util/text/TextBuilder;"),
      index = 0,
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The native caption contains a runtime slot count.
  private static String aeroscapes$translateCapacity(String text) {
    return ConstructionLanguage.literal(text);
  }
}
