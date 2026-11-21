package dev.aeroscapes.regression;

import com.ordana.immersive_weathering.data.block_growths.BlockGrowthHandler;
import com.ordana.immersive_weathering.data.block_growths.TickSource;
import com.ordana.immersive_weathering.data.block_growths.growths.builtin.CampfireSootGrowth;
import com.ordana.immersive_weathering.reg.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class SootSourcesRegression {
  private SootSourcesRegression() {}

  static void verify(ServerLevel level) {
    Block stove = block("farmersdelight:stove");
    Block brazier = block("wetland_whimsy:lemonstone_brazier");
    Block ash = block("supplementaries:ash");
    var sourcePos = new BlockPos(8, level.getMaxBuildHeight() - 12, 8);
    var sootPos = sourcePos.above(2);
    var ceiling = sootPos.above();
    require(
        level.getBlockState(sourcePos).isAir()
            && level.getBlockState(sourcePos.above()).isAir()
            && level.getBlockState(sootPos).isAir()
            && level.getBlockState(ceiling).isAir(),
        "Soot fixture has clear space beneath its ceiling");
    level.setBlockAndUpdate(ceiling, Blocks.STONE.defaultBlockState());
    try {
      for (Block source : List.of(Blocks.CAMPFIRE, stove, brazier)) {
        var growth = BlockGrowthHandler.getBlockGrowths(TickSource.BLOCK_TICK, source);
        require(
            growth.orElseThrow().stream().filter(CampfireSootGrowth.class::isInstance).count() == 1,
            "A single native campfire soot rule owns " + source);
        var lit = source.defaultBlockState().setValue(BlockStateProperties.LIT, true);
        var unlit = lit.setValue(BlockStateProperties.LIT, false);
        if (source != Blocks.CAMPFIRE) {
          require(lit.isRandomlyTicking(), "Lit soot source schedules natural growth " + source);
          require(!unlit.isRandomlyTicking(), "Unlit soot source avoids random ticks " + source);
        }
        checkGrowth(level, sourcePos, sootPos, unlit, false);
        checkGrowth(level, sourcePos, sootPos, lit, true);
      }
      var wet =
          brazier
              .defaultBlockState()
              .setValue(BlockStateProperties.LIT, true)
              .setValue(BlockStateProperties.WATERLOGGED, true);
      require(!wet.isRandomlyTicking(), "Waterlogged brazier avoids soot ticks");
      checkGrowth(level, sourcePos, sootPos, wet, false);
      var litStove = stove.defaultBlockState().setValue(BlockStateProperties.LIT, true);
      level.setBlockAndUpdate(sourcePos, litStove);
      level.setBlockAndUpdate(sootPos, ash.defaultBlockState());
      BlockGrowthHandler.tickBlock(TickSource.BLOCK_TICK, litStove, level, sourcePos);
      require(level.getBlockState(sootPos).is(ash), "Soot leaves existing ash layers intact");
    } finally {
      level.removeBlock(sourcePos, false);
      level.removeBlock(sootPos, false);
      level.removeBlock(ceiling, false);
    }
  }

  private static void checkGrowth(
      ServerLevel level, BlockPos sourcePos, BlockPos sootPos, BlockState source, boolean emits) {
    level.setBlockAndUpdate(sourcePos, source);
    level.removeBlock(sootPos, false);
    BlockGrowthHandler.tickBlock(TickSource.BLOCK_TICK, source, level, sourcePos);
    BlockState deposit = level.getBlockState(sootPos);
    if (emits)
      require(
          deposit.is(ModBlocks.SOOT.get()) && deposit.getValue(BlockStateProperties.UP),
          "Native soot attaches to the roof above " + source);
    else require(deposit.isAir(), "Soot leaves the roof clear above " + source);
    require(level.getBlockState(sourcePos).is(source.getBlock()), "Growth preserves source block");
  }

  private static Block block(String id) {
    Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
    require(block != Blocks.AIR, "Installed block " + id);
    return block;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
