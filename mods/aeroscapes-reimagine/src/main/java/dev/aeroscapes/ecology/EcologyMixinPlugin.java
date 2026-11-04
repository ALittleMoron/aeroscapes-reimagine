package dev.aeroscapes.ecology;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Every ecology adapter belongs to the exact distributed provider and loader. */
public final class EcologyMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String target, String mixin) {
    if (mixin.contains("Wetland") || mixin.endsWith("SpawnHeightPlacementMixin"))
      return available("wetland_whimsy", "2.1.0-1.21.1")
          && available("biolith", "3.0.14")
          && available("neoforge", "21.1.244");
    if (mixin.endsWith(".SpawnFoodConfigurationMixin")
        || mixin.endsWith(".SpawnFeastCompatibilityMixin"))
      return available("spawn", "4.0.8-1.21.1")
          && available("farmersdelight", "1.2.11a")
          && available("neoforge", "21.1.244");
    if (mixin.contains("Fowl"))
      return available("fowlplay", "1.2.3") && available("neoforge", "21.1.244");
    if (mixin.contains("Hybrid"))
      return available("hybrid_birds", "1.3.3")
          && available("hapi", "1.1.3")
          && available("neoforge", "21.1.244");
    String id = mixin.contains("Debris") ? "rocks" : "spawn";
    String expected = id.equals("rocks") ? "1.8.0" : "4.0.8-1.21.1";
    return available(id, expected) && available("neoforge", "21.1.244");
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
      throw new MixinInitialisationError("Re-audit ecology adapter: " + id + " " + actual);
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
  public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
    if (mixin.endsWith(".SpawnClientTintMixin")) SpawnTintTransformer.patch(node);
  }
}
