package dev.aeroscapes.mixin.fauna;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.fauna.FaunaHabitats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(
    targets = "io.github.bonsaistudi0s.crittersandcompanions.common.entity.SeaBunnyEntity",
    remap = false)
public abstract class CrittersSeaBunnyMixin {
  @ModifyExpressionValue(
      method = "checkSeaBunnySpawnRules",
      at =
          @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getSeaLevel()I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Preserve the native shallow-water band and water tests.
  private static int aeroscapes$localWaterline(
      int nativeSea,
      @Local(argsOnly = true) LevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    return FaunaHabitats.waterline(nativeSea, level, position);
  }
}
