package dev.aeroscapes.mixin.emi;

import cc.abbie.emi_ores.compat.emi.recipe.AbstractPlacedFeatureEmiRecipe;
import cc.abbie.emi_ores.compat.emi.recipe.OreGenEmiRecipe;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.atmosphere.OreRecipeLayout;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = OreGenEmiRecipe.class, remap = false)
public abstract class EmiOreLayoutMixin extends AbstractPlacedFeatureEmiRecipe {
  @Shadow @Final private int countMin;
  @Shadow @Final private int countMax;
  @Shadow @Final private int rarityChance;

  @ModifyReturnValue(method = "getDisplayHeight", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Reserve the actual localized footer height.
  private int aeroscapesReimagine$frequencyFooter(int original) {
    return original
        + OreRecipeLayout.footerHeight(
            Minecraft.getInstance().font,
            getVeinFreqComponent(countMin, countMax, rarityChance),
            getDisplayWidth());
  }
}
