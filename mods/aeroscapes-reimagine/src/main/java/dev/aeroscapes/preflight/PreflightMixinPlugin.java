package dev.aeroscapes.preflight;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class PreflightMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    var mods = FMLLoader.getLoadingModList();
    if (mods.getModFileById("aeronautics") == null || mods.getModFileById("sable") == null)
      return false;
    for (var entry : Map.of("aeronautics", "1.3.2", "sable", "2.0.6").entrySet()) {
      String actual =
          mods.getModFileById(entry.getKey()).getMods().stream()
              .filter(mod -> mod.getModId().equals(entry.getKey()))
              .map(mod -> mod.getVersion().toString())
              .findFirst()
              .orElseThrow();
      if (!entry.getValue().equals(actual))
        throw new IllegalStateException(
            "Review preflight integration for " + entry.getKey() + " " + actual);
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
