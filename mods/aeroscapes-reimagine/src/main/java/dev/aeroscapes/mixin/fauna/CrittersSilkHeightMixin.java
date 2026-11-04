package dev.aeroscapes.mixin.fauna;

import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HeightRangePlacement.class)
public abstract class CrittersSilkHeightMixin {
  @Unique
  private static final Set<String> COCOONS =
      Set.of("silk_cocoon", "silk_cocoon_lush", "hanging_silk_cocoon", "hanging_silk_cocoon_lush");

  @Inject(method = "getPositions", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Retain one uniform draw; later native scan/support/water/biome
  // filters still apply.
  private void aeroscapes$allIslandHeights(
      PlacementContext context,
      RandomSource random,
      BlockPos position,
      CallbackInfoReturnable<Stream<BlockPos>> callback) {
    if (context.topFeature().isEmpty()) return;
    var level = context.getLevel();
    var id =
        level
            .registryAccess()
            .registryOrThrow(Registries.PLACED_FEATURE)
            .getKey(context.topFeature().orElseThrow());
    if (id == null
        || !id.getNamespace().equals("crittersandcompanions")
        || !COCOONS.contains(id.getPath())
        || IslandTerrain.from(level.getLevel().getChunkSource().randomState()) == null) return;
    int height =
        Mth.nextInt(random, level.getMinBuildHeight() + 16, level.getMaxBuildHeight() - 17);
    callback.setReturnValue(Stream.of(position.atY(height)));
  }
}
