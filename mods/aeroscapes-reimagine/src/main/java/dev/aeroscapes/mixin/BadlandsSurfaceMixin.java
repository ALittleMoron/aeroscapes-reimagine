package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SurfaceSystem.class)
public abstract class BadlandsSurfaceMixin {
  @Unique private RandomState aeroscapesReimagine$badlandsRandom;

  @Inject(method = "<init>", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Retain this surface system's immutable seeded state.
  private void aeroscapesReimagine$badlandsState(
      RandomState random,
      BlockState defaultBlock,
      int seaLevel,
      PositionalRandomFactory positional,
      CallbackInfo callback) {
    aeroscapesReimagine$badlandsRandom = random;
  }

  @Inject(method = "erodedBadlandsExtension", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Empty native heightmaps otherwise gain pillars down to min Y.
  private void aeroscapesReimagine$supportedBadlands(
      BlockColumn column,
      int x,
      int z,
      int surfaceY,
      LevelHeightAccessor bounds,
      CallbackInfo callback) {
    // Native buildSurface supplies WORLD_SURFACE_WG + 1: min Y means no existing land.
    if (surfaceY <= bounds.getMinBuildHeight()
        && IslandTerrain.from(aeroscapesReimagine$badlandsRandom) != null) callback.cancel();
  }

  @ModifyConstant(method = "erodedBadlandsExtension", constant = @Constant(doubleValue = 64.0))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Keep native spire relief relative to low land.
  private double aeroscapesReimagine$badlandsBase(
      double nativeBase,
      BlockColumn column,
      int x,
      int z,
      int surfaceY,
      LevelHeightAccessor bounds) {
    return IslandTerrain.from(aeroscapesReimagine$badlandsRandom) == null
        ? nativeBase
        : Math.min(nativeBase, surfaceY);
  }
}
