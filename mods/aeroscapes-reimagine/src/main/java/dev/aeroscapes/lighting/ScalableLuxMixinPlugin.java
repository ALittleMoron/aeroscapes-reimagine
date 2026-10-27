package dev.aeroscapes.lighting;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.IModInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Enable the plot adapter only for the artifacts whose internals it targets. */
public final class ScalableLuxMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    var mods = FMLLoader.getLoadingModList();
    var scalable = mods.getModFileById("scalablelux");
    if (scalable == null) return false;
    if (mods.getModFileById("lucis") != null) {
      throw new IllegalStateException("Lucis and ScalableLux cannot be installed together");
    }
    var sable = mods.getModFileById("sable");
    if (sable == null) return false;
    requireVersion(scalable.getMods(), "scalablelux", "0.1.0.1+neoforge.1cb1e91");
    requireVersion(sable.getMods(), "sable", "2.0.6");
    if (mixinClassName.endsWith(".PlotCopycatMixin")) {
      var create = mods.getModFileById("create");
      if (create == null) return false;
      requireVersion(create.getMods(), "create", "6.0.10");
    }
    return true;
  }

  private static void requireVersion(List<IModInfo> mods, String id, String expected) {
    String actual =
        mods.stream()
            .filter(mod -> id.equals(mod.getModId()))
            .map(mod -> mod.getVersion().toString())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing mod metadata for " + id));
    if (!expected.equals(actual)) {
      throw new IllegalStateException(
          "Unsupported " + id + " version " + actual + "; expected " + expected);
    }
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
