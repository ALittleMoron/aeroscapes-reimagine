package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.races.ProfessionFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
abstract class ProfessionFurnaceMixin {
  @Inject(method = "setItem", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$inputReplaced(int slot, ItemStack stack, CallbackInfo callback) {
    if (slot == 0)
      ProfessionFurnace.replaceInput((AbstractFurnaceBlockEntity) (Object) this, stack);
  }

  @ModifyExpressionValue(
      method = {"canBurn", "burn"},
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/crafting/AbstractCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static ItemStack aeroscapes$prepareFood(
      ItemStack result, @Local(argsOnly = true) AbstractFurnaceBlockEntity furnace) {
    return ProfessionFurnace.prepare(furnace, result);
  }

  @Inject(method = "burn", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$consumePortion(
      CallbackInfoReturnable<Boolean> callback,
      @Local(argsOnly = true) AbstractFurnaceBlockEntity furnace) {
    if (callback.getReturnValueZ()) ProfessionFurnace.removed(furnace, 1);
  }
}
