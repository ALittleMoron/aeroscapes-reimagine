package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.redspace.ironsspellbooks.damage.ISSDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
abstract class SpellFireLootMixin {
  @WrapMethod(method = "dropAllDeathLoot")
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$fireLoot(
      ServerLevel level, DamageSource source, Operation<Void> original) {
    LivingEntity victim = (LivingEntity) (Object) this;
    boolean fire = source.is(ISSDamageTypes.FIRE_MAGIC) || source.is(ISSDamageTypes.FIRE_FIELD);
    int previousFire = victim.getRemainingFireTicks();
    // Some native fire effects ignite only after hurt returns, after lethal loot is evaluated.
    if (fire) victim.setRemainingFireTicks(Math.max(previousFire, 1));
    try {
      original.call(level, source);
    } finally {
      if (fire) victim.setRemainingFireTicks(previousFire);
    }
  }
}
