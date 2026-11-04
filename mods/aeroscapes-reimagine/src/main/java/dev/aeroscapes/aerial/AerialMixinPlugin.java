package dev.aeroscapes.aerial;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** The login-message adapter is specific to the selected stable vessel provider. */
public final class AerialMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String target, String mixin) {
    var file = FMLLoader.getLoadingModList().getModFileById("cr_aero_structures");
    if (file == null) return false;
    String version =
        file.getMods().stream()
            .filter(mod -> mod.getModId().equals("cr_aero_structures"))
            .map(mod -> mod.getVersion().toString())
            .findFirst()
            .orElseThrow();
    if (!version.equals("1.0.0"))
      throw new MixinInitialisationError("Re-audit aerial login adapter: " + version);
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
  public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

  @Override
  public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
