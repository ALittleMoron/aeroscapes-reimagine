package dev.aeroscapes.mixin.sky;

import dev.aeroscapes.sky.WhaleLifecycle;
import dev.aeroscapes.sky.WhalePodCalls;
import dev.dacite.skywhales.entity.SkyWhale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SkyWhale.class, remap = false)
public abstract class WhaleLifecycleMixin extends PathfinderMob {
  @Unique private final WhalePodCalls aeroscapes$podCalls = new WhalePodCalls();

  protected WhaleLifecycleMixin(EntityType<? extends PathfinderMob> type, Level level) {
    super(type, level);
  }

  @Override
  public void playAmbientSound() {
    super.playAmbientSound();
    if (level() instanceof ServerLevel server) {
      aeroscapes$podCalls.afterAmbientCall((SkyWhale) (Object) this, server);
    }
  }

  @Inject(method = "tick", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Runs after the native whale tick.
  private void aeroscapes$answerPodCall(CallbackInfo callback) {
    if (level() instanceof ServerLevel server) {
      aeroscapes$podCalls.tick((SkyWhale) (Object) this, server);
    }
  }

  @Inject(method = "checkDespawn", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native code discards the encounter immediately.
  private void aeroscapes$distantGrace(CallbackInfo callback) {
    WhaleLifecycle.checkDespawn((SkyWhale) (Object) this);
    callback.cancel();
  }
}
