package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.StoveBlockEntity;

@Mixin(value = StoveBlockEntity.class, remap = false)
abstract class ProfessionStoveCookingMixin {
  @Inject(method = "addItem", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$resetSource(
      ItemStack input,
      RecipeHolder<CampfireCookingRecipe> recipe,
      int slot,
      CallbackInfoReturnable<Boolean> callback) {
    if (callback.getReturnValueZ())
      ProfessionEffects.cookingSlotChanged((StoveBlockEntity) (Object) this, slot, 0);
  }

  @ModifyExpressionValue(
      method = "cookAndOutputItems",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/crafting/CampfireCookingRecipe;getResultItem(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ItemStack aeroscapes$finishedFood(ItemStack result, @Local(index = 2) int slot) {
    var stove = (StoveBlockEntity) (Object) this;
    var meal = ProfessionEffects.cookedServing(stove, slot, result);
    ProfessionEffects.cookingSlotChanged(stove, slot, 0);
    return meal;
  }
}
