package dev.aeroscapes.atmosphere;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AtmosphereMixinPlugin implements IMixinConfigPlugin {
  @Override
  public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
    var mods = FMLLoader.getLoadingModList();
    return shouldApplyForVersions(
        mixinClassName,
        id -> {
          var file = mods.getModFileById(id);
          if (file == null) return null;
          return file.getMods().stream()
              .filter(mod -> mod.getModId().equals(id))
              .map(mod -> mod.getVersion().toString())
              .findFirst()
              .orElseThrow();
        });
  }

  static boolean shouldApplyForVersions(
      String mixinClassName, Function<String, String> versionLookup) {
    Map<String, String> versions;
    if (mixinClassName.endsWith("TooltipScrollBoundaryMixin")) {
      versions = Map.of("smart_tooltip_scroll", "1.1.1+1.21.1", "neoforge", "21.1.244");
    } else if (mixinClassName.endsWith("MiscTranslationFallbackMixin")) {
      versions = Map.of("moonlight", "1.21.1-3.0.18", "neoforge", "21.1.244");
    } else if (mixinClassName.startsWith("dev.aeroscapes.mixin.weathering.")) {
      versions =
          Map.of(
              "immersive_weathering", "1.0.4",
              "moonlight", "1.21.1-3.0.18",
              "neoforge", "21.1.244");
    } else if (mixinClassName.endsWith("EmiOresSyncMixin")) {
      versions =
          Map.of(
              "emi_ores", "1.3+1.21.1+neoforge",
              "create", "6.0.10",
              "neoforge", "21.1.244");
    } else if (mixinClassName.startsWith("dev.aeroscapes.mixin.emi.")) {
      versions =
          Map.of(
              "emi_ores", "1.3+1.21.1+neoforge",
              "emi", "1.1.24+1.21.1+neoforge",
              "create", "6.0.10",
              "neoforge", "21.1.244");
    } else if (mixinClassName.startsWith("dev.aeroscapes.mixin.construction.LittleSable")) {
      versions =
          Map.of(
              "littletiles", "1.6.0-pre236",
              "creativecore", "2.13.52",
              "sable", "2.0.6",
              "neoforge", "21.1.244");
    } else if (mixinClassName.startsWith("dev.aeroscapes.mixin.construction.")) {
      versions =
          Map.of(
              "littletiles", "1.6.0-pre236",
              "creativecore", "2.13.52",
              "neoforge", "21.1.244");
    } else if (mixinClassName.startsWith("dev.aeroscapes.mixin.travel.")) {
      versions =
          Map.of(
              "waystones", "21.1.46",
              "waystonessable", "1.0.7",
              "balm", "21.0.57",
              "sable", "2.0.6",
              "neoforge", "21.1.244");
    } else if (mixinClassName.startsWith("dev.aeroscapes.mixin.backup.")) {
      versions = Map.of("simplebackups", "1.21-4.0.21", "sable", "2.0.6", "neoforge", "21.1.244");
    } else if (mixinClassName.endsWith("LegendaryFrostStrikeMixin")
        || mixinClassName.endsWith("ReforgeAnvilReturnMixin")
        || mixinClassName.endsWith("ReforgeCatalystHintMixin")) {
      versions = Map.of("tieredneo", "1.2.0+1.21.1", "neoforge", "21.1.244");
    } else if (mixinClassName.startsWith("dev.aeroscapes.mixin.reforge.")) {
      var pins =
          new java.util.HashMap<>(Map.of("tieredneo", "1.2.0+1.21.1", "neoforge", "21.1.244"));
      if (mixinClassName.contains("ReforgeEmiRefresh")) pins.put("emi", "1.1.24+1.21.1+neoforge");
      if (mixinClassName.contains("Slingshot")) pins.put("supplementaries", "1.21.1-3.6.8");
      if (mixinClassName.contains("QuarkReaper")) pins.put("quark", "4.1-477");
      if (mixinClassName.contains("CuttingBoard")) pins.put("farmersdelight", "1.2.11a");
      if (mixinClassName.contains("Glider")) pins.put("paraglider", "21.1.5");
      if (mixinClassName.contains(".Tide")) pins.put("tide", "2.1.1");
      versions = pins;
    } else if (mixinClassName.startsWith("dev.aeroscapes.mixin.Compass")) {
      versions =
          Map.of(
              "naturescompass",
              "1.21.1-3.4.0-neoforge",
              "explorerscompass",
              "1.21.1-3.4.0-neoforge",
              "neoforge",
              "21.1.244");
    } else if (mixinClassName.startsWith("dev.aeroscapes.mixin.AccuratePlacement")) {
      // Connector exposes the Fabric build separator as '_' in the loaded mod version.
      versions =
          Map.of(
              "accurateblockplacement",
              "1.4.8_mc-1.20.5-1.21.1",
              "connector",
              "2.0.0-beta.17+1.21.1",
              "cloth_config",
              "15.0.140",
              "neoforge",
              "21.1.244");
    } else if (mixinClassName.endsWith("LunchboxHeldModelMixin")) {
      versions = Map.of("nutritionalbalance", "1.21.1-7.0.3", "neoforge", "21.1.244");
    } else if (mixinClassName.endsWith("AltimeterHeldModelMixin")) {
      versions = Map.of("supplementaries", "1.21.1-3.6.8", "neoforge", "21.1.244");
    } else if (mixinClassName.endsWith("SandpaperHeldModelMixin")) {
      versions = Map.of("create", "6.0.10", "neoforge", "21.1.244");
    } else if (mixinClassName.endsWith("ScrollFallbackModelMixin")) {
      versions = Map.of("irons_spellbooks", "1.21.1-3.16.3", "neoforge", "21.1.244");
    } else if (mixinClassName.endsWith("SwordCastPresentationMixin")) {
      versions = Map.of("irons_spellbooks", "1.21.1-3.16.3", "playeranimator", "2.0.4+1.21.1");
    } else if (mixinClassName.endsWith("TinctureDrinkingRenderMixin")
        || mixinClassName.endsWith("TinctureNativeDrinkingMixin")) {
      versions =
          Map.of(
              "irons_spellbooks",
              "1.21.1-3.16.3",
              "notenoughanimations",
              "1.12.2",
              "entity_model_features",
              "3.3.12",
              "entity_texture_features",
              "7.2.6",
              "neoforge",
              "21.1.244");
    } else if (mixinClassName.endsWith("CelestialSkyMixin")) {
      versions = Map.of("neoforge", "21.1.244");
      if (versionLookup.apply("spyglass_astronomy") != null) return false;
    } else if (mixinClassName.endsWith("SpyglassAstronomyMixin")) {
      versions = Map.of("spyglass_astronomy", "1.0.20", "neoforge", "21.1.244");
    } else if (mixinClassName.endsWith("LanguageCorrectionMixin")
        || mixinClassName.endsWith("NativeConfigurationLanguageMixin")) {
      versions = Map.of("neoforge", "21.1.244");
    } else if (mixinClassName.contains("CatnipConfig")) {
      versions = Map.of("ponder", "1.0.82+mc1.21.1");
    } else if (mixinClassName.endsWith("PuzzlesConfigurationLanguageMixin")) {
      versions = Map.of("puzzleslib", "21.1.66");
    } else if (mixinClassName.contains("Dusk")) {
      versions = Map.of("collective", "8.72");
    } else if (mixinClassName.endsWith("DistantHorizonsLanguageMixin")
        || mixinClassName.endsWith("DistantHorizonsCompatibilityChatMixin")
        || mixinClassName.endsWith("DistantHorizonsLightMaterialMixin")) {
      versions = Map.of("distanthorizons", "3.3.3");
    } else if (mixinClassName.endsWith("BetterCloudsMessageDefaultMixin")) {
      versions = Map.of("betterclouds", "1.13.11+1.21.1-neoforge");
    } else if (mixinClassName.endsWith("BetterCloudsCoverageMixin")) {
      versions =
          Map.of(
              "betterclouds", "1.13.11+1.21.1-neoforge",
              "distanthorizons", "3.3.3",
              "iris", "1.8.14-beta.1+mc1.21.1");
    } else if (mixinClassName.endsWith("DistantHorizonsChunkyStartupMixin")) {
      versions = Map.of("distanthorizons", "3.3.3", "chunky", "1.4.23");
    } else if (mixinClassName.endsWith("EffectDescriptionPagesMixin")) {
      versions = Map.of("emiffect", "2.1.6+mc1.21.1", "emi", "1.1.24+1.21.1+neoforge");
    } else if (mixinClassName.endsWith("CreativeSearchMixin")) {
      versions = Map.of("neoforge", "21.1.244");
    } else if (mixinClassName.contains("EmiTrades")) {
      versions = Map.of("emitrades", "2.0.0+alpha.1", "emi", "1.1.24+1.21.1+neoforge");
    } else if (mixinClassName.endsWith("EmiReloadStepLanguageMixin")) {
      versions = Map.of("emi", "1.1.24+1.21.1+neoforge", "jei", "19.39.0.372");
    } else if (mixinClassName.endsWith("HorseMountedMiningScopeMixin")
        || mixinClassName.endsWith("HorseRecallSnapshotMixin")) {
      versions = Map.of("horseman", "1.5.13");
    } else if (mixinClassName.endsWith("EasyAnvilsFreeRenameMixin")) {
      versions =
          Map.of(
              "easyanvils",
              "21.1.0",
              "allurement",
              "5.0.1",
              "puzzleslib",
              "21.1.66",
              "neoforge",
              "21.1.244");
    } else if (mixinClassName.endsWith("EasyMagicExperiencePointsMixin")) {
      versions = Map.of("easymagic", "21.1.4", "neoforge", "21.1.244");
    } else if (mixinClassName.endsWith("PickupNotifierLanguageMixin")) {
      versions = Map.of("pickupnotifier", "21.1.1");
    } else if (mixinClassName.startsWith("dev.aeroscapes.mixin.atmosphere.InvMove")) {
      versions = Map.of("invmove", "0.9.3", "cloth_config", "15.0.140");
    } else if (mixinClassName.endsWith("SophisticatedInventoryFocusAccess")) {
      versions = Map.of("sophisticatedcore", "1.5.17");
    } else if (mixinClassName.endsWith("TomsTerminalPopupAccess")
        || mixinClassName.endsWith("TomsPopupMenuAccess")) {
      versions = Map.of("toms_storage", "2.4.2");
    } else if (mixinClassName.endsWith("ForgematicaMessageMixin")) {
      versions = Map.of("forgematica", "0.4.3+mc1.21.1", "mafglib", "0.4.3+mc1.21.1");
    } else if (mixinClassName.endsWith("AdvancementCriterionLanguageMixin")) {
      versions = Map.of("betteradvancements", "0.4.3.21", "zeta", "1.1-40");
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
    } else if (mixinClassName.endsWith("LanternGripMixin")
        || mixinClassName.endsWith("PunchyMapMixin")) {
      versions = Map.of("punchy", "3.0");
    } else if (mixinClassName.endsWith("HeldEquipmentGripMixin")
        || mixinClassName.endsWith("RodCaptureTraceMixin")
        || mixinClassName.endsWith("RodLineTraceMixin")) {
      versions = Map.of("punchy", "3.0");
    } else if (mixinClassName.endsWith("SteeringOwnershipMixin")) {
      versions = Map.of("simulated", "1.3.2", "sable", "2.0.6");
    } else if (mixinClassName.endsWith("TestShipContainerMixin")) {
      versions = Map.of("sable", "2.0.6");
    } else if (mixinClassName.endsWith("PuzzleLoadingOverlayMixin")) {
      versions = Map.of("puzzle", "2.3.0", "neoforge", "21.1.244");
    } else if (mixinClassName.endsWith("PrecipitationParticleMixin")) {
      versions = Map.of("particlerain", "4.0.2", "sable", "2.0.6");
    } else {
      versions = Map.of("particlerain", "4.0.2");
    }
    if (versions.keySet().stream().anyMatch(id -> versionLookup.apply(id) == null)) return false;
    for (var entry : versions.entrySet()) {
      String actual = versionLookup.apply(entry.getKey());
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
