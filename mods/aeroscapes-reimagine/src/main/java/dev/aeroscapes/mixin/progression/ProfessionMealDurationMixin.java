package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
abstract class ProfessionMealDurationMixin {
  @ModifyReturnValue(method = "getUseDuration", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$eatingTime(int ticks) {
    return ProfessionEffects.eatingTime((ItemStack) (Object) this, ticks);
  }
}
