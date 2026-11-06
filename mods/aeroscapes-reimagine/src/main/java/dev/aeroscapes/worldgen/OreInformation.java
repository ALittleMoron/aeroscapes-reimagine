package dev.aeroscapes.worldgen;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.worldgen.ConfigPlacementFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.RandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockStateMatchTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

/** Display snapshots of active native features; never changes generation or loads chunks. */
public final class OreInformation {
  private OreInformation() {}

  public record Snapshot(
      Map<ResourceLocation, PlacedFeature> features,
      SetMultimap<ResourceKey<PlacedFeature>, ResourceKey<Biome>> biomes) {}

  public static Snapshot snapshot(MinecraftServer server) {
    Map<ResourceKey<PlacedFeature>, Set<ResourceKey<Biome>>> roots = new LinkedHashMap<>();
    for (var level : server.getAllLevels()) {
      for (var biome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
        var biomeKey = biome.unwrapKey();
        if (!biome.isBound() || biomeKey.isEmpty()) continue;
        for (var step : biome.value().getGenerationSettings().features()) {
          for (var feature : step) {
            if (!feature.isBound()) continue;
            feature
                .unwrapKey()
                .ifPresent(
                    key ->
                        roots
                            .computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                            .add(biomeKey.orElseThrow()));
          }
        }
      }
    }
    Map<ResourceLocation, PlacedFeature> features = new LinkedHashMap<>();
    SetMultimap<ResourceKey<PlacedFeature>, ResourceKey<Biome>> biomes = HashMultimap.create();
    var registry = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
    roots.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceKey::location)))
        .forEach(
            entry -> {
              var feature = registry.get(entry.getKey());
              if (feature != null)
                expand(
                    entry.getKey().location(),
                    feature,
                    List.of(),
                    1.0,
                    "",
                    entry.getValue(),
                    features,
                    biomes,
                    Collections.newSetFromMap(new IdentityHashMap<>()));
            });
    return new Snapshot(Map.copyOf(features), biomes);
  }

  private static void expand(
      ResourceLocation root,
      PlacedFeature feature,
      List<PlacementModifier> inherited,
      double probability,
      String branch,
      Set<ResourceKey<Biome>> membership,
      Map<ResourceLocation, PlacedFeature> features,
      SetMultimap<ResourceKey<PlacedFeature>, ResourceKey<Biome>> biomes,
      Set<PlacedFeature> path) {
    if (probability <= 0 || !path.add(feature)) return;
    try {
      List<PlacementModifier> placement = new ArrayList<>(inherited);
      placement.addAll(feature.placement());
      if (placement.stream().anyMatch(ConfigPlacementFilter.class::isInstance)
          && AllConfigs.common().worldGen.disable.get()) return;
      var configuration = feature.feature().value().config();
      if (configuration instanceof RandomFeatureConfiguration selector) {
        double remaining = probability;
        for (int index = 0; index < selector.features.size(); index++) {
          var choice = selector.features.get(index);
          expand(
              root,
              choice.feature.value(),
              placement,
              remaining * choice.chance,
              branch + "/choice_" + index,
              membership,
              features,
              biomes,
              path);
          remaining *= 1.0 - choice.chance;
        }
        expand(
            root,
            selector.defaultFeature.value(),
            placement,
            remaining,
            branch + "/default",
            membership,
            features,
            biomes,
            path);
      } else if (configuration instanceof OreConfiguration
          || configuration instanceof GeodeConfiguration) {
        ResourceLocation id =
            branch.isEmpty()
                ? root
                : ResourceLocation.fromNamespaceAndPath(
                    "aeroscapes_reimagine",
                    "emi/selector/" + root.getNamespace() + "/" + root.getPath() + branch);
        features.put(
            id,
            new PlacedFeature(
                displayConfiguration(feature.feature()),
                supportedPlacement(placement, probability)));
        biomes.putAll(ResourceKey.create(Registries.PLACED_FEATURE, id), membership);
      }
    } finally {
      path.remove(feature);
    }
  }

  private static Holder<ConfiguredFeature<?, ?>> displayConfiguration(
      Holder<ConfiguredFeature<?, ?>> original) {
    var configured = original.value();
    if (configured.feature() != Feature.ORE
        || !(configured.config() instanceof OreConfiguration ore)) return original;
    List<OreConfiguration.TargetBlockState> targets = new ArrayList<>(ore.targetStates);
    Set<net.minecraft.world.level.block.state.BlockState> added = new LinkedHashSet<>();
    var blackstone = Blocks.BLACKSTONE.defaultBlockState();
    for (var target : ore.targetStates) {
      var adapted = BlackstoneOres.adapt(blackstone, target.state);
      if (adapted != target.state && matchesBlackstone(target.target) && added.add(adapted))
        targets.add(OreConfiguration.target(new BlockMatchTest(Blocks.BLACKSTONE), adapted));
    }
    return Holder.direct(
        new ConfiguredFeature<>(
            Feature.ORE, new OreConfiguration(targets, ore.size, ore.discardChanceOnAirExposure)));
  }

  private static boolean matchesBlackstone(RuleTest rule) {
    return (rule instanceof TagMatchTest
            || rule instanceof BlockMatchTest
            || rule instanceof BlockStateMatchTest)
        && rule.test(Blocks.BLACKSTONE.defaultBlockState(), RandomSource.create(0));
  }

  private static List<PlacementModifier> supportedPlacement(
      List<PlacementModifier> original, double probability) {
    List<PlacementModifier> result = new ArrayList<>();
    double rarity = 1.0 / probability;
    for (var modifier : original) {
      if (modifier instanceof RarityFilter filter)
        rarity *= ((cc.abbie.emi_ores.mixin.accessor.RarityFilterAccessor) filter).getChance();
      else if (modifier instanceof HeightRangePlacement || modifier instanceof CountPlacement)
        result.add(modifier);
    }
    if (rarity > 1.0) result.add(RarityFilter.onAverageOnceEvery((int) Math.round(rarity)));
    return List.copyOf(result);
  }
}
