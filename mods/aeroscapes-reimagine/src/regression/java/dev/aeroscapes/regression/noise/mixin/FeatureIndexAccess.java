package dev.aeroscapes.regression.noise.mixin;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkGenerator.class)
public interface FeatureIndexAccess {
  @Accessor("featuresPerStep")
  Supplier<List<FeatureSorter.StepFeatureData>> aeroscapesRegression$featuresPerStep();
}
