package dev.aeroscapes.mixin.workshop;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.equipment.workshop.WorkshopQuality;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CrafterBlock.class)
abstract class WorkshopCrafterMixin {
  @ModifyExpressionValue(
      method = "dispenseFrom",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/crafting/CraftingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ItemStack aeroscapes$dropWorkmanship(ItemStack result) {
    return WorkshopQuality.finishAutomatic(result);
  }
}
