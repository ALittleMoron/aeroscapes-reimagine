package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.equipment.combat.RangedReforges;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.mehvahdjukaar.supplementaries.common.items.SlingshotItem", remap = false)
abstract class SlingshotChargeReforgeMixin {
  @ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static int aeroscapes$chargeDuration(int original, ItemStack stack, LivingEntity user) {
    return RangedReforges.slingshotCharge(stack, original);
  }
}
