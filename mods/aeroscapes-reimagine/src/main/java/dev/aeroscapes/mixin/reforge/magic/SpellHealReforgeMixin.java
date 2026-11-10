package dev.aeroscapes.mixin.reforge.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.magic.MagicSpecialties;
import io.redspace.ironsspellbooks.entity.spells.HealingAoe;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = HealingAoe.class, remap = false)
abstract class SpellHealReforgeMixin {
  @WrapOperation(
      method = "applyEffect",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$recoveredAlly(
      LivingEntity target, float amount, Operation<Void> original) {
    float before = target.getHealth();
    original.call(target, amount);
    MagicSpecialties.healedAlly((HealingAoe) (Object) this, target, before);
  }
}
