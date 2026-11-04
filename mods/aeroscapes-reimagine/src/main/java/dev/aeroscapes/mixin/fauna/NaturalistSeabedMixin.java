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
@Mixin(
    targets = {
      "com.crispytwig.naturalist.world.entity.animal.clam.Clam",
      "com.crispytwig.naturalist.world.entity.animal.starfish.Starfish",
      "com.crispytwig.naturalist.world.entity.animal.giantisopod.GiantIsopod",
      "com.crispytwig.naturalist.world.entity.animal.fish.Anglerfish"
    },
    remap = false)
public abstract class NaturalistSeabedMixin {
  @ModifyExpressionValue(
      method = "*",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getSeaLevel()I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native water and sturdy seabed predicates remain intact.
  private static int aeroscapes$localWaterline(
      int nativeSea,
      @Local(argsOnly = true) ServerLevelAccessor level,
      @Local(argsOnly = true) BlockPos position) {
    return FaunaHabitats.waterline(nativeSea, level, position);
  }
}
