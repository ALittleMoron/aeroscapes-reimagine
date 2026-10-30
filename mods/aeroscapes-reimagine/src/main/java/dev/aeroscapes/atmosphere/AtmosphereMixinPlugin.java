package dev.aeroscapes.atmosphere;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AtmosphereMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    Map<String, String> versions;
    if (mixinClassName.endsWith("CelestialSkyMixin")) {
      versions = Map.of("neoforge", "21.1.230");
    } else if (mixinClassName.endsWith("QuiverEmfMixin")) {
      versions = Map.of("entity_model_features", "3.3.12");
    } else if (mixinClassName.endsWith("PunchyEmfMixin")) {
      versions =
          Map.of(
              "punchy",
              "3.0",
              "entity_model_features",
              "3.3.12",
              "entity_texture_features",
              "7.2.6");
    } else if (mixinClassName.endsWith("FastRodCaptureMixin")) {
      versions = Map.of("punchy", "3.0", "flerovium", "1.1.2");
    } else if (mixinClassName.endsWith("RodCaptureTraceMixin")
        || mixinClassName.endsWith("RodLineTraceMixin")) {
      versions = Map.of("punchy", "3.0");
    } else if (mixinClassName.endsWith("SteeringOwnershipMixin")) {
      versions = Map.of("simulated", "1.3.2", "sable", "2.0.6");
    } else if (mixinClassName.endsWith("TestShipContainerMixin")) {
      versions = Map.of("sable", "2.0.6");
    } else if (mixinClassName.endsWith("PuzzleLoadingOverlayMixin")) {
      versions = Map.of("puzzle", "2.3.0", "neoforge", "21.1.230");
    } else if (mixinClassName.endsWith("PrecipitationParticleMixin")) {
      versions = Map.of("particlerain", "4.0.2", "sable", "2.0.6");
    } else {
      versions = Map.of("particlerain", "4.0.2");
    }
    var mods = FMLLoader.getLoadingModList();
    if (versions.keySet().stream().anyMatch(id -> mods.getModFileById(id) == null)) return false;
    for (var entry : versions.entrySet()) {
      String actual =
          mods.getModFileById(entry.getKey()).getMods().stream()
              .filter(mod -> mod.getModId().equals(entry.getKey()))
              .map(mod -> mod.getVersion().toString())
              .findFirst()
              .orElseThrow();
      if (!entry.getValue().equals(actual)) {
        throw new IllegalStateException(
            "Review atmosphere adapter for "
                + entry.getKey()
                + " "
                + actual
                + "; expected "
                + entry.getValue());
      }
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
  public void preApply(
      String targetClassName, ClassNode targetClass, String mixinName, IMixinInfo info) {}

  @Override
  public void postApply(
      String targetClassName, ClassNode targetClass, String mixinName, IMixinInfo info) {
    if (mixinName.endsWith("PuzzleLoadingOverlayMixin"))
      PuzzleLoadingOverlayTransformer.patch(targetClass);
  }
}
