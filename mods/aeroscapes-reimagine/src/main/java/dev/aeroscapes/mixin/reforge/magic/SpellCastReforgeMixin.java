package dev.aeroscapes.mixin.reforge.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.magic.MagicSpecialties;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AbstractSpell.class, remap = false)
abstract class SpellCastReforgeMixin {
  @WrapOperation(
      method = "attemptInitiateCast",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getEffectiveCastTime(ILnet/minecraft/world/entity/LivingEntity;)I"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$castDuration(
      AbstractSpell spell,
      int level,
      LivingEntity caster,
      Operation<Integer> original,
      @Local(argsOnly = true) CastSource source) {
    int duration = original.call(spell, level, caster);
    return caster instanceof ServerPlayer player
        ? MagicSpecialties.castDuration(spell, level, player, source, duration)
        : duration;
  }
}
