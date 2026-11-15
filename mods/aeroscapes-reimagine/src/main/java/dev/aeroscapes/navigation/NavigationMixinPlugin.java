package dev.aeroscapes.navigation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class NavigationMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    var mods = FMLLoader.getLoadingModList();
    return supportsVersions(
        id -> {
          var file = mods.getModFileById(id);
          if (file == null) return null;
          return file.getMods().stream()
              .filter(mod -> mod.getModId().equals(id))
              .map(mod -> mod.getVersion().toString())
              .findFirst()
              .orElseThrow();
        });
  }

  static boolean supportsVersions(Function<String, String> lookup) {
    Map<String, String> required =
        Map.of(
            "xaeroworldmap", "1.47.0",
            "xaerominimap", "26.6.0",
            "xaerolib", "1.7.3",
            "neoforge", "21.1.244");
    if (lookup.apply("xaeroworldmap") == null || lookup.apply("xaerominimap") == null) return false;
    for (var entry : required.entrySet()) {
      String actual = lookup.apply(entry.getKey());
      if (!entry.getValue().equals(actual))
        throw new IllegalStateException(
            "Aeroscapes map layer integration requires "
                + entry.getKey()
                + " "
                + entry.getValue()
                + "; found "
                + actual);
    }
    return true;
  }

  @Override
  public void onLoad(String mixinPackage) {}

  @Override
  public String getRefMapperConfig() {
    return null;
  }

  @Override
  public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

  @Override
  public List<String> getMixins() {
    return null;
  }

  @Override
  public void preApply(
      String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

  @Override
  public void postApply(
      String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
