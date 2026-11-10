package dev.aeroscapes.mixin.reforge.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.magic.MagicSpecialties;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.entity.spells.HealingAoe;
import io.redspace.ironsspellbooks.spells.holy.HealingCircleSpell;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = HealingCircleSpell.class, remap = false)
abstract class HealingCircleReforgeMixin {
  @WrapOperation(
      method = "onCast",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$paidHealingCircle(
      Level level,
      Entity entity,
      Operation<Boolean> original,
      @Local(argsOnly = true) CastSource source) {
    boolean added = original.call(level, entity);
    if (added
        && entity instanceof HealingAoe area
        && area.getOwner() instanceof ServerPlayer caster) {
      MagicSpecialties.rememberHealingCircle(area, caster, source);
    }
    return added;
  }
}
