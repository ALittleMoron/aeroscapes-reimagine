package dev.aeroscapes.mixin.magic;

import dev.aeroscapes.magic.BindingPact;
import io.redspace.ironsspellbooks.entity.spells.AbstractMagicProjectile;
import io.redspace.ironsspellbooks.entity.spells.ender_chain.ArcaneShackleProjectile;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ArcaneShackleProjectile.class, remap = false)
abstract class ArcaneShackleTalentMixin extends AbstractMagicProjectile {
  @Unique private boolean aeroscapes$foundImmediateTarget;

  protected ArcaneShackleTalentMixin(EntityType<? extends Projectile> type, Level level) {
    super(type, level);
  }

  @Shadow
  private void spawnChain(LivingEntity target, Vec3 point) {
    throw new AssertionError();
  }

  @Inject(method = "spawnChain", at = @At("HEAD"), require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$rememberImmediateTarget(
      LivingEntity target, Vec3 point, CallbackInfo callback) {
    aeroscapes$foundImmediateTarget = true;
  }

  @Inject(
      method = "onHitBlock",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/entity/spells/ender_chain/ArcaneShackleProjectile;spawnChainsFromBlock(Lnet/minecraft/world/phys/Vec3;)V",
              shift = At.Shift.AFTER),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$placeMarker(BlockHitResult hit, CallbackInfo callback) {
    if (!aeroscapes$foundImmediateTarget && getOwner() instanceof ServerPlayer player) {
      BindingPact.markShackle(
          player, hit, this::aeroscapes$canHitMarkerTarget, this::aeroscapes$triggerMarker);
    }
  }

  @Unique
  private boolean aeroscapes$canHitMarkerTarget(LivingEntity target) {
    return canHitEntity(target);
  }

  @Unique
  private void aeroscapes$triggerMarker(LivingEntity target, Vec3 point) {
    // The native tick advances the discarded projectile after processing its impact.
    setPos(point);
    spawnChain(target, point);
  }
}
