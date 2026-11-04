package dev.aeroscapes.aquatic;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import net.neoforged.fml.loading.FMLLoader;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.Config;

final class TealServerMixinCompatibility {
  private static final String CONFIG = "teallib.mixins.json";
  private static final String CLIENT_ACCESSOR = "accessor.HierarchicalModelAccessor";

  private TealServerMixinCompatibility() {}

  static void prepare() {
    if (MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.SERVER) return;
    var file = FMLLoader.getLoadingModList().getModFileById("teallib");
    if (file == null) return;
    String version =
        file.getMods().stream()
            .filter(mod -> mod.getModId().equals("teallib"))
            .map(mod -> mod.getVersion().toString())
            .findFirst()
            .orElseThrow();
    if (!version.equals("1.3.teal"))
      throw new MixinInitialisationError("Review Teal Lib server mixin compatibility: " + version);

    try {
      Field configs = Config.class.getDeclaredField("allConfigs");
      configs.setAccessible(true);
      if (!(configs.get(null) instanceof Map<?, ?> allConfigs)
          || !(allConfigs.get(CONFIG) instanceof Config config))
        throw new MixinInitialisationError("Teal Lib mixin configuration was not registered");
      Object rawConfig = config.getConfig();
      Field mixins = rawConfig.getClass().getDeclaredField("mixinClasses");
      mixins.setAccessible(true);
      if (!(mixins.get(rawConfig) instanceof List<?> entries)
          || !entries.equals(
              List.of(
                  "AxolotlMixin",
                  "MobBucketItemMixin",
                  CLIENT_ACCESSOR,
                  "accessor.TropicalFishAccessor")))
        throw new MixinInitialisationError("Teal Lib mixin configuration changed");
      // Teal 1.3 puts this client-only accessor in its common mixin list.
      entries.remove(CLIENT_ACCESSOR);
    } catch (ReflectiveOperationException exception) {
      throw new MixinInitialisationError("Cannot prepare Teal Lib server mixins", exception);
    }
  }
}
