package dev.aeroscapes.sky;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;

/** Bounded inspection of already loaded columns; an encounter never requests terrain. */
public final class WhaleHabitats {
  private WhaleHabitats() {}

  public static WhaleVariety at(ServerLevel level, BlockPos position) {
    return at(level, position, null);
  }

  /**
   * Selects the form from the ground below the whale. Over open void, where no ground lies below,
   * the ground the encountering player stands on supplies the habitat instead of the void biome.
   */
  public static WhaleVariety at(ServerLevel level, BlockPos position, BlockPos fallbackGround) {
    if (!level.isLoaded(position)) return WhaleVariety.STONE;
    var surface = surfaceBelow(level, position);
    if (surface == null && fallbackGround != null && level.isLoaded(fallbackGround)) {
      var ground = level.getBlockState(fallbackGround);
      if (ground.canOcclude() || !ground.getFluidState().isEmpty()) surface = fallbackGround;
    }
    if (surface != null && lavaLake(level, surface)) return WhaleVariety.LAVA;
    if (position.getY() >= 320) return WhaleVariety.CLOUD;
    // High open sky is cold; over ground the biome below decides, so high islands keep their forms.
    if (position.getY() >= 256 && surface == null) return WhaleVariety.SNOW;
    var habitat = surface == null ? position : surface;
    var biome = level.getBiome(habitat);
    var biomeId = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
    var state = level.getBlockState(habitat);
    var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    if (blockId.contains("basalt") || biomeId.contains("basalt")) return WhaleVariety.BASALT;
    if (blockId.contains("dripstone") || biomeId.contains("dripstone"))
      return WhaleVariety.DRIPSTONE;
    if (blockId.contains("chalk")) return WhaleVariety.CHALK;
    if (blockId.contains("granite") || biomeId.contains("granite")) return WhaleVariety.GRANITE;
    if (biomeId.contains("mushroom")
        || state.is(Blocks.MYCELIUM)
        || state.is(Blocks.RED_MUSHROOM_BLOCK)
        || state.is(Blocks.BROWN_MUSHROOM_BLOCK)) return WhaleVariety.FUNGAL;
    if (biome.is(BiomeTags.IS_OCEAN)) return WhaleVariety.REEF;
    if (biome.value().coldEnoughToSnow(habitat)) return WhaleVariety.SNOW;
    if (biome.is(BiomeTags.IS_BADLANDS) || biome.value().getBaseTemperature() >= 1.5F)
      return WhaleVariety.SANDSTONE;
    if (position.getY() < 32 || biomeId.contains("lush")) return WhaleVariety.MOSS;
    if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_JUNGLE)) return WhaleVariety.ROOTED;
    return WhaleVariety.STONE;
  }

  private static BlockPos surfaceBelow(ServerLevel level, BlockPos position) {
    var cursor = position.mutable().move(0, -8, 0);
    int bottom = Math.max(level.getMinBuildHeight(), position.getY() - 192);
    for (; cursor.getY() >= bottom; cursor.move(0, -1, 0)) {
      if (!level.isLoaded(cursor)) return null;
      var state = level.getBlockState(cursor);
      if (state.canOcclude() || !state.getFluidState().isEmpty()) return cursor.immutable();
    }
    return null;
  }

  public static boolean lavaLake(ServerLevel level, BlockPos center) {
    for (int x = -2; x <= 2; x++) {
      for (int z = -2; z <= 2; z++) {
        var position = center.offset(x, 0, z);
        if (!level.isLoaded(position)) return false;
        var fluid = level.getFluidState(position);
        if (!fluid.is(FluidTags.LAVA) || !fluid.isSource()) return false;
        if (!level.getBlockState(position.above()).isAir()) return false;
      }
    }
    return true;
  }
}
