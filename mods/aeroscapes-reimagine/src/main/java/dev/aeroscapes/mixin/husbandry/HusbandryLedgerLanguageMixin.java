package dev.aeroscapes.mixin.husbandry;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.husbandry.HusbandryLanguage;
import net.minecraft.network.chat.MutableComponent;
import org.athisworkshop.animalhusbandry.client.gui.FarmLedgerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FarmLedgerScreen.class, remap = false)
abstract class HusbandryLedgerLanguageMixin {
  @Redirect(
      method = "*",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private static MutableComponent aeroscapes$localizeComponent(String source) {
    return HusbandryLanguage.literal(source);
  }

  @ModifyArg(
      method = {
        "renderCoOwnersPage",
        "renderAnimalDetails",
        "renderCarePage",
        "drawStatusLine",
        "renderGeneticsPage",
        "drawStatBar"
      },
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private String aeroscapes$localizeLabel(String source) {
    return HusbandryLanguage.text(source);
  }

  @ModifyArg(
      method = {"renderAnimalDetails", "drawStatusLine"},
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/gui/Font;width(Ljava/lang/String;)I"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private String aeroscapes$localizedWidth(String source) {
    return HusbandryLanguage.text(source);
  }

  @ModifyArg(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private String aeroscapes$localizeEmptyRoster(String source) {
    return HusbandryLanguage.text(source);
  }

  @ModifyVariable(method = "renderParentRow", at = @At("HEAD"), argsOnly = true, ordinal = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private String aeroscapes$parentLabel(String label) {
    return HusbandryLanguage.text(label);
  }

  @ModifyArg(
      method = "renderParentRow",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/athisworkshop/animalhusbandry/client/gui/FarmLedgerScreen;drawScaledString(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/String;IIII)V"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private String aeroscapes$unknownParent(String source) {
    return source.endsWith(": Unknown")
        ? source.substring(0, source.length() - "Unknown".length())
            + HusbandryLanguage.text("Unknown")
        : source;
  }

  @ModifyReturnValue(method = "cleanStoredParentName", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private static String aeroscapes$unknownStoredParent(String original) {
    return original.equals("Unknown") ? HusbandryLanguage.text(original) : original;
  }

  @ModifyReturnValue(method = "formatWord", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private String aeroscapes$localizeAppearance(String original) {
    return HusbandryLanguage.text(original);
  }
}
