package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.worldgen.BasinSeaLevel;
import net.minecraft.world.entity.monster.Drowned;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.Drowned$DrownedSwimUpGoal")
public abstract class BasinDrownedSwimUpMixin {
  @Shadow @Final private Drowned drowned;

  @ModifyExpressionValue(
      method = {"canUse", "tick"},
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/world/entity/monster/Drowned$DrownedSwimUpGoal;seaLevel:I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native goal reads its constructor-captured sea level.
  private int aeroscapesReimagine$basinSurface(int capturedSea) {
    return BasinSeaLevel.water(capturedSea, drowned.level(), drowned.blockPosition());
  }
}
