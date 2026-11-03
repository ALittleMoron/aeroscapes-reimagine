package dev.aeroscapes.survival;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class SurvivalMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    var file = FMLLoader.getLoadingModList().getModFileById("nutritionalbalance");
    if (file == null) return false;
    String version =
        file.getMods().stream()
            .filter(mod -> mod.getModId().equals("nutritionalbalance"))
            .map(mod -> mod.getVersion().toString())
            .findFirst()
            .orElseThrow();
    if (!version.equals("1.21.1-7.0.3"))
      throw new IllegalStateException(
          "Review nutrition interface for Nutritional Balance " + version);
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
