package dev.aeroscapes.ecology;

import dev.aeroscapes.worldgen.IslandLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;

/** The three native marine climates apply to physical basin water and shores. */
public final class SpawnHabitats {
  private final Map<String, Holder<Biome>> biomes;

  public SpawnHabitats(List<Holder<Biome>> roster) {
    Map<String, Holder<Biome>> selected = new HashMap<>();
    for (var biome : roster) {
      var id = biome.unwrapKey().orElseThrow().location();
      if (!id.getNamespace().equals("spawn") || selected.put(id.getPath(), biome) != null)
        throw new IllegalArgumentException("Foreign or duplicate Spawn marine habitat " + id);
    }
    if (!selected.isEmpty()
        && !selected.keySet().equals(Set.of("deep_warm_ocean", "rocky_shore", "seagrass_meadow")))
      throw new IllegalArgumentException("Spawn marine habitats require all three pinned biomes");
    biomes = Map.copyOf(selected);
  }

  public Holder<Biome> select(
      IslandLayout.Basin basin, int x, int y, int z, Climate.Sampler sampler, double temperature) {
    if (biomes.isEmpty()) return null;
    var point = new SinglePointContext(x, y, z);
    double continentalness = sampler.continentalness().compute(point);
    double floor = basin.floor(x, z);
    if (floor >= basin.waterline()) {
      double humidity = sampler.humidity().compute(point);
      return temperature >= 0.2 && humidity >= -0.1 ? biomes.get("rocky_shore") : null;
    }
    if (temperature >= 0.55 && continentalness >= -1.05 && continentalness <= -0.455)
      return biomes.get("deep_warm_ocean");
    if (temperature >= 0.24
        && temperature <= 0.35
        && continentalness >= -0.355
        && continentalness <= -0.17) return biomes.get("seagrass_meadow");
    return null;
  }
}
