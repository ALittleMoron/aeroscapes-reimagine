package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.ConstructionLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
    targets = "team.creative.littletiles.common.gui.signal.dialog.GuiDialogSignal",
    remap = false)
public abstract class LittleSignalDelayLanguageMixin {
  @ModifyArg(
      method = "changed(Lteam/creative/creativecore/common/gui/event/GuiControlChangedEvent;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"),
      index = 0,
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The two native delay states include literal units.
  private static String aeroscapes$translateDelay(String text) {
    return ConstructionLanguage.literal(text);
  }
}
