package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.IslandCarvingContext;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.CaveWorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CaveWorldCarver.class)
public abstract class IslandCaveStartMixin {
  @ModifyExpressionValue(
      method = "carve",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/util/RandomSource;nextInt(I)I",
              ordinal = 2))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // An island has few source chunks to supply a cave.
  private int aeroscapes$nonemptyIslandStart(
      int count, @Local(argsOnly = true) CarvingContext context) {
    return ((IslandCarvingContext) (Object) context).aeroscapes$layer() == null
        ? count
        : Math.max(1, count);
  }
}
