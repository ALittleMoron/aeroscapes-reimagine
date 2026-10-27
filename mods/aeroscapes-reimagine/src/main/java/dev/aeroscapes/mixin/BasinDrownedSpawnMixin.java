package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.BasinSeaLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Drowned.class)
public abstract class BasinDrownedSpawnMixin {
  @ModifyExpressionValue(
      method = "isDeepEnoughToSpawn",
      at =
          @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getSeaLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Pinned native ocean-drowned depth predicate.
  private static int aeroscapesReimagine$basinDepth(
      int nativeSea,
      @Local(argsOnly = true) LevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    return BasinSeaLevel.water(nativeSea, level, position);
  }
}
