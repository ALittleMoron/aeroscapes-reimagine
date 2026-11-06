package dev.aeroscapes.mixin;

import dev.aeroscapes.worldgen.CarvingSupport;
import dev.aeroscapes.worldgen.IslandCarvingContext;
import dev.aeroscapes.worldgen.IslandCaveHosts;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CarvingContext.class)
public abstract class IslandCarvingContextMixin implements IslandCarvingContext {
  @Unique private IslandCaveHosts.Range aeroscapes$layer;
  @Unique private net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator aeroscapes$generator;
  @Unique private CarvingSupport aeroscapes$support;

  @Override
  public CarvingSupport aeroscapes$support(net.minecraft.world.level.chunk.ChunkAccess target) {
    if (aeroscapes$support == null) aeroscapes$support = new CarvingSupport(target);
    return aeroscapes$support;
  }

  @Inject(method = "<init>", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Retain the native generator for source columns.
  private void aeroscapes$generator(
      net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator generator,
      net.minecraft.core.RegistryAccess registries,
      net.minecraft.world.level.LevelHeightAccessor height,
      net.minecraft.world.level.levelgen.NoiseChunk noise,
      net.minecraft.world.level.levelgen.RandomState state,
      net.minecraft.world.level.levelgen.SurfaceRules.RuleSource surface,
      CallbackInfo callback) {
    aeroscapes$generator = generator;
  }

  @Override
  public net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator aeroscapes$generator() {
    return aeroscapes$generator;
  }

  @Override
  public IslandCaveHosts.Range aeroscapes$layer() {
    return aeroscapes$layer;
  }

  @Override
  public void aeroscapes$layer(IslandCaveHosts.Range layer) {
    aeroscapes$layer = layer;
  }
}
