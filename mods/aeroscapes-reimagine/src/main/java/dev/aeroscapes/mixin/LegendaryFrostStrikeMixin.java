package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.FrostReforge;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class LegendaryFrostStrikeMixin {
  @WrapOperation(
      method = "attack",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$applyFrost(
      Entity target,
      DamageSource source,
      float damage,
      Operation<Boolean> original,
      @Local ItemStack weapon,
      @Local(index = 6) float charge) {
    var player = (Player) (Object) this;
    boolean frostWeapon =
        !player.level().isClientSide() && charge > 0.9F && FrostReforge.matches(weapon);
    boolean hit = original.call(target, source, damage);
    if (hit
        && frostWeapon
        && target instanceof LivingEntity living
        && living.isAlive()
        && !living.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
        && player.getRandom().nextFloat() < 0.20F) {
      living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40), player);
    }
    return hit;
  }
}
