package dev.aeroscapes.mixin.technique;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.combat.MartialTechniques;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class MartialMeleeMixin {
  @WrapOperation(
      method = "attack",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$martialStrike(
      Entity target, DamageSource source, float damage, Operation<Boolean> original) {
    if ((Object) this instanceof ServerPlayer player && target instanceof LivingEntity living) {
      var technique = MartialTechniques.meleeTechnique(player, living);
      boolean dash = MartialTechniques.dashAttack(player);
      if (technique != null || dash) {
        float multiplier = technique == null ? 1.0F : MartialTechniques.meleeMultiplier(technique);
        if (dash) multiplier += 0.35F;
        boolean landed = original.call(target, source, damage * multiplier);
        if (landed) {
          if (technique != null) MartialTechniques.meleeLanded(player, living, technique, true);
          if (dash) MartialTechniques.dashLanded(player);
        }
        return landed;
      }
    }
    return original.call(target, source, damage);
  }
}
