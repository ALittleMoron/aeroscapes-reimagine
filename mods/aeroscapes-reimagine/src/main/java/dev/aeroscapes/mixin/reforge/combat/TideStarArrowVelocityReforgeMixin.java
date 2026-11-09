package dev.aeroscapes.mixin.reforge.combat;

import com.li64.tide.registries.entities.misc.StarArrow;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.equipment.combat.RangedReforges;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = StarArrow.class, remap = false)
abstract class TideStarArrowVelocityReforgeMixin extends AbstractArrow
    implements RangedReforges.FixedSpeedArrow {
  @Unique
  private static final EntityDataAccessor<Float> AEROSCAPES_SPEED =
      SynchedEntityData.defineId(StarArrow.class, EntityDataSerializers.FLOAT);

  protected TideStarArrowVelocityReforgeMixin(
      EntityType<? extends AbstractArrow> type, Level level) {
    super(type, level);
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(AEROSCAPES_SPEED, 1.0F);
  }

  @Override
  public void aeroscapes$setSpeedMultiplier(float multiplier) {
    getEntityData().set(AEROSCAPES_SPEED, multiplier);
  }

  @ModifyReturnValue(method = "getDeltaMovement", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private Vec3 aeroscapes$projectileSpeed(Vec3 movement) {
    return movement.scale(getEntityData().get(AEROSCAPES_SPEED));
  }
}
