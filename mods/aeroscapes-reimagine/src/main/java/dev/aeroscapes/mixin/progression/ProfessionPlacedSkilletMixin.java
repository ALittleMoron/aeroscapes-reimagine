package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity;

@Mixin(value = SkilletBlockEntity.class, remap = false)
abstract class ProfessionPlacedSkilletMixin {
  @ModifyExpressionValue(
      method = "addItemToCook",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/items/ItemStackHandler;insertItem(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ItemStack aeroscapes$placedFood(
      ItemStack remainder, @Local(argsOnly = true) Player actor) {
    var pan = (SkilletBlockEntity) (Object) this;
    ProfessionEffects.startCooking(pan, 0, actor, pan.getStoredStack().getCount());
    return remainder;
  }

  @ModifyExpressionValue(
      method = "cookAndOutputItems",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/crafting/CampfireCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ItemStack aeroscapes$finishedFood(ItemStack result) {
    return ProfessionEffects.cookedServing((SkilletBlockEntity) (Object) this, 0, result);
  }
}
