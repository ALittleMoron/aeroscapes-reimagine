package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.magic.SpellSpecializations;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.SummonedSkeleton;
import io.redspace.ironsspellbooks.entity.mobs.SummonedZombie;
import io.redspace.ironsspellbooks.entity.spells.blood_needle.BloodNeedle;
import java.util.Comparator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DamageSources.class, remap = false)
abstract class BloodNeedlesTalentMixin {
  @Unique private static final double AEROSCAPES$SUMMON_RANGE = 8;

  @WrapOperation(
      method = "postHitEffects",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$redirectOverheal(
      LivingEntity caster, float amount, Operation<Void> original, LivingDamageEvent.Post event) {
    if (!(caster instanceof ServerPlayer player)
        || !(event.getSource() instanceof SpellDamageSource source)
        || !(source.getDirectEntity() instanceof BloodNeedle)
        || source.spell() != SpellRegistry.BLOOD_NEEDLES_SPELL.get()
        || !SpellSpecializations.learned(player, "magic_blood_needles_overheal")) {
      original.call(caster, amount);
      return;
    }

    // Use NeoForge's native heal adjustment before measuring the unused part of the lifesteal.
    float adjusted = EventHooks.onLivingHeal(player, amount);
    float before = player.getHealth();
    if (!(adjusted > 0) || !(before > 0)) return;
    float missing = player.getMaxHealth() - before;
    player.setHealth(before + adjusted);
    float excess = Math.min(adjusted, amount) - missing;
    if (!(excess > 0)) return;

    player
        .serverLevel()
        .getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(AEROSCAPES$SUMMON_RANGE),
            target ->
                (target instanceof SummonedSkeleton || target instanceof SummonedZombie)
                    && ((IMagicSummon) target).getSummoner() == player
                    && target.isAlive()
                    && target.getHealth() < target.getMaxHealth()
                    && target.distanceToSqr(player)
                        <= AEROSCAPES$SUMMON_RANGE * AEROSCAPES$SUMMON_RANGE)
        .stream()
        .min(Comparator.comparingDouble(player::distanceToSqr))
        .ifPresent(
            target -> {
              float healing = Math.min(excess, EventHooks.onLivingHeal(target, excess));
              if (healing > 0 && target.isAlive()) target.setHealth(target.getHealth() + healing);
            });
  }
}
