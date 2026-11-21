package dev.aeroscapes.weathering;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class PinkSandstoneErosion {
  private static final ResourceKey<Block> PINK_SANDSTONE =
      ResourceKey.create(Registries.BLOCK, ResourceLocation.parse("natures_spirit:pink_sandstone"));

  private PinkSandstoneErosion() {}

  public static boolean randomTicks(BlockState state) {
    return state.is(PINK_SANDSTONE);
  }
}
