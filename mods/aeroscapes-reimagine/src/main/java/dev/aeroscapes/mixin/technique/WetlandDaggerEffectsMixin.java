package dev.aeroscapes.mixin.technique;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "uwu.juni.wetland_whimsy.content.items.DaggerItem", remap = false)
abstract class WetlandDaggerEffectsMixin extends Item {
  protected WetlandDaggerEffectsMixin(Properties properties) {
    super(properties);
  }

  @Inject(
      method = "hurtEnemy",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/Minecraft;getInstance()Lnet/minecraft/client/Minecraft;"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$serverParryParticles(
      ItemStack stack,
      LivingEntity target,
      LivingEntity attacker,
      CallbackInfoReturnable<Boolean> callback) {
    if (attacker.level() instanceof ServerLevel level) {
      level.sendParticles(
          ParticleTypes.CRIT,
          attacker.getX(),
          attacker.getY(0.55),
          attacker.getZ(),
          8,
          0.2,
          0.25,
          0.2,
          0.15);
      callback.setReturnValue(super.hurtEnemy(stack, target, attacker));
    }
  }
}
