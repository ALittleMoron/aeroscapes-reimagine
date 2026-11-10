package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.ninni.spawn.server.entity.misc.CrabBubble;
import com.ninni.spawn.server.item.CoastalCrabClawItem;
import dev.aeroscapes.equipment.combat.InstalledWeaponReforges;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(CoastalCrabClawItem.class)
abstract class CrabClawReforgeMixin {
  @ModifyConstant(method = "releaseUsing", constant = @Constant(intValue = 20, ordinal = 0))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$bubbleWarmup(int original, ItemStack stack) {
    return InstalledWeaponReforges.clawWarmup(stack, original);
  }

  @WrapOperation(
      method = "createBubble",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/ninni/spawn/server/entity/misc/CrabBubble;shootFromRotation(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$bubbleAim(
      CrabBubble bubble,
      Entity shooter,
      float pitch,
      float yaw,
      float roll,
      float speed,
      float spread,
      Operation<Void> original) {
    var user = (LivingEntity) shooter;
    original.call(
        bubble,
        shooter,
        pitch,
        yaw,
        roll,
        InstalledWeaponReforges.clawSpeed(user, speed),
        InstalledWeaponReforges.clawSpread(user, spread));
  }
}
