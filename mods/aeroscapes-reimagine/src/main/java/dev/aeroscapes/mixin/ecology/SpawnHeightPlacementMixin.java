package dev.aeroscapes.mixin.ecology;

import dev.aeroscapes.aquatic.AquaticWater;
import dev.aeroscapes.worldgen.ExposedIslandSurfaces;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HeightmapPlacement.class)
public abstract class SpawnHeightPlacementMixin {
  @Shadow @Final private Heightmap.Types heightmap;

  @Inject(method = "getPositions", at = @At("RETURN"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Only Spawn's pinned placed features gain local layer selection.
  private void aeroscapes$localFeatureLayer(
      PlacementContext context,
      RandomSource random,
      BlockPos position,
      CallbackInfoReturnable<Stream<BlockPos>> callback) {
    if (context.topFeature().isEmpty()) return;
    var level = context.getLevel();
    var key =
        level
            .registryAccess()
            .registryOrThrow(Registries.PLACED_FEATURE)
            .getKey(context.topFeature().orElseThrow());
    if (key == null || !key.getNamespace().equals("spawn")) return;
    IslandTerrain terrain = IslandTerrain.from(level.getLevel().getChunkSource().randomState());
    if (terrain == null) return;
    int x = position.getX();
    int z = position.getZ();
    int top = level.getHeight(heightmap, x, z);
    if (heightmap == Heightmap.Types.OCEAN_FLOOR_WG || heightmap == Heightmap.Types.OCEAN_FLOOR) {
      callback.setReturnValue(Stream.of(position.atY(AquaticWater.floor(level, x, z, top))));
      return;
    }
    if (heightmap != Heightmap.Types.WORLD_SURFACE_WG) return;
    var cursor = new BlockPos.MutableBlockPos();
    var lower =
        ExposedIslandSurfaces.below(
            level.getMinBuildHeight(),
            top,
            y -> heightmap.isOpaque().test(level.getBlockState(cursor.set(x, y, z))),
            y ->
                ExposedIslandSurfaces.interpolated(
                        x,
                        y,
                        z,
                        (sampleX, sampleY, sampleZ) ->
                            terrain.biomeDensity(new SinglePointContext(sampleX, sampleY, sampleZ)))
                    > 0);
    callback.setReturnValue(
        Stream.concat(callback.getReturnValue(), lower.stream().map(position::atY)));
  }
}
