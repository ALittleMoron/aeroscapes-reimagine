package dev.aeroscapes.pickup;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class PickupMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    var mods = FMLLoader.getLoadingModList();
    for (var expected :
        Map.of(
                "itemphysic", "1.8.17",
                "creativecore", "2.13.52",
                "sable", "2.0.6",
                "neoforge", "21.1.244")
            .entrySet()) {
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
            "Review pickup adapter for " + expected.getKey() + " " + actual);
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
