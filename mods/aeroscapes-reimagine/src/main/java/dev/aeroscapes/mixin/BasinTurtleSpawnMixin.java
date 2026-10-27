package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.BasinSeaLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Turtle.class)
public abstract class BasinTurtleSpawnMixin {
  @ModifyExpressionValue(
      method = "checkTurtleSpawnRules",
      at =
          @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelAccessor;getSeaLevel()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Pinned native turtle coastal-height predicate.
  private static int aeroscapesReimagine$basinShore(
      int nativeSea,
      @Local(argsOnly = true) LevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    return BasinSeaLevel.shore(nativeSea, level, position);
  }
}
