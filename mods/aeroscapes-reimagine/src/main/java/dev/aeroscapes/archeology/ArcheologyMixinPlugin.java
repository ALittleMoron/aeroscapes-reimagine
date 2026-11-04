package dev.aeroscapes.archeology;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** These display and placement adapters are bound to the inspected native files. */
public final class ArcheologyMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String target, String mixin) {
    return available("betterarcheology", "1.21.1-1.3.9")
        && available("neoforge", "21.1.244")
        && (!mixin.contains(".Resourceful") || available("resourcefulconfig", "3.0.11"));
  }

  private static boolean available(String id, String expected) {
    var file = FMLLoader.getLoadingModList().getModFileById(id);
    if (file == null) return false;
    String actual =
        file.getMods().stream()
            .filter(mod -> mod.getModId().equals(id))
            .map(mod -> mod.getVersion().toString())
            .findFirst()
            .orElseThrow();
    if (!expected.equals(actual))
      throw new MixinInitialisationError("Re-audit archeology adapter: " + id + " " + actual);
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
