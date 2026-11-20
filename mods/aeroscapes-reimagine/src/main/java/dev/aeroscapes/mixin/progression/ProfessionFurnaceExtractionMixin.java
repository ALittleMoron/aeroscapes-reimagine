package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.races.ProfessionFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseContainerBlockEntity.class)
abstract class ProfessionFurnaceExtractionMixin {
  @Inject(
      method = {"removeItem", "removeItemNoUpdate"},
      at = @At("RETURN"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$extractInput(
      CallbackInfoReturnable<ItemStack> callback, @Local(argsOnly = true, ordinal = 0) int slot) {
    if (slot == 0 && (Object) this instanceof AbstractFurnaceBlockEntity furnace)
      ProfessionFurnace.removed(furnace, callback.getReturnValue().getCount());
  }
}
