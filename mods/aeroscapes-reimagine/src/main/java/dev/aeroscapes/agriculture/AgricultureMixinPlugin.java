package dev.aeroscapes.agriculture;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** The recipe guard is bound to the exact inspected AgriCraft implementation. */
public final class AgricultureMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String target, String mixin) {
    var file = FMLLoader.getLoadingModList().getModFileById("agricraft");
    if (file == null) return false;
    String version =
        file.getMods().stream()
            .filter(mod -> mod.getModId().equals("agricraft"))
            .map(mod -> mod.getVersion().toString())
            .findFirst()
            .orElseThrow();
    if (!version.equals("4.0.27"))
      throw new MixinInitialisationError("Re-audit AgriCraft recipe adapter: " + version);
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
