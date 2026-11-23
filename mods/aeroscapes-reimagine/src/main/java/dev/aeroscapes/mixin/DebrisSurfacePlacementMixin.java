package dev.aeroscapes.mixin;

import dev.aeroscapes.aquatic.AquaticWater;
import dev.aeroscapes.worldgen.AirborneExpeditionStructure;
import dev.aeroscapes.worldgen.ExposedIslandSurfaces;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
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
public abstract class DebrisSurfacePlacementMixin {
  @Shadow @Final private Heightmap.Types heightmap;

  @Inject(method = "getPositions", at = @At("RETURN"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The native heightmap only returns the top layer.
  private void aeroscapes$lowerIslandSurfaces(
      PlacementContext context,
      RandomSource random,
      BlockPos position,
      CallbackInfoReturnable<Stream<BlockPos>> callback) {
    if (heightmap != Heightmap.Types.WORLD_SURFACE_WG || context.topFeature().isEmpty()) return;
    var level = context.getLevel();
    var key =
        level
            .registryAccess()
            .registryOrThrow(Registries.PLACED_FEATURE)
            .getKey(context.topFeature().orElseThrow());
    if (key == null || !key.getNamespace().equals("rocks")) return;
    var server = level.getLevel();
    IslandTerrain terrain = IslandTerrain.from(server.getChunkSource().randomState());
    if (terrain == null) return;
    int x = position.getX();
    int z = position.getZ();
    int top = level.getHeight(heightmap, x, z);
    if (key.getPath().equals("underwater_starfish")
        || key.getPath().equals("underwater_seashell")) {
      callback.setReturnValue(Stream.of(position.atY(AquaticWater.floor(level, x, z, top))));
      return;
    }
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
    var surfaces = Stream.concat(callback.getReturnValue(), lower.stream().map(position::atY));
    if (level instanceof WorldGenRegion region) {
      var airframes =
          AirborneExpeditionStructure.airframes(
              server.structureManager().forWorldGenRegion(region), new ChunkPos(position));
      if (!airframes.isEmpty()) {
        surfaces =
            surfaces.filter(
                surface -> {
                  var support = surface.below();
                  return airframes.stream().noneMatch(bounds -> bounds.isInside(support));
                });
      }
    }
    callback.setReturnValue(surfaces);
  }
}
