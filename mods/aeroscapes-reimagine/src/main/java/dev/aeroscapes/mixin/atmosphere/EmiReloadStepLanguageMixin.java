package dev.aeroscapes.mixin.atmosphere;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "dev.emi.emi.runtime.EmiReloadManager", remap = false)
public abstract class EmiReloadStepLanguageMixin {
  @ModifyVariable(
      method = "step(Lnet/minecraft/network/chat/Component;J)V",
      at = @At("HEAD"),
      argsOnly = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the exact EMI reload hook.
  private static Component aeroscapes$translateJemiProgress(Component original) {
    if (!(original.getContents() instanceof PlainTextContents literal)) return original;
    String key =
        switch (literal.text()) {
          case "Waiting for JEI to finish..." -> "aeroscapes_reimagine.emi_reload.jei_wait";
          case "Loading information from JEI..." ->
              "aeroscapes_reimagine.emi_reload.jei_information";
          case "Processing JEI stacks..." -> "aeroscapes_reimagine.emi_reload.jei_stacks";
          case "Processing JEI subtypes..." -> "aeroscapes_reimagine.emi_reload.jei_subtypes";
          case "Processing JEI recipes..." -> "aeroscapes_reimagine.emi_reload.jei_recipes";
          case "Loading JEI data for " -> "aeroscapes_reimagine.emi_reload.jei_category";
          default -> null;
        };
    if (key == null) return original;
    MutableComponent translated =
        Component.translatableWithFallback(key, literal.text()).setStyle(original.getStyle());
    original.getSiblings().forEach(translated::append);
    return translated;
  }
}
