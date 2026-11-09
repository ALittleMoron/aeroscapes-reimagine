package dev.aeroscapes.mixin.reforge.combat;

import dev.aeroscapes.equipment.combat.RangedReforges;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.herobrot.tieredneo.events.TieredGameplayEvents", remap = false)
abstract class TieredRangedReforgeMixin {
  @Inject(method = "onProjectileJoin", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$skipDuplicateArrowStats(
      EntityJoinLevelEvent event, CallbackInfo ci) {
    if (event.getEntity() instanceof AbstractArrow arrow
        && (event.loadedFromDisk() || RangedReforges.handled(arrow))) {
      ci.cancel();
    }
  }

  @Inject(method = "onTridentIncomingDamage", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$skipPlayerHandStats(
      LivingIncomingDamageEvent event, CallbackInfo ci) {
    if (event.getSource().getDirectEntity() instanceof ThrownTrident trident
        && trident.getOwner() instanceof ServerPlayer) {
      ci.cancel();
    }
  }
}
