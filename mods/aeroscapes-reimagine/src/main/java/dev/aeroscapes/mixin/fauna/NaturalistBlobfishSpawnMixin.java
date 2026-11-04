package dev.aeroscapes.mixin.fauna;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.fauna.FaunaHabitats;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.crispytwig.naturalist.world.entity.animal.fish.Blobfish", remap = false)
public abstract class NaturalistBlobfishSpawnMixin {
  @ModifyExpressionValue(
      method = "checkBlobfishSpawnRules",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getSeaLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Keep the native 33-block deep-water threshold.
  private static int aeroscapes$localWaterline(
      int nativeSea,
      @Local(argsOnly = true) ServerLevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    return FaunaHabitats.waterline(nativeSea, level, position);
  }
}
