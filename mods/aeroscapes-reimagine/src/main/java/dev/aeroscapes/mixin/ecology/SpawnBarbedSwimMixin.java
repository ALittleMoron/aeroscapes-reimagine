package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.ninni.spawn.server.entity.mob.fictional.Barbed;
import dev.aeroscapes.ecology.SpawnWaterline;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    targets = "com.ninni.spawn.server.entity.mob.fictional.Barbed$BarbedSwimUpGoal",
    remap = false)
public abstract class SpawnBarbedSwimMixin {
  @Shadow @Final private Barbed barbed;

  @ModifyExpressionValue(
      method = {"canUse", "tick"},
      at =
          @At(
              value = "FIELD",
              target =
                  "Lcom/ninni/spawn/server/entity/mob/fictional/Barbed$BarbedSwimUpGoal;seaLevel:I"),
      require = 3)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The provider otherwise captures global sea level at entity
  // construction.
  private int aeroscapes$localSwimTarget(int original) {
    return SpawnWaterline.at(barbed.level(), barbed.blockPosition());
  }
}
