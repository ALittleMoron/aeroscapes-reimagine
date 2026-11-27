package dev.aeroscapes.mixin.magic;

import dev.aeroscapes.magic.SpellSpecializations;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.spells.ice.FrostwaveSpell;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FrostwaveSpell.class, remap = false)
abstract class FrostwaveTalentMixin {
  @Inject(method = "onCast", at = @At("TAIL"), require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$extinguish(
      Level level,
      int rank,
      LivingEntity caster,
      CastSource source,
      MagicData data,
      CallbackInfo callback) {
    if (caster instanceof ServerPlayer player
        && SpellSpecializations.learned(player, "magic_frostwave_extinguish")) {
      float radius = ((FrostwaveSpell) (Object) this).getRadius(rank, caster);
      SpellSpecializations.frostwave(player, radius);
    }
  }
}
