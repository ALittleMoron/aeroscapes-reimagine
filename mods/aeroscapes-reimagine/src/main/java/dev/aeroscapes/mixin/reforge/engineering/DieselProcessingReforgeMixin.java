package dev.aeroscapes.mixin.reforge.engineering;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.engineering.DieselReforges;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(
    targets = {
      "com.jesz.createdieselgenerators.content.tools.hammer.HammerItem",
      "com.jesz.createdieselgenerators.content.tools.wire_cutters.WireCuttersItem"
    },
    remap = false)
abstract class DieselProcessingReforgeMixin {
  @ModifyReturnValue(method = "getUseDuration", at = @At("RETURN"), allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$manualProcessingTime(
      int duration, @Local(argsOnly = true) ItemStack stack) {
    return DieselReforges.processingDuration(stack, duration);
  }

  @ModifyArg(
      method = "finishUsingItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"),
      index = 0,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$completedManualWear(
      int wear,
      @Local(argsOnly = true) ItemStack stack,
      @Local(argsOnly = true) LivingEntity user) {
    return DieselReforges.processingWear(stack, user.getRandom(), wear);
  }
}
