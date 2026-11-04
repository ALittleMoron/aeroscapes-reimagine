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
    targets = {
      "io.github.bonsaistudi0s.crittersandcompanions.common.entity.DragonflyEntity",
      "io.github.bonsaistudi0s.crittersandcompanions.common.entity.LeafInsectEntity"
    },
    remap = false)
public abstract class CrittersSurfaceMixin {
  @ModifyExpressionValue(
      method = "*",
      at =
          @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getSeaLevel()I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Only spawn predicates in these exact classes read sea level.
  private static int aeroscapes$localSurface(
      int nativeSea,
      @Local(argsOnly = true) LevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    return FaunaHabitats.surface(nativeSea, level, position);
  }
}
