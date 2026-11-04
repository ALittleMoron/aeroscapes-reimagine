package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.ecology.BirdHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "aqario.fowlplay.common.worldgen.SpawnPredicates", remap = false)
public abstract class FowlSkyMixin {
  @ModifyReturnValue(method = "hasSkyAccess", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Exterior lower islands retain each species' native substrate
  // predicate.
  private static boolean aeroscapes$lowerExterior(
      boolean original, LevelAccessor level, BlockPos position) {
    return original || BirdHabitat.exterior(level, position);
  }

  @WrapOperation(
      method = "isMidairSpawn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/LevelAccessor;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Preserve the native 32-block takeoff distance over the local
  // surface.
  private static int aeroscapes$localFlightSurface(
      LevelAccessor level,
      Heightmap.Types type,
      int x,
      int z,
      Operation<Integer> original,
      @Local(argsOnly = true) BlockPos position) {
    return BirdHabitat.localSurface(level, position, original.call(level, type, x, z));
  }
}
