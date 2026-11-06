package dev.aeroscapes.regression.noise.mixin;

import dev.aeroscapes.regression.CaveNetworkRegression;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ConfiguredWorldCarver.class)
public abstract class CaveStartCaptureMixin {
  @Inject(method = "carve", at = @At("HEAD"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Observe probability-selected native layer passes without
  // consuming RNG.
  private void aeroscapes$selectedSource(
      CarvingContext context,
      ChunkAccess target,
      Function<BlockPos, Holder<Biome>> biomes,
      RandomSource random,
      Aquifer aquifer,
      ChunkPos source,
      CarvingMask mask,
      CallbackInfoReturnable<Boolean> callback) {
    var carver = (ConfiguredWorldCarver<?>) (Object) this;
    CaveNetworkRegression.started(
        context, target, source, carver.worldCarver().getClass().getName());
  }
}
