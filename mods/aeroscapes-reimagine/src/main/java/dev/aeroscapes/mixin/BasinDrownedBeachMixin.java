package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.worldgen.BasinSeaLevel;
import net.minecraft.world.entity.monster.Drowned;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.entity.monster.Drowned$DrownedGoToBeachGoal")
public abstract class BasinDrownedBeachMixin {
  @Shadow @Final private Drowned drowned;

  @ModifyExpressionValue(
      method = "canUse",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getSeaLevel()I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native beach goal must start near the local surface.
  private int aeroscapesReimagine$basinShore(int nativeSea) {
    return BasinSeaLevel.water(nativeSea, drowned.level(), drowned.blockPosition());
  }
}
