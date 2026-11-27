package dev.aeroscapes.startup;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class StartupMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String target, String mixin) {
    var mods = FMLLoader.getLoadingModList();
    var versions = Map.of("create", "6.0.10", "ponder", "1.0.82+mc1.21.1", "simulated", "1.3.2");
    if (versions.keySet().stream().anyMatch(id -> mods.getModFileById(id) == null)) return false;
    for (var entry : versions.entrySet()) {
      String id = entry.getKey();
      String actual =
          mods.getModFileById(id).getMods().stream()
              .filter(mod -> mod.getModId().equals(id))
              .map(mod -> mod.getVersion().toString())
              .findFirst()
              .orElseThrow();
      if (!entry.getValue().equals(actual))
        throw new MixinInitialisationError("Re-audit sprite startup adapter: " + id + " " + actual);
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
  public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}

  @Override
  public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
}
