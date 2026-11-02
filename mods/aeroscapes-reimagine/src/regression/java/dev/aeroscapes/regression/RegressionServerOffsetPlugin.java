package dev.aeroscapes.regression;

import java.util.List;
import java.util.Set;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Reproduce the integrated server's shared client-side block transformation headlessly. */
public final class RegressionServerOffsetPlugin implements IMixinConfigPlugin {
  private boolean enabled;

  @Override
  public void onLoad(String mixinPackage) {
    enabled =
        FMLLoader.getDist() == Dist.DEDICATED_SERVER
            && (Boolean.getBoolean("aeroscapes.regression.sableOffsets")
                || Boolean.getBoolean("aeroscapes.regression.islandUpdate"));
    if (enabled && FMLLoader.getLoadingModList().getModFileById("supplementaries") == null)
      throw new IllegalStateException("The server offset fixture needs pinned Supplementaries");
  }

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    return enabled;
  }

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
