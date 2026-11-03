package dev.aeroscapes.aquatic;

import dev.aeroscapes.worldgen.IslandLayout;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Native marine decoration gets finite habitats inside the existing sealed terrain. */
public final class AquaticHabitats {
  public enum Habitat {
    COLD_TRENCH,
    CORAL_REEF,
    DEEP_CORAL_REEF,
    DEEP_WARM_OCEAN,
    FROZEN_TRENCH,
    LUKEWARM_TRENCH,
    RED_MEADOW,
    SEAGRASS_BED,
    SULFURIC_CAVES,
    TIDE_POOLS,
    TRENCH,
    TROPICAL_DEEP_CORAL_REEF,
    TROPICAL_RIVER,
    WARM_TRENCH
  }

  private final Map<Habitat, Holder<Biome>> biomes;

  public AquaticHabitats(List<Holder<Biome>> roster) {
    Map<Habitat, Holder<Biome>> selected = new EnumMap<>(Habitat.class);
    for (Holder<Biome> biome : roster) {
      var key = biome.unwrapKey().orElseThrow().location();
      if (!"hybrid_aquatic".equals(key.getNamespace()))
        throw new IllegalArgumentException("Foreign aquatic habitat " + key);
      Habitat habitat = Habitat.valueOf(key.getPath().toUpperCase(Locale.ROOT));
      if (selected.put(habitat, biome) != null)
        throw new IllegalArgumentException("Duplicate aquatic habitat " + key);
    }
    if (!selected.isEmpty() && selected.size() != Habitat.values().length)
      throw new IllegalArgumentException("Aquatic habitats require all 14 pinned biomes");
    biomes = Map.copyOf(selected);
  }

  public Holder<Biome> select(
      Holder<Biome> original, IslandLayout.Basin basin, int x, int y, int z, double temperature) {
    if (biomes.isEmpty()) return original;
    double floor = basin.floor(x, z);
    int depth = basin.waterline() - y;
    double patch = patch(basin, x, z);
    if (floor >= basin.waterline() - 2)
      return patch < -0.28 ? biomes.get(Habitat.TIDE_POOLS) : original;
    if (temperature >= 0.5 && floor >= basin.waterline() - 12 && patch >= 0.04 && patch < 0.15)
      return biomes.get(Habitat.TROPICAL_RIVER);
    if (depth >= 48 && floor < basin.waterline() - 55) {
      if (patch < -0.28 && y <= floor + 8) return biomes.get(Habitat.SULFURIC_CAVES);
      Habitat trench =
          temperature < -0.45
              ? Habitat.FROZEN_TRENCH
              : temperature < -0.15
                  ? Habitat.COLD_TRENCH
                  : temperature < 0.2
                      ? Habitat.TRENCH
                      : temperature < 0.5 ? Habitat.LUKEWARM_TRENCH : Habitat.WARM_TRENCH;
      return biomes.get(trench);
    }
    if (temperature >= 0.2) {
      if (depth >= 24)
        return biomes.get(
            temperature >= 0.5 ? Habitat.TROPICAL_DEEP_CORAL_REEF : Habitat.DEEP_CORAL_REEF);
      return patch < -0.28 ? biomes.get(Habitat.DEEP_WARM_OCEAN) : biomes.get(Habitat.CORAL_REEF);
    }
    return patch < -0.15
        ? biomes.get(Habitat.RED_MEADOW)
        : patch < 0.04 ? biomes.get(Habitat.SEAGRASS_BED) : original;
  }

  private static double patch(IslandLayout.Basin basin, int x, int z) {
    long key = basin.key();
    double warpedX = x + 32 * IslandLayout.noise(key, x, z, 173, 101);
    double warpedZ = z + 32 * IslandLayout.noise(key, x, z, 173, 102);
    return 0.8 * IslandLayout.noise(key, warpedX, warpedZ, 96, 103)
        + 0.2 * IslandLayout.noise(key, warpedX, warpedZ, 41, 104);
  }
}
