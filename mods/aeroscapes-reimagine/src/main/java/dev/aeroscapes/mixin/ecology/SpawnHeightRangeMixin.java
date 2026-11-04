package dev.aeroscapes.mixin.ecology;

import dev.aeroscapes.ecology.SpawnIslandSites;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HeightRangePlacement.class)
public abstract class SpawnHeightRangeMixin {
  @Unique
  private static final Set<String> SEA_RELATIVE =
      Set.of(
          "sea_star",
          "sea_urchin",
          "dead_sea_star",
          "dead_sea_urchin",
          "ore_reefstone",
          "above_ground_glow_lichen");

  @Inject(method = "getPositions", at = @At("RETURN"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Retain the native distribution and RNG draw, translating its
  // sea-relative band.
  private void aeroscapes$localHeightBand(
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
    if (key == null || !key.getNamespace().equals("spawn") || !SEA_RELATIVE.contains(key.getPath()))
      return;
    int sea =
        SpawnIslandSites.seaLevel(
            level.getLevel().getChunkSource().randomState(),
            position.getX(),
            position.getZ(),
            Integer.MIN_VALUE);
    if (sea == Integer.MIN_VALUE) return;
    callback.setReturnValue(
        callback
            .getReturnValue()
            .map(sample -> sample.above(sea - 63))
            .filter(sample -> !level.isOutsideBuildHeight(sample)));
  }
}
