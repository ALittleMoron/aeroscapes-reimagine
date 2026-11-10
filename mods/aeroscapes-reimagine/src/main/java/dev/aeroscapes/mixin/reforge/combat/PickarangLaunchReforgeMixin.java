package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.combat.InstalledWeaponReforges;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "org.violetmoon.quark.content.tools.item.PickarangItem", remap = false)
abstract class PickarangLaunchReforgeMixin {
  @ModifyArg(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/violetmoon/quark/content/tools/entity/rang/AbstractPickarang;shoot(Lnet/minecraft/world/entity/Entity;FFFFF)V"),
      index = 4)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$throwSpeed(float original, @Local(ordinal = 0) ItemStack weapon) {
    return InstalledWeaponReforges.pickarangLaunch(weapon, original);
  }
}
