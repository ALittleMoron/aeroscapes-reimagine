package dev.aeroscapes.mixin.preflight;

import dev.aeroscapes.preflight.BalloonLeakAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
    targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon",
    remap = false)
public interface BalloonLeakMixin extends BalloonLeakAccess {
  @Override
  @Accessor("leaking")
  boolean aeroscapes$isLeaking();
}
