package dev.aeroscapes.mixin.fauna;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
    targets = "com.crispytwig.naturalist.world.entity.animal.bird.Bird$MountOnOwnersHeadGoal",
    remap = false)
public abstract class NaturalistHeadMountMixin {
  @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Reject the complete head-perching mechanic, not only its
  // movement bonus.
  private void aeroscapes$keepBirdOffHead(CallbackInfoReturnable<Boolean> callback) {
    callback.setReturnValue(false);
  }
}
