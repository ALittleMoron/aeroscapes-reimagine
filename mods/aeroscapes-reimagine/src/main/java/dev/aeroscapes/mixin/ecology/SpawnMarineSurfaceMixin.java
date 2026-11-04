package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.worldgen.IslandTerrain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(
    targets =
        "net.minecraft.world.level.levelgen.SurfaceRules$Context$AbovePreliminarySurfaceCondition")
public abstract class SpawnMarineSurfaceMixin {
  @WrapOperation(
      method = "test",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;getMinSurfaceLevel()I"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native preliminary density omits deep basin floors.
  private int aeroscapes$basinFloor(@Coerce Object context, Operation<Integer> original) {
    int cutoff = original.call(context);
    var surface = (SpawnSurfaceContextAccess) context;
    int y = surface.aeroscapes$y();
    int depth = surface.aeroscapes$stoneDepthAbove();
    if (y >= cutoff || depth < 1 || depth > 9) return cutoff;
    var terrain = IslandTerrain.from(surface.aeroscapes$random());
    if (terrain == null) return cutoff;
    int x = surface.aeroscapes$x();
    int z = surface.aeroscapes$z();
    var basin = terrain.basin(x, z);
    int floor = y + depth - 1;
    if (basin == null
        || !basin.water(x, basin.waterline(), z)
        || surface.aeroscapes$waterHeight() != basin.waterline() + 1
        || floor < basin.floor(x, z) - 12
        || floor >= basin.waterline()) return cutoff;
    String biome = surface.aeroscapes$biome().get().unwrapKey().orElseThrow().location().toString();
    return biome.equals("spawn:seagrass_meadow")
            || biome.equals("spawn:deep_warm_ocean") && depth == 1
        ? y
        : cutoff;
  }
}
