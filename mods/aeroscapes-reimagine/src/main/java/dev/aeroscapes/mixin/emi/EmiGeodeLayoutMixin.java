package dev.aeroscapes.mixin.emi;

import cc.abbie.emi_ores.compat.emi.recipe.AbstractPlacedFeatureEmiRecipe;
import cc.abbie.emi_ores.compat.emi.recipe.GeodeGenEmiRecipe;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.atmosphere.OreRecipeLayout;
import dev.emi.emi.EmiRenderHelper;
import dev.emi.emi.api.widget.SlotWidget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GeodeGenEmiRecipe.class, remap = false)
public abstract class EmiGeodeLayoutMixin extends AbstractPlacedFeatureEmiRecipe {
  @Shadow @Final private int rarityChance;

  @ModifyReturnValue(method = "getDisplayHeight", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Reserve the actual localized footer height.
  private int aeroscapesReimagine$frequencyFooter(int original) {
    return original
        + OreRecipeLayout.footerHeight(
            Minecraft.getInstance().font,
            getVeinFreqComponent(-1, -1, rarityChance),
            getDisplayWidth());
  }

  @ModifyExpressionValue(
      method = "addWidgets",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/emi/emi/api/widget/WidgetHolder;addSlot(Ldev/emi/emi/api/stack/EmiIngredient;II)Ldev/emi/emi/api/widget/SlotWidget;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Preserve chance data without checkerboard slots.
  private SlotWidget aeroscapesReimagine$ordinarySlotBackground(SlotWidget slot) {
    return slot.backgroundTexture(EmiRenderHelper.WIDGETS, 0, 0);
  }
}
