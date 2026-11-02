package dev.aeroscapes.worldgen.opencl;

import com.ishland.c2me.base.common.ModuleMixinPlugin;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Follow the accelerator's native enablement before targeting its added methods. */
public final class OpenClMixinPlugin implements IMixinConfigPlugin {
  @Nullable private IMixinConfigPlugin module;

  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    if (mixinClassName.endsWith("OpenClEventDiagnosticsMixin")
        && !Files.isRegularFile(
            FMLPaths.CONFIGDIR.get().resolve("aeroscapes-chunk-diagnostics.enabled"))) return false;
    return module != null && module.shouldApplyMixin(targetClassName, mixinClassName);
  }

  @Override
  public void onLoad(String mixinPackage) {
    if (FMLLoader.getLoadingModList().getModFileById("c2me_opts_accel_opencl") == null) return;
    var gate = new ModuleMixinPlugin();
    gate.onLoad("com.ishland.c2me.opts.accel.opencl.mixin");
    module = gate;
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
