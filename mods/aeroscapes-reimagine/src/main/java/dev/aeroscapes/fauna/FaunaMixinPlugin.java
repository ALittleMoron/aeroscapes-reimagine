package dev.aeroscapes.fauna;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class FaunaMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String target, String mixin) {
    if (mixin.endsWith("NaturalistSeatedRiderMixin"))
      return available("naturalist", "2.0.7")
          && available("entity_model_features", "3.3.12")
          && available("entity_texture_features", "7.2.6")
          && available("neoforge", "21.1.244");
    if (mixin.endsWith("FaunaPackDescriptionMixin"))
      return available("naturalist", "2.0.7")
          && available("crittersandcompanions", "2.7.0")
          && available("neoforge", "21.1.244");
    String id = mixin.contains("Naturalist") ? "naturalist" : "crittersandcompanions";
    return available(id, id.equals("naturalist") ? "2.0.7" : "2.7.0")
        && available("neoforge", "21.1.244");
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
      throw new IllegalStateException("Re-audit fauna adapter: " + id + " " + actual);
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
