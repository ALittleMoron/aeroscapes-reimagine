package dev.aeroscapes.mixin.ecology;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "aqario.fowlplay.common.entity.bird.FlyingBirdEntity", remap = false)
public interface FowlCliffFlightAccess {
  @Invoker("canStartFlying")
  boolean aeroscapes$cliffCanStartFlying();

  @Invoker("isFlying")
  boolean aeroscapes$cliffIsFlying();

  @Invoker("startFlying")
  void aeroscapes$cliffStartFlying();

  @Invoker("stopFlying")
  void aeroscapes$cliffStopFlying();
}
