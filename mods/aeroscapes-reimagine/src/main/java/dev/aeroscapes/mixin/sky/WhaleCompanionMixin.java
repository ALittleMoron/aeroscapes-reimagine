package dev.aeroscapes.mixin.sky;

import dev.aeroscapes.sky.WhaleCompanionGoal;
import dev.dacite.skywhales.entity.SkyWhale;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ranks pod and ship companionship above native drifting and below native combat. */
@Mixin(value = SkyWhale.class, remap = false)
public abstract class WhaleCompanionMixin extends PathfinderMob {
  protected WhaleCompanionMixin(EntityType<? extends PathfinderMob> type, Level level) {
    super(type, level);
  }

  @Override
  protected float getFlyingSpeed() {
    double command = getMoveControl().getSpeedModifier();
    if (getTarget() != null || command <= 0 || command > 1.2) return super.getFlyingSpeed();
    // Drift issues 1.0, pod follow 1.2. Native emergency climb and combat keep their own thrust.
    return (float) (0.040 + 0.006 * Math.sin((tickCount + getId() * 17.0) / 54));
  }

  @Inject(method = "registerGoals", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the native mob constructor.
  private void aeroscapes$companionship(CallbackInfo callback) {
    goalSelector.addGoal(2, new WhaleCompanionGoal((SkyWhale) (Object) this));
  }
}
