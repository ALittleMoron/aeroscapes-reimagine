package dev.aeroscapes.mixin.emi;

import cc.abbie.emi_ores.compat.emi.recipe.GeodeGenEmiRecipe;
import cc.abbie.emi_ores.compat.emi.recipe.OreGenEmiRecipe;
import dev.aeroscapes.atmosphere.OreRecipeLayout;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.WidgetHolder;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    value = {OreGenEmiRecipe.class, GeodeGenEmiRecipe.class},
    remap = false)
public abstract class EmiOresRecipeMixin {
  @Redirect(
      method = "addWidgets",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/emi/emi/api/widget/WidgetHolder;addText(Lnet/minecraft/network/chat/Component;IIIZ)Ldev/emi/emi/api/widget/TextWidget;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Replace the unbounded native row with a footer.
  private TextWidget aeroscapesReimagine$wrappedFrequency(
      WidgetHolder widgets, Component text, int x, int y, int color, boolean shadow) {
    return OreRecipeLayout.addFrequency(widgets, Minecraft.getInstance().font, text, color, shadow);
  }

  @Inject(method = "addWidgets", at = @At("TAIL"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Explain the limits beside each native distribution.
  private void aeroscapesReimagine$attempts(WidgetHolder widgets, CallbackInfo callback) {
    widgets.addText(Component.literal("?"), 146, 1, 0x606060, false);
    widgets.addTooltipText(
        List.of(
            Component.translatable("aeroscapes_reimagine.ore_information.attempts"),
            Component.translatable("aeroscapes_reimagine.ore_information.hosts"),
            Component.translatable("aeroscapes_reimagine.ore_information.scope")),
        142,
        0,
        18,
        14);
  }
}
