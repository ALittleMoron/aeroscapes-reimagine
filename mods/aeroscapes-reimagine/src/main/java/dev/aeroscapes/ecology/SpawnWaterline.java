package dev.aeroscapes.ecology;

import com.ninni.spawn.registry.SpawnTags;
import dev.aeroscapes.aquatic.AquaticWater;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Spawn's dry native islands sit above deep basin floors rather than the original shore envelope.
 */
public final class SpawnWaterline {
  private SpawnWaterline() {}

  @SuppressWarnings("deprecation") // Match the exact provider's LevelAccessor sea-level contract.
  public static int at(LevelAccessor level, BlockPos position) {
    int nativeSea = level.getSeaLevel();
    if (!(level instanceof ServerLevelAccessor server)) return nativeSea;
    int local = AquaticWater.seaLevel(nativeSea, server, position);
    if (local != nativeSea) return local + 1;
    IslandTerrain terrain = IslandTerrain.from(server.getLevel().getChunkSource().randomState());
    if (terrain == null || !level.getBiome(position).is(SpawnTags.IS_ISLAND)) return nativeSea;
    var basin = terrain.basin(position.getX(), position.getZ());
    return basin != null
            && basin.water(position.getX(), basin.waterline(), position.getZ())
            && position.getY() >= basin.floor(position.getX(), position.getZ())
            && position.getY() <= basin.waterline() + 64
        ? basin.waterline() + 1
        : nativeSea;
  }
}
