package dev.aeroscapes.mixin.reforge.engineering;

import com.jesz.createdieselgenerators.content.tools.ChemicalSprayerProjectileEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.engineering.DieselReforges;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.jesz.createdieselgenerators.content.tools.ChemicalSprayerItem", remap = false)
abstract class DieselSprayReforgeMixin {
  @WrapOperation(
      method = "onUseTick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/jesz/createdieselgenerators/content/tools/ChemicalSprayerProjectileEntity;shootFromRotation(Lnet/minecraft/world/entity/Entity;FFFFF)V"),
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$focusNativePulse(
      ChemicalSprayerProjectileEntity projectile,
      Entity user,
      float pitch,
      float yaw,
      float roll,
      float speed,
      float inaccuracy,
      Operation<Void> original,
      @Local(argsOnly = true) ItemStack stack,
      @Local(argsOnly = true) int count) {
    float focus = DieselReforges.sprayFocus(stack, 1000 - count);
    original.call(
        projectile,
        user,
        user.getXRot() + (pitch - user.getXRot()) * focus,
        user.getYRot() + (yaw - user.getYRot()) * focus,
        roll,
        speed,
        inaccuracy * focus);
  }
}
