package dev.aeroscapes.worldgen.feature;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.DripstoneClusterConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Retains only the feature codecs required by the adopted generation data. */
public final class IslandFeatures {
  private IslandFeatures() {}

  public static void register(IEventBus bus) {
    DeferredRegister<Feature<?>> features =
        DeferredRegister.create(Registries.FEATURE, "aeroscapes");
    features.register("pillar", () -> new PillarFeature(PillarFeatureConfig.CODEC));
    features.register(
        "basalt_cluster", () -> new BasaltClusterFeature(DripstoneClusterConfiguration.CODEC));
    features.register(bus);
  }
}
