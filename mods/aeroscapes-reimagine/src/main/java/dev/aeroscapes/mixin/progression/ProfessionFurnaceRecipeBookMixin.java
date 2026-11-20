package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.aeroscapes.races.ProfessionFurnace;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeBookMenu.class)
abstract class ProfessionFurnaceRecipeBookMixin {
  @Inject(method = "handlePlacement", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$beforeRecipe(
      boolean all,
      RecipeHolder<?> recipe,
      ServerPlayer cook,
      CallbackInfo callback,
      @Share("furnaceInput") LocalRef<ProfessionFurnace> input) {
    if ((Object) this instanceof AbstractFurnaceMenu menu
        && menu.getSlot(0).container instanceof AbstractFurnaceBlockEntity furnace)
      input.set(new ProfessionFurnace(furnace));
  }

  @Inject(method = "handlePlacement", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$afterRecipe(
      boolean all,
      RecipeHolder<?> recipe,
      ServerPlayer cook,
      CallbackInfo callback,
      @Share("furnaceInput") LocalRef<ProfessionFurnace> input) {
    if (input.get() != null
        && (Object) this instanceof AbstractFurnaceMenu menu
        && menu.getSlot(0).container instanceof AbstractFurnaceBlockEntity furnace)
      input.get().afterClick(furnace, cook, false);
  }
}
