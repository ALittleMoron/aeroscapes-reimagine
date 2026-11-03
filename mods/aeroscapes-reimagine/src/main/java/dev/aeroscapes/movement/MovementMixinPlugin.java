package dev.aeroscapes.movement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class MovementMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    var mods = FMLLoader.getLoadingModList();
    Map<String, String> versions =
        new HashMap<>(Map.of("paraglider", "21.1.5", "yo_hooks", "5.4.0", "neoforge", "21.1.230"));
    if (mixinClassName.contains("ForeignConfiguration")
        || mixinClassName.endsWith("CatnipConfigurationAccess"))
      versions.put("ponder", "1.0.82+mc1.21.1");
    if (mixinClassName.endsWith("HookItemCaptureMixin")
        || mixinClassName.endsWith("HookWorldRenderMixin")
        || mixinClassName.endsWith("HookLineMixin")) {
      versions.put("punchy", "3.0");
      versions.put("flerovium", "1.1.2");
    }
    for (var expected : versions.entrySet()) {
      var file = mods.getModFileById(expected.getKey());
      if (file == null) return false;
      String actual =
          file.getMods().stream()
              .filter(mod -> mod.getModId().equals(expected.getKey()))
              .map(mod -> mod.getVersion().toString())
              .findFirst()
              .orElseThrow();
      if (!expected.getValue().equals(actual))
        throw new IllegalStateException(
            "Review movement adapter for " + expected.getKey() + " " + actual);
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
      String targetClassName, ClassNode targetClass, String mixinName, IMixinInfo info) {}

  @Override
  public void postApply(
      String targetClassName, ClassNode targetClass, String mixinName, IMixinInfo info) {}
}
