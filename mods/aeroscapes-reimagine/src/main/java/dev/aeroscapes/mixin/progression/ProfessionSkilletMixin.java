package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.item.SkilletItem;

@Mixin(value = SkilletItem.class, remap = false)
abstract class ProfessionSkilletMixin {
  @ModifyExpressionValue(
      method = "lambda$finishUsingItem$0",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/crafting/CampfireCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static ItemStack aeroscapes$manualCooking(
      ItemStack result, @Local(argsOnly = true) Player cook) {
    return ProfessionEffects.prepare(result, ProfessionEffects.mealTraits(cook));
  }
}
