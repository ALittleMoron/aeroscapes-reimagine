package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.IslandStopStructure;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class IslandStopDecorationMixin {
  @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes the callback.
  private void aeroscapes$finishStopPlants(
      WorldGenLevel level, ChunkAccess chunk, StructureManager structures, CallbackInfo callback) {
    if (level instanceof WorldGenRegion region) IslandStopStructure.finishStopPlants(region, chunk);
  }
}
