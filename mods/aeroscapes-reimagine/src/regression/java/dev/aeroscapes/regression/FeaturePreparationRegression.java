package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.regression.noise.mixin.FeatureIndexAccess;
import dev.aeroscapes.regression.noise.mixin.LazyCachedValueAccess;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.FeatureSorter;
import net.neoforged.neoforge.common.util.Lazy;

/** Verify startup preparation and native refresh on the actual installed generators. */
public final class FeaturePreparationRegression {
  private FeaturePreparationRegression() {}

  public static void verify(ServerLevel level) {
    for (ServerLevel world : level.getServer().getAllLevels()) {
      var generator = world.getChunkSource().getGenerator();
      var supplier = ((FeatureIndexAccess) generator).aeroscapesRegression$featuresPerStep();
      if (!(supplier instanceof Lazy<?> lazy))
        throw new IllegalStateException("Expected NeoForge Lazy");
      var inspection = (LazyCachedValueAccess) (Object) lazy;
      Object prepared = inspection.aeroscapesRegression$cachedValue();
      if (prepared == null)
        throw new IllegalStateException(
            "Feature index was not prepared for " + world.dimension().location());
      List<FeatureSorter.StepFeatureData> before = supplier.get();
      if (before != prepared)
        throw new IllegalStateException("Unexpected feature supplier identity");
      var features = before.stream().map(FeatureSorter.StepFeatureData::features).toList();
      generator.refreshFeaturesPerStep();
      if (inspection.aeroscapesRegression$cachedValue() != null)
        throw new IllegalStateException("Refresh did not invalidate native feature index");
      var rebuilt = supplier.get().stream().map(FeatureSorter.StepFeatureData::features).toList();
      if (!features.equals(rebuilt))
        throw new IllegalStateException("Feature order changed after native refresh");
    }
    LogUtils.getLogger()
        .info(
            "Feature preparation regression passed: startup cache, invalidation and feature ordering");
  }
}
