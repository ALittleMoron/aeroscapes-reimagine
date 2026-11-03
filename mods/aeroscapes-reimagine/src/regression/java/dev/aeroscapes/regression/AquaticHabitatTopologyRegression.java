package dev.aeroscapes.regression;

import dev.aeroscapes.aquatic.AquaticHabitats;
import dev.aeroscapes.worldgen.IslandLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Actual selector continuity and seed variation, isolated from climate and seabed thresholds. */
public final class AquaticHabitatTopologyRegression {
  private static final int SIDE = 256;

  private AquaticHabitatTopologyRegression() {}

  public record Measurement(
      long key,
      double temperature,
      double seedDifference,
      double neighborAgreement,
      double largestBoundaryResidue) {}

  public static List<Measurement> run(AquaticHabitats habitats, Holder<Biome> original) {
    List<Measurement> measurements = new ArrayList<>();
    for (long key : new long[] {1, 42, 777, -1}) {
      for (double temperature : new double[] {0, 0.7}) {
        measurements.add(measure(habitats, original, key, temperature));
      }
    }
    return List.copyOf(measurements);
  }

  private static Measurement measure(
      AquaticHabitats habitats, Holder<Biome> original, long key, double temperature) {
    // The floor stays between Y50 and Y110, excluding shore and river branches at Y160.
    var basin = new IslandLayout.Basin(0, 0, 160, 100000, 100000, 80, key);
    var other = new IslandLayout.Basin(0, 0, 160, 100000, 100000, 80, key + 1);
    List<Holder<Biome>> selected = new ArrayList<>(SIDE * SIDE);
    Map<Holder<Biome>, Integer> counts = new HashMap<>();
    int differentSeed = 0;
    for (int x = -512; x < 512; x += 4) {
      for (int z = -512; z < 512; z += 4) {
        Holder<Biome> biome = habitats.select(original, basin, x, 160, z, temperature);
        require(
            biome.equals(habitats.select(original, basin, x, 160, z, temperature)),
            "Aquatic habitat selection is not deterministic");
        if (!biome.equals(habitats.select(original, other, x, 160, z, temperature)))
          differentSeed++;
        selected.add(biome);
        counts.merge(biome, 1, Integer::sum);
      }
    }
    require(
        counts.size() == (temperature >= 0.2 ? 2 : 3),
        "Aquatic patch selection lost its shallow habitat alternatives: " + counts);
    for (int count : counts.values())
      require(count > selected.size() / 50, "Aquatic patch alternative became a negligible speck");
    require(
        differentSeed > selected.size() / 20,
        "Aquatic patches ignore their seed: key=" + key + " temperature=" + temperature);

    int sameAdjacent = 0;
    int[] residues = new int[12];
    for (int x = 0; x < SIDE; x++) {
      for (int z = 0; z < SIDE; z++) {
        Holder<Biome> biome = selected.get(x * SIDE + z);
        if (x + 1 < SIDE) {
          if (biome.equals(selected.get((x + 1) * SIDE + z))) sameAdjacent++;
          else residues[Math.floorMod(-512 + (x + 1) * 4, 48) / 4]++;
        }
        if (z + 1 < SIDE) {
          if (biome.equals(selected.get(x * SIDE + z + 1))) sameAdjacent++;
          else residues[Math.floorMod(-512 + (z + 1) * 4, 48) / 4]++;
        }
      }
    }
    int adjacent = 2 * SIDE * (SIDE - 1);
    require(
        sameAdjacent > adjacent * 0.9,
        "Aquatic habitats scatter between adjacent quart cells: key=" + key);
    int transitions = adjacent - sameAdjacent;
    int occupied = 0;
    int largest = 0;
    for (int residue : residues) {
      if (residue > 0) occupied++;
      largest = Math.max(largest, residue);
    }
    require(
        occupied >= 6 && largest < transitions * 0.35,
        "Aquatic boundaries follow a regular 48-block lattice: key="
            + key
            + " temperature="
            + temperature);
    return new Measurement(
        key,
        temperature,
        (double) differentSeed / selected.size(),
        (double) sameAdjacent / adjacent,
        (double) largest / transitions);
  }

  private static void require(boolean accepted, String failure) {
    if (!accepted) throw new IllegalStateException(failure);
  }
}
