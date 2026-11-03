package dev.aeroscapes.aquatic;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Every adapter targets the distributed release, including its Kotlin companion classes. */
public final class AquaticMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    if (mixinClassName.endsWith("RefinedRodCastMixin")
        || mixinClassName.endsWith("BlockModelElementsAccess"))
      return available("neoforge", "21.1.230");
    var mods = FMLLoader.getLoadingModList();
    String id = targetClassName.startsWith("com.li64.tide.") ? "tide" : "hybrid_aquatic";
    if (targetClassName.startsWith("dev.hybridlabs.hapi.")) id = "hapi";
    if (targetClassName.startsWith("com.teamabnormals.blueprint.")) id = "blueprint";
    if (targetClassName.startsWith("timmychips.modefiteitemdefinitions.")) id = "modefite";
    if (targetClassName.startsWith("punchy.")) id = "punchy";
    if ((id.equals("modefite") || id.equals("punchy")) && !available("tide", "2.1.1")) return false;
    if (mixinClassName.endsWith("TideRodLineMixin") && !available("punchy", "3.0")) return false;
    if (targetClassName.startsWith("dev.worldgen.lithostitched.")) {
      if (mods.getModFileById("hybrid_aquatic") == null) return false;
      id = "lithostitched";
    }
    String version =
        switch (id) {
          case "tide" -> "2.1.1";
          case "hapi" -> "1.1.3";
          case "lithostitched" -> "1.8.1";
          case "blueprint" -> "8.0.8";
          case "modefite" -> "1.0.1+1.21.1";
          case "punchy" -> "3.0";
          default -> "1.7.4";
        };
    var file = mods.getModFileById(id);
    if (file == null) return false;
    String modId = id;
    String actual =
        file.getMods().stream()
            .filter(mod -> modId.equals(mod.getModId()))
            .map(mod -> mod.getVersion().toString())
            .findFirst()
            .orElse("");
    if (!version.equals(actual))
      throw new IllegalStateException("Unsupported aquatic adapter: " + id + " " + actual);
    return !mixinClassName.endsWith("TideEncounterMixin")
        || mods.getModFileById("hybrid_aquatic") != null;
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
    if (!actual.equals(expected))
      throw new IllegalStateException("Review aquatic equipment adapter for " + id + " " + actual);
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
