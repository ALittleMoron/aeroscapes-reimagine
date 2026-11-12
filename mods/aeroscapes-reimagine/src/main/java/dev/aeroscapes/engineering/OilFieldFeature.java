package dev.aeroscapes.engineering;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Writes only within its origin chunk and never touches main-thread reserve storage. */
public final class OilFieldFeature extends Feature<NoneFeatureConfiguration> {
  public OilFieldFeature(Codec<NoneFeatureConfiguration> codec) {
    super(codec);
  }

  @Override
  public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
    WorldGenLevel level = context.level();
    ChunkPos chunk = new ChunkPos(context.origin());
    RandomSource random = OilFields.random(level.getSeed(), chunk);
    if (random.nextInt(8) != 0) return false;
    boolean placed = false;
    for (int attempt = 0; attempt < 24; attempt++) {
      int x = chunk.getMinBlockX() + 2 + random.nextInt(12);
      int z = chunk.getMinBlockZ() + 2 + random.nextInt(12);
      for (int y = level.getMinBuildHeight() + 8; y < level.getMaxBuildHeight() - 8; y++) {
        BlockPos origin = new BlockPos(x, y, z);
        if (!level.getBlockState(origin).is(BlockTags.BASE_STONE_OVERWORLD)
            || !level.getBlockState(origin.below()).isAir()
            || !level.getBlockState(origin.above(3)).is(BlockTags.BASE_STONE_OVERWORLD)
            || !level.getBlockState(origin.above(6)).is(BlockTags.BASE_STONE_OVERWORLD)) continue;
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
          for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
            BlockPos marker = origin.offset(offsetX, 0, offsetZ);
            var stone = level.getBlockState(marker);
            var host = OilRockBlock.Host.of(stone);
            if (stone.is(BlockTags.BASE_STONE_OVERWORLD) && host != null) {
              level.setBlock(
                  marker,
                  OilFields.OIL_BEARING_ROCK
                      .get()
                      .defaultBlockState()
                      .setValue(OilRockBlock.HOST, host),
                  2);
              placed = true;
            }
          }
        }
        break;
      }
    }
    return placed;
  }
}
