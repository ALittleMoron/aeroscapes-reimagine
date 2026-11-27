package dev.aeroscapes.mixin.technique;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
interface LivingDamageRecoveryAccess {
  @Accessor("lastHurt")
  float aeroscapes$lastHurt();

  @Accessor("lastHurt")
  void aeroscapes$lastHurt(float amount);
}
