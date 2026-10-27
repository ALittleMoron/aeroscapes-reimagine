package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.BasinAquifer;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunk.class)
public abstract class BasinAquiferMixin {
  @Shadow @Final @Mutable private Aquifer aquifer;

  @Inject(method = "<init>", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the pinned NoiseChunk constructor.
  private void aeroscapesReimagine$localWater(
      int cellCount,
      RandomState random,
      int x,
      int z,
      net.minecraft.world.level.levelgen.NoiseSettings noise,
      net.minecraft.world.level.levelgen.DensityFunctions.BeardifierOrMarker beardifier,
      net.minecraft.world.level.levelgen.NoiseGeneratorSettings settings,
      Aquifer.FluidPicker fluids,
      net.minecraft.world.level.levelgen.blending.Blender blender,
      CallbackInfo callback) {
    IslandTerrain terrain = IslandTerrain.from(random);
    if (terrain != null && settings.isAquifersEnabled())
      aquifer = new BasinAquifer(aquifer, terrain);
  }
}
