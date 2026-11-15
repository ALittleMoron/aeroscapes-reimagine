package dev.aeroscapes.mixin.husbandry;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.husbandry.GroomingReforges;
import net.minecraft.world.item.ItemStack;
import org.athisworkshop.animalhusbandry.item.BrushItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = BrushItem.class, remap = false)
abstract class HusbandryBrushMixin {
  @ModifyVariable(
      method = "interactLivingEntity",
      at = @At(value = "STORE", ordinal = 0),
      ordinal = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private int aeroscapes$groomingGain(int nativeGain, @Local(argsOnly = true) ItemStack brush) {
    return GroomingReforges.happiness(brush, nativeGain);
  }
}
