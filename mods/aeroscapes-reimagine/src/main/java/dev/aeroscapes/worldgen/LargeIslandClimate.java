package dev.aeroscapes.worldgen;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;

/** Coherent surface zones on existing shelves, with the native climate around their margins. */
final class LargeIslandClimate {
  private static final ResourceKey<Biome> MARSH = nature("marsh");
  private static final ResourceKey<Biome> PRAIRIE = nature("prairie");
  private static final Set<ResourceKey<Biome>> ARID_TRANSITIONS =
      Set.of(Biomes.SAVANNA, nature("arid_savanna"), nature("drylands"), nature("xeric_plains"));

  private final Holder<Biome> desert;
  private final Holder<Biome> marsh;
  private final Holder<Biome> snowy;
  private final Holder<Biome> plains;
  private final Holder<Biome> forest;

  LargeIslandClimate(Registry<Biome> registry) {
    desert = registry.getHolderOrThrow(Biomes.DESERT);
    marsh = registry.getHolder(MARSH).orElseGet(() -> registry.getHolderOrThrow(Biomes.SWAMP));
    snowy = registry.getHolderOrThrow(Biomes.SNOWY_PLAINS);
    plains = registry.getHolderOrThrow(Biomes.PLAINS);
    forest = registry.getHolderOrThrow(Biomes.FOREST);
  }

  List<Holder<Biome>> possibleBiomes() {
    return List.of(desert, marsh, snowy, plains, forest);
  }

  enum Profile {
    ARID,
    MARSH,
    SNOWY,
    WOODED,
    GRASSLAND;

    @Nullable
    static Profile forIsland(@Nullable IslandLayout.LargeIsland island, Climate.Sampler sampler) {
      if (island == null || island.peakHeight() != 0 || island.anchor() < 32) return null;
      var centre =
          new SinglePointContext(
              (int) Math.floor(island.centerX()) & ~3,
              (int) island.anchor(),
              (int) Math.floor(island.centerZ()) & ~3);
      return fromClimate(
          island.anchor(),
          sampler.temperature().compute(centre),
          sampler.humidity().compute(centre));
    }

    static Profile fromClimate(double anchor, double temperature, double humidity) {
      // Low shelves remain in the lower rock bands of the surface rules.
      if (anchor >= 112 && temperature >= 0.50 && humidity <= 0.22) return ARID;
      if (temperature >= -0.12 && humidity >= 0.35) return MARSH;
      if (temperature <= -0.38) return SNOWY;
      return humidity >= 0.20 ? WOODED : GRASSLAND;
    }
  }

  Holder<Biome> select(Holder<Biome> current, Profile profile) {
    boolean ocean = current.is(BiomeTags.IS_OCEAN);
    return switch (profile) {
      case ARID ->
          ocean || current.unwrapKey().filter(ARID_TRANSITIONS::contains).isPresent()
              ? desert
              : current;
      case MARSH ->
          ocean || current.is(Biomes.PLAINS) || current.is(Biomes.RIVER) || current.is(Biomes.SWAMP)
              ? marsh
              : current;
      case SNOWY -> ocean || current.is(Biomes.PLAINS) || current.is(PRAIRIE) ? snowy : current;
      case WOODED -> ocean ? forest : current;
      case GRASSLAND -> ocean ? plains : current;
    };
  }

  static boolean insideZone(IslandLayout.LargeIsland island, int x, int z) {
    double dx = (x - island.centerX()) / (0.78 * island.radiusX());
    double dz = (z - island.centerZ()) / (0.55 * island.radiusZ());
    return dx * dx + dz * dz + 0.30 * IslandLayout.noise(island.key(), x, z, 146, 101) < 1;
  }

  private static ResourceKey<Biome> nature(String path) {
    return ResourceKey.create(
        Registries.BIOME, ResourceLocation.fromNamespaceAndPath("natures_spirit", path));
  }
}
