package dev.aeroscapes.weathering;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class SootSources {
  private static final ResourceKey<Block> STOVE =
      ResourceKey.create(Registries.BLOCK, ResourceLocation.parse("farmersdelight:stove"));
  private static final ResourceKey<Block> BRAZIER =
      ResourceKey.create(
          Registries.BLOCK, ResourceLocation.parse("wetland_whimsy:lemonstone_brazier"));

  private SootSources() {}

  public static boolean isSource(BlockState state) {
    return state.is(STOVE) || state.is(BRAZIER);
  }

  public static boolean randomTicks(BlockState state) {
    return isSource(state)
        && state.getOptionalValue(BlockStateProperties.LIT).orElse(false)
        && !state.getOptionalValue(BlockStateProperties.WATERLOGGED).orElse(false);
  }
}
