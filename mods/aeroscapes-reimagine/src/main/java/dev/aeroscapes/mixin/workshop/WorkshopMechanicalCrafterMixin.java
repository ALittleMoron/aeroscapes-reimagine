package dev.aeroscapes.mixin.workshop;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.equipment.workshop.WorkshopQuality;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.crafter.RecipeGridHandler", remap = false)
abstract class WorkshopMechanicalCrafterMixin {
  @ModifyExpressionValue(
      method = "lambda$tryToApplyRecipe$1",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static ItemStack aeroscapes$dropWorkmanship(ItemStack result) {
    return WorkshopQuality.finishAutomatic(result);
  }
}
