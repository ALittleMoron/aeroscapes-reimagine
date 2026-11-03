package dev.aeroscapes.mixin.aquatic;

import dev.aeroscapes.aquatic.AquaticWater;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
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
public abstract class AquaticFloorPlacementMixin {
  @Shadow @Final private Heightmap.Types heightmap;

  @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native global heightmaps see an upper island first.
  private void aeroscapes$basinFloor(
      PlacementContext context,
      RandomSource random,
      BlockPos position,
      CallbackInfoReturnable<Stream<BlockPos>> callback) {
    if (heightmap != Heightmap.Types.OCEAN_FLOOR && heightmap != Heightmap.Types.OCEAN_FLOOR_WG
        || context.topFeature().isEmpty()) return;
    var key =
        context
            .getLevel()
            .registryAccess()
            .registryOrThrow(Registries.PLACED_FEATURE)
            .getKey(context.topFeature().orElseThrow());
    if (key == null || !"hybrid_aquatic".equals(key.getNamespace())) return;
    int floor =
        AquaticWater.floor(context.getLevel(), position.getX(), position.getZ(), Integer.MIN_VALUE);
    if (floor != Integer.MIN_VALUE) callback.setReturnValue(Stream.of(position.atY(floor)));
  }
}
