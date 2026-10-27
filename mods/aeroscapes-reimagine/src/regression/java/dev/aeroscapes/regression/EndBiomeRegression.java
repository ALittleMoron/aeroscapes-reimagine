package dev.aeroscapes.regression;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biomes;
import terrablender.api.EndBiomeRegistry;
import terrablender.core.TerraBlender;
import terrablender.worldgen.noise.LayeredNoiseUtil;

/** Exercises repeated native area initialization and preserves the legitimate edge-biome call. */
final class EndBiomeRegression {
  private EndBiomeRegression() {}

  static void verify(ServerLevel level) {
    int islandSize = TerraBlender.CONFIG.endIslandBiomeSize;
    int edgeSize = TerraBlender.CONFIG.endEdgeBiomeSize;
    if (islandSize != 2 || edgeSize != 3 || islandSize == edgeSize) {
      throw new IllegalStateException(
          "Pinned End compatibility requires island size 2 and edge size 3");
    }
    var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
    int small = biomes.getId(biomes.getHolderOrThrow(Biomes.SMALL_END_ISLANDS).value());
    int barrens = biomes.getId(biomes.getHolderOrThrow(Biomes.END_BARRENS).value());
    var edges = EndBiomeRegistry.getEdgeBiomes();
    var islands = EndBiomeRegistry.getIslandBiomes();
    var first =
        LayeredNoiseUtil.biomeArea(level.registryAccess(), level.getSeed(), islandSize, edges);
    var repeated =
        LayeredNoiseUtil.biomeArea(level.registryAccess(), level.getSeed(), islandSize, edges);
    var unchangedEdge =
        LayeredNoiseUtil.biomeArea(level.registryAccess(), level.getSeed(), edgeSize, edges);
    var correctIsland =
        LayeredNoiseUtil.biomeArea(level.registryAccess(), level.getSeed(), islandSize, islands);
    int[] coordinates = {-128, -17, 0, 23, 128};
    for (int x : coordinates) {
      for (int z : coordinates) {
        if (first.get(x, z) != small
            || repeated.get(x, z) != small
            || correctIsland.get(x, z) != small
            || unchangedEdge.get(x, z) != barrens) {
          throw new IllegalStateException("Native End area correction failed at " + x + ", " + z);
        }
      }
    }
  }
}
