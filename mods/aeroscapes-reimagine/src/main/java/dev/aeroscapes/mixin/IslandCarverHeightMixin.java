package dev.aeroscapes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.worldgen.IslandCarvingContext;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.CanyonWorldCarver;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.CaveWorldCarver;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({CaveWorldCarver.class, CanyonWorldCarver.class})
public abstract class IslandCarverHeightMixin {
  @WrapOperation(
      method = "carve",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/heightproviders/HeightProvider;sample(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;)I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Sample all solid layers instead of global heights.
  private int aeroscapes$islandHeight(
      HeightProvider provider,
      RandomSource random,
      WorldGenerationContext generation,
      Operation<Integer> original,
      @Local(argsOnly = true) CarvingContext context) {
    var layer = ((IslandCarvingContext) (Object) context).aeroscapes$layer();
    return layer == null ? original.call(provider, random, generation) : layer.height(random);
  }
}
