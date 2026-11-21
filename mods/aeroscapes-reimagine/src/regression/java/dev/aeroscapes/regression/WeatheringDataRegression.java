package dev.aeroscapes.regression;

import com.ordana.immersive_weathering.blocks.mossy.MossSpreader;
import com.ordana.immersive_weathering.data.block_growths.BlockGrowthHandler;
import com.ordana.immersive_weathering.data.block_growths.TickSource;
import com.ordana.immersive_weathering.data.block_growths.growths.ConfigurableBlockGrowth;
import com.ordana.immersive_weathering.reg.ModBlocks;
import com.ordana.immersive_weathering.reg.ModTags;
import com.ordana.immersive_weathering.util.PatchSpreader.WeatheringAgent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

final class WeatheringDataRegression {
  private static final BlockPos SANDSTONE_POS = new BlockPos(8, 300, 4);
  private static final int SAMPLE_ATTEMPTS = 8_000;

  private WeatheringDataRegression() {}

  static void verify(ServerLevel level) {
    verifyMossSources(level);
    verifySandstoneErosion(level);
  }

  private static void verifyMossSources(ServerLevel level) {
    var lichen = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("galosphere:lichen_moss"));
    require(lichen != Blocks.AIR, "Galosphere lichen moss is registered");
    for (BlockState state : lichen.getStateDefinition().getPossibleStates()) {
      require(state.is(ModTags.MOSS_SOURCE), "Every lichen state is in the loaded moss source tag");
      require(
          MossSpreader.INSTANCE.getHighInfluenceWeatheringEffect(state, level, SANDSTONE_POS)
              == WeatheringAgent.WEATHER,
          "Native moss spreading recognizes Galosphere lichen");
    }
    require(
        Blocks.MOSS_BLOCK.defaultBlockState().is(ModTags.MOSS_SOURCE),
        "The lichen tag preserves the native moss source");
  }

  private static void verifySandstoneErosion(ServerLevel level) {
    var sandstoneBlock =
        BuiltInRegistries.BLOCK.get(ResourceLocation.parse("natures_spirit:pink_sandstone"));
    require(sandstoneBlock != Blocks.AIR, "Nature's Spirit pink sandstone is registered");
    BlockState sandstone = sandstoneBlock.defaultBlockState();
    require(sandstone.isRandomlyTicking(), "Pink sandstone opts into the native growth tick");
    var redSand = ModBlocks.RED_SAND_LAYER_BLOCK.get();
    var rule =
        BlockGrowthHandler.getBlockGrowths(TickSource.BLOCK_TICK, sandstoneBlock)
            .orElseThrow()
            .stream()
            .filter(ConfigurableBlockGrowth.class::isInstance)
            .map(ConfigurableBlockGrowth.class::cast)
            .filter(growth -> growth.getPossibleBlocks().contains(redSand))
            .findFirst()
            .orElseThrow();

    BlockPos floor = SANDSTONE_POS.below(2);
    BlockPos water = SANDSTONE_POS.east();
    BlockPos deposit = SANDSTONE_POS.below();
    BlockState flowing = Fluids.FLOWING_WATER.getFlowing(6, false).createLegacyBlock();
    require(
        flowing.getFluidState().getType() == Fluids.FLOWING_WATER, "Fixture uses flowing water");
    level.setBlockAndUpdate(SANDSTONE_POS, sandstone);
    level.setBlockAndUpdate(floor, Blocks.DIRT.defaultBlockState());
    level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
    level.setBlockAndUpdate(deposit, Blocks.AIR.defaultBlockState());
    runGrowth(level, sandstone, rule, deposit);
    require(level.getBlockState(deposit).isAir(), "Still water leaves pink sandstone intact");

    level.setBlockAndUpdate(water, flowing);
    level.setBlockAndUpdate(floor, Blocks.AIR.defaultBlockState());
    runGrowth(level, sandstone, rule, deposit);
    require(level.getBlockState(deposit).isAir(), "A loose layer requires sturdy support");

    level.setBlockAndUpdate(floor, Blocks.DIRT.defaultBlockState());
    BlockState plant = Blocks.SHORT_GRASS.defaultBlockState();
    level.setBlockAndUpdate(deposit, plant);
    runGrowth(level, sandstone, rule, deposit);
    require(level.getBlockState(deposit) == plant, "Erosion preserves occupied surfaces");

    level.setBlockAndUpdate(deposit, Blocks.CAVE_AIR.defaultBlockState());
    runGrowth(level, sandstone, rule, deposit);
    require(
        level.getBlockState(deposit) == redSand.defaultBlockState(),
        "Flowing water deposits one native loose red sand layer on empty supported ground");
    level.destroyBlock(deposit, false);
    require(
        level.getBlockState(SANDSTONE_POS) == sandstone
            && level.getBlockState(floor).is(Blocks.DIRT)
            && level.getBlockState(deposit).isAir(),
        "Removing the layer exposes the original sandstone and dirt");
  }

  private static void runGrowth(
      ServerLevel level, BlockState sandstone, ConfigurableBlockGrowth rule, BlockPos deposit) {
    BlockState initial = level.getBlockState(deposit);
    for (int attempt = 0;
        attempt < SAMPLE_ATTEMPTS && level.getBlockState(deposit) == initial;
        attempt++) {
      rule.tryGrowing(SANDSTONE_POS, sandstone, level, () -> level.getBiome(SANDSTONE_POS));
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
