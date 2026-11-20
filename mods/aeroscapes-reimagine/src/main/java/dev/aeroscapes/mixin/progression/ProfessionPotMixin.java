package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.races.ProfessionEffects;
import dev.aeroscapes.races.ProfessionPot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

@Mixin(value = CookingPotBlockEntity.class, remap = false)
abstract class ProfessionPotMixin {
  @ModifyExpressionValue(
      method = {"canCook", "processCooking"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lvectorwing/farmersdelight/common/crafting/CookingPotRecipe;assemble(Lnet/neoforged/neoforge/items/wrapper/RecipeWrapper;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ItemStack aeroscapes$preparedServing(ItemStack result) {
    return ProfessionEffects.prepare(
        result, ProfessionPot.traits((CookingPotBlockEntity) (Object) this));
  }

  @Inject(method = "processCooking", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$consumeIngredients(CallbackInfoReturnable<Boolean> callback) {
    if (callback.getReturnValueZ()) ProfessionPot.consumed((CookingPotBlockEntity) (Object) this);
  }

  @WrapOperation(
      method = {"canCook", "processCooking"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;isSameItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$compatibleMeals(
      ItemStack first, ItemStack second, Operation<Boolean> original) {
    return original.call(first, second)
        && ProfessionEffects.preparedTraits(first) == ProfessionEffects.preparedTraits(second);
  }

  @Inject(
      method = {"moveMealToOutput", "useStoredContainersOnMeal"},
      at = @At("HEAD"),
      cancellable = true,
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$separateServings(CallbackInfo callback) {
    CookingPotBlockEntity pot = (CookingPotBlockEntity) (Object) this;
    var output = pot.getInventory().getStackInSlot(CookingPotBlockEntity.OUTPUT_SLOT);
    if (!output.isEmpty()
        && ProfessionEffects.preparedTraits(output)
            != ProfessionEffects.preparedTraits(pot.getMeal())) callback.cancel();
  }
}
