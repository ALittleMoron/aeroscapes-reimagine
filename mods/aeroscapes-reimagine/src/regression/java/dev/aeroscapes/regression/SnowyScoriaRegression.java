package dev.aeroscapes.regression;

import com.ordana.immersive_weathering.blocks.snowy.Snowy;
import com.ordana.immersive_weathering.data.block_growths.BlockGrowthHandler;
import com.ordana.immersive_weathering.data.block_growths.TickSource;
import com.ordana.immersive_weathering.data.block_growths.growths.builtin.SnowGrowth;
import com.ordana.immersive_weathering.reg.ModTags;
import com.ordana.immersive_weathering.util.TemperatureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Runs the native snow lifecycle against the registered full scoria pair. */
final class SnowyScoriaRegression {
  private SnowyScoriaRegression() {}

  static void verify(ServerLevel level) {
    BlockState plain = state("create:cut_scoria_bricks");
    BlockState coated = state("aeroscapes_reimagine:snowy_cut_scoria_bricks");
    require(plain.is(ModTags.SNOWABLE) && plain.is(ModTags.DOUBLE_SNOWABLE), "Scoria snow tags");
    require(coated.is(ModTags.SNOWY) && coated.is(BlockTags.MINEABLE_WITH_PICKAXE), "Coating tags");
    require(coated.requiresCorrectToolForDrops(), "A coated block still requires a pickaxe");
    require(Snowy.getSnowy(plain).orElseThrow() == coated, "Native scoria snow conversion");
    require(Snowy.getUnSnowy(coated).orElseThrow() == plain, "Exact reverse scoria conversion");
    BlockPos position = new BlockPos(8, level.getMaxBuildHeight() - 8, 24);
    var frozen =
        level
            .registryAccess()
            .registryOrThrow(Registries.BIOME)
            .getHolderOrThrow(Biomes.FROZEN_PEAKS);
    var temperate =
        level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.DESERT);
    var growth =
        BlockGrowthHandler.getBlockGrowths(TickSource.SNOW, plain.getBlock()).orElseThrow().stream()
            .filter(SnowGrowth.class::isInstance)
            .findFirst()
            .orElseThrow();
    level.setBlockAndUpdate(position, plain);
    for (int attempt = 0; attempt < 128; attempt++) {
      growth.tryGrowing(position, plain, level, () -> temperate);
    }
    require(level.getBlockState(position) == plain, "Warm precipitation leaves scoria plain");
    for (int attempt = 0; attempt < 128 && level.getBlockState(position) == plain; attempt++) {
      growth.tryGrowing(position, plain, level, () -> frozen);
    }
    require(level.getBlockState(position) == coated, "Native snow rule coats Create scoria");
    shovel(level, position, plain, coated);
    loot(level, position, plain, coated);
    thaw(level, position, plain, coated);
  }

  private static void shovel(
      ServerLevel level, BlockPos position, BlockState plain, BlockState coated) {
    var player = FakePlayerFactory.getMinecraft(level);
    var main = InteractionHand.MAIN_HAND;
    var off = InteractionHand.OFF_HAND;
    ItemStack previousMain = player.getItemInHand(main);
    ItemStack previousOff = player.getItemInHand(off);
    ItemStack shovel = new ItemStack(Items.IRON_SHOVEL);
    var hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.NORTH, position, false);
    try {
      player.setItemInHand(main, new ItemStack(Items.IRON_PICKAXE));
      player.setItemInHand(off, shovel);
      require(
          !coated.useItemOn(player.getItemInHand(main), level, player, main, hit).consumesAction()
              && level.getBlockState(position) == coated
              && shovel.getDamageValue() == 0,
          "The clicked non-shovel hand leaves the coating intact");
      int snowballs =
          WeatheringDrops.spawnedBy(
              level,
              position,
              Items.SNOWBALL,
              () ->
                  require(
                      coated.useItemOn(shovel, level, player, off, hit).consumesAction()
                          && level.getBlockState(position) == plain
                          && shovel.getDamageValue() == 1,
                      "The clicked shovel removes the coating and takes one damage"));
      require(snowballs == 1, "Shovel returns one snowball, observed " + snowballs);
    } finally {
      player.setItemInHand(main, previousMain);
      player.setItemInHand(off, previousOff);
    }
  }

  private static void loot(
      ServerLevel level, BlockPos position, BlockState plain, BlockState coated) {
    ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
    var drops = Block.getDrops(coated, level, position, null, null, pickaxe);
    require(
        drops.size() == 2
            && count(drops, plain.getBlock().asItem()) == 1
            && count(drops, Items.SNOWBALL) == 1,
        "Regular mining drops one scoria brick block and one snowball");
    var silk =
        level
            .registryAccess()
            .registryOrThrow(Registries.ENCHANTMENT)
            .getHolderOrThrow(Enchantments.SILK_TOUCH);
    pickaxe.enchant(silk, 1);
    var silken = Block.getDrops(coated, level, position, null, null, pickaxe);
    require(
        silken.size() == 1 && count(silken, coated.getBlock().asItem()) == 1,
        "Silk Touch preserves the coated block and its snow");
  }

  private static int count(java.util.List<ItemStack> drops, Item item) {
    return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
  }

  private static void thaw(
      ServerLevel level, BlockPos position, BlockState plain, BlockState coated) {
    require(TemperatureManager.canSnowMelt(position, level), "The fixture needs a warm thaw site");
    level.setBlockAndUpdate(position, coated);
    level.setBlockAndUpdate(position.above(), Blocks.SNOW.defaultBlockState());
    coated.randomTick(level, position, level.getRandom());
    require(level.getBlockState(position) == coated, "Native thaw respects snow above the coating");
    level.removeBlock(position.above(), false);
    coated.randomTick(level, position, level.getRandom());
    require(level.getBlockState(position) == plain, "Native thaw restores the exact scoria block");
    level.setBlockAndUpdate(position, coated);
    level.setBlockAndUpdate(position.east(), Blocks.WATER.defaultBlockState());
    require(level.getBlockState(position) == plain, "Native fluid contact washes off scoria snow");
    level.removeBlock(position.east(), false);
    level.removeBlock(position, false);
  }

  private static BlockState state(String name) {
    return BuiltInRegistries.BLOCK
        .getOptional(ResourceLocation.parse(name))
        .orElseThrow()
        .defaultBlockState();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
