package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.StoveBlock;
import vectorwing.farmersdelight.common.block.entity.StoveBlockEntity;

@Mixin(value = StoveBlock.class, remap = false)
abstract class ProfessionStovePlacementMixin {
  @WrapOperation(
      method = "useItemOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lvectorwing/farmersdelight/common/block/entity/StoveBlockEntity;addItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/crafting/RecipeHolder;I)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$placedFood(
      StoveBlockEntity stove,
      ItemStack stack,
      RecipeHolder<CampfireCookingRecipe> recipe,
      int slot,
      Operation<Boolean> original,
      @Local(argsOnly = true) Player actor) {
    boolean accepted = original.call(stove, stack, recipe, slot);
    if (accepted) ProfessionEffects.startCooking(stove, slot, actor, 1);
    return accepted;
  }
}
