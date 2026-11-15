package dev.aeroscapes.sky;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the whale-fall feature; its placement lives in the owned worldgen data. */
public final class SkyWhaleFalls {
  private SkyWhaleFalls() {}

  public static void register(IEventBus bus) {
    DeferredRegister<Feature<?>> features =
        DeferredRegister.create(Registries.FEATURE, "aeroscapes_reimagine");
    features.register(
        "sky_whale_fall", () -> new SkyWhaleFallFeature(SkyWhaleFallFeature.Config.CODEC));
    features.register(bus);
  }
}
