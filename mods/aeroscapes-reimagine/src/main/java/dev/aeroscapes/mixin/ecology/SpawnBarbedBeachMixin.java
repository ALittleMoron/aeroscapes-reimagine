package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ninni.spawn.server.entity.mob.fictional.Barbed;
import dev.aeroscapes.ecology.SpawnWaterline;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    targets = "com.ninni.spawn.server.entity.mob.fictional.Barbed$BarbedGoToBeachGoal",
    remap = false)
public abstract class SpawnBarbedBeachMixin {
  @Shadow @Final private Barbed barbed;

  @ModifyExpressionValue(
      method = "canUse",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getSeaLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Resolve the animal's current basin after movement.
  private int aeroscapes$localBeach(int original) {
    return SpawnWaterline.at(barbed.level(), barbed.blockPosition());
  }
}
