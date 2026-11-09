package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.combat.MeleeReforges;
import net.minecraft.world.damagesource.DamageSource;
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
    if (player.level().isClientSide()
        || weapon.isEmpty()
        || !(target instanceof LivingEntity living)
        || !living.isAlive()) {
      return original.call(target, source, damage);
    }
    boolean charged = charge > 0.9F;
    var strike = MeleeReforges.prepare(player, living, weapon, damage, charged);
    boolean hit = original.call(target, source, strike.damage());
    if (hit) {
      MeleeReforges.onHit(player, living, weapon, strike, charged);
    }
    return hit;
  }
}
