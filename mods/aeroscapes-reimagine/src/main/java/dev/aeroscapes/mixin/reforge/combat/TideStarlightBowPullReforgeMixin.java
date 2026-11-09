package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.combat.RangedReforges;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.li64.tide.client.TideItemModelProperties", remap = false)
abstract class TideStarlightBowPullReforgeMixin {
  @ModifyReturnValue(method = "lambda$static$2", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static float aeroscapes$bowPull(float pull, @Local(argsOnly = true) ItemStack stack) {
    return pull / RangedReforges.bowDrawTime(stack);
  }
}
