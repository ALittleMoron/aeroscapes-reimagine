package dev.aeroscapes.mixin.ecology;

import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public interface SpawnSurfaceContextAccess {
  @Accessor("blockX")
  int aeroscapes$x();

  @Accessor("blockY")
  int aeroscapes$y();

  @Accessor("blockZ")
  int aeroscapes$z();

  @Accessor("randomState")
  RandomState aeroscapes$random();

  @Accessor("biome")
  Supplier<Holder<Biome>> aeroscapes$biome();

  @Accessor("stoneDepthAbove")
  int aeroscapes$stoneDepthAbove();

  @Accessor("waterHeight")
  int aeroscapes$waterHeight();
}
