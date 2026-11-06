package dev.aeroscapes.mixin.construction;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.construction.LittleSableGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import team.creative.littletiles.common.block.mc.BlockTile;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.physics.mass.MassTracker", remap = false)
public abstract class LittleSableCenterContextMixin {
  @ModifyExpressionValue(
      method =
          "build(Lnet/minecraft/world/level/BlockGetter;Ldev/ryanhcode/sable/companion/math/BoundingBox3ic;)Ldev/ryanhcode/sable/api/physics/mass/MassTracker;",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ljava/util/function/BiFunction;apply(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
      require = 2)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Both native mass and inertia passes need this BE's center.
  private static Object aeroscapes$centerAtTile(
      Object original,
      @Local(argsOnly = true) BlockGetter level,
      @Local BlockPos.MutableBlockPos position,
      @Local BlockState state) {
    return state.getBlock() instanceof BlockTile
        ? LittleSableGeometry.center(level, position)
        : original;
  }
}
