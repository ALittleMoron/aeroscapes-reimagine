package dev.aeroscapes.regression;

import com.ordana.immersive_weathering.blocks.LayerBlock;
import com.ordana.immersive_weathering.blocks.LeafPileBlock;
import com.ordana.immersive_weathering.data.block_growths.BlockGrowthHandler;
import com.ordana.immersive_weathering.data.block_growths.TickSource;
import com.ordana.immersive_weathering.data.block_growths.growths.ConfigurableBlockGrowth;
import com.ordana.immersive_weathering.data.block_growths.growths.builtin.LeavesGrowth;
import com.ordana.immersive_weathering.entities.FallingLayerEntity;
import com.ordana.immersive_weathering.reg.ModBlocks;
import com.ordana.immersive_weathering.util.WeatheringHelper;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

final class WeatheringCareRegression {
  private static final BlockPos SAPLING = new BlockPos(8, 302, 8);
  private static final BlockPos DEPOSITS = new BlockPos(8, 302, 12);
  private static final ResourceLocation WILTED =
      ResourceLocation.parse("aeroscapes_reimagine:wilted_sapling");
  private static final BlockState MATURE_SPRUCE =
      Blocks.SPRUCE_SAPLING.defaultBlockState().setValue(BlockStateProperties.STAGE, 1);

  private WeatheringCareRegression() {}

  static void verify(ServerLevel level, boolean reload) {
    if (reload) {
      require(wilted(level, SAPLING), "Wilted spruce survives server restart");
      water(level, SAPLING, MATURE_SPRUCE);
      return;
    }
    weeds();
    wilt(level, SAPLING, MATURE_SPRUCE, false);
    BlockState oak = Blocks.OAK_SAPLING.defaultBlockState();
    wilt(level, SAPLING.east(), oak, true);
    water(level, SAPLING.east(), oak);
    wilt(
        level,
        SAPLING.east(2),
        Blocks.MANGROVE_PROPAGULE
            .defaultBlockState()
            .setValue(BlockStateProperties.WATERLOGGED, true),
        true);
    deposits(level);
  }

  private static void weeds() {
    var loaded = BlockGrowthHandler.getBlockGrowths(TickSource.CLEAR_SKY, Blocks.FARMLAND);
    var weedRules =
        loaded.orElseThrow().stream()
            .filter(ConfigurableBlockGrowth.class::isInstance)
            .map(ConfigurableBlockGrowth.class::cast)
            .filter(rule -> rule.getPossibleBlocks().contains(ModBlocks.WEEDS.get()))
            .toList();
    require(weedRules.size() == 2, "Both native weed rules remain loaded on farmland");
    Block tomato = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("farmersdelight:tomatoes"));
    require(tomato != Blocks.AIR, "Installed tomato crop");
    for (var rule : weedRules) {
      for (Block block :
          List.of(
              Blocks.AIR,
              Blocks.CAVE_AIR,
              Blocks.VOID_AIR,
              Blocks.WHEAT,
              tomato,
              Blocks.SHORT_GRASS,
              ModBlocks.WEEDS.get())) {
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
          boolean accepted = false;
          for (int trial = 0; trial < 128; trial++) {
            accepted |= rule.getTargetPredicate().test(state, RandomSource.create(trial * 104729L));
          }
          require(accepted == state.isAir(), "Weed growth vacancy for " + state);
        }
      }
      require(
          BlockGrowthHandler.getBlockGrowths(TickSource.CLEAR_SKY, Blocks.DIRT).stream()
              .noneMatch(growths -> growths.contains(rule)),
          "Weed rule has no bare-dirt owner");
    }
  }

  private static void wilt(
      ServerLevel level, BlockPos position, BlockState sapling, boolean nether) {
    level.setBlockAndUpdate(position.below(), Blocks.DIRT.defaultBlockState());
    level.setBlockAndUpdate(position, sapling);
    var biome =
        level
            .registryAccess()
            .registryOrThrow(Registries.BIOME)
            .getHolderOrThrow(nether ? Biomes.NETHER_WASTES : Biomes.DESERT);
    var change =
        BlockGrowthHandler.getBlockGrowths(TickSource.BLOCK_TICK, sapling.getBlock())
            .orElseThrow()
            .stream()
            .filter(ConfigurableBlockGrowth.class::isInstance)
            .map(ConfigurableBlockGrowth.class::cast)
            .filter(ConfigurableBlockGrowth::targetSelf)
            .filter(rule -> rule.getPossibleBlocks().contains(Blocks.DEAD_BUSH))
            .filter(
                rule ->
                    rule.getPositionTests().stream()
                        .allMatch(test -> test.test(() -> biome, position, level)))
            .findFirst()
            .orElseThrow();
    for (int tick = 0; tick < 40_000 && level.getBlockState(position) == sapling; tick++)
      change.tryGrowing(position, sapling, level, () -> biome);
    if (sapling.getFluidState().isEmpty()) {
      require(
          wilted(level, position),
          "Native " + biome.getRegisteredName() + " sapling withers at " + position);
    } else {
      require(level.getBlockState(position) == sapling, "Waterlogged propagules stay hydrated");
    }
  }

  private static boolean wilted(ServerLevel level, BlockPos position) {
    return BuiltInRegistries.BLOCK.getKey(level.getBlockState(position).getBlock()).equals(WILTED);
  }

  private static void water(ServerLevel level, BlockPos position, BlockState expected) {
    var player = FakePlayerFactory.getMinecraft(level);
    var hand = InteractionHand.MAIN_HAND;
    ItemStack previous = player.getItemInHand(hand);
    var hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
    try {
      for (var potion : List.of(Potions.AWKWARD, Potions.WATER)) {
        ItemStack bottle = PotionContents.createItemStack(Items.POTION, potion);
        player.setItemInHand(hand, bottle);
        boolean restored =
            level
                .getBlockState(position)
                .useItemOn(bottle, level, player, hand, hit)
                .consumesAction();
        if (potion.equals(Potions.WATER)) {
          require(
              restored && level.getBlockState(position) == expected,
              "Water restores exact sapling");
          require(
              player.getItemInHand(hand).is(Items.GLASS_BOTTLE),
              "Watering returns one glass bottle");
        } else {
          require(
              !restored && wilted(level, position) && player.getItemInHand(hand) == bottle,
              "Other potions leave the wilted sapling and bottle intact");
        }
      }
    } finally {
      player.setItemInHand(hand, previous);
    }
  }

  private static void deposits(ServerLevel level) {
    BlockState turf = Blocks.GRASS_BLOCK.defaultBlockState();
    BlockPos ground = DEPOSITS.below();
    BlockPos canopy = DEPOSITS.above(5);
    BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
    Block leafPile = WeatheringHelper.getFallenLeafPile(leaves).orElseThrow();
    BlockState decoration = Blocks.SHORT_GRASS.defaultBlockState();
    var growth = new LeavesGrowth("leaf_piles", null, List.of(TickSource.BLOCK_TICK), 1.0F);
    level.setBlockAndUpdate(ground, turf);
    level.setBlockAndUpdate(canopy, leaves);
    level.setBlockAndUpdate(DEPOSITS, decoration);
    growth.tryGrowing(canopy, leaves, level, () -> level.getBiome(canopy));
    require(level.getBlockState(DEPOSITS) == decoration, "Canopy leaf fall preserves short grass");
    BlockState sand = ModBlocks.SAND_LAYER_BLOCK.get().defaultBlockState();
    for (BlockState deposit : List.of(leafPile.defaultBlockState(), sand)) {
      BlockPos source = DEPOSITS.above(3);
      level.setBlockAndUpdate(source, deposit);
      FallingLayerEntity falling = FallingLayerEntity.fall(level, source, deposit);
      for (int tick = 0; tick < 40 && !falling.isRemoved(); tick++) falling.tick();
      require(
          falling.isRemoved() && level.getBlockState(DEPOSITS) == decoration,
          "Falling " + deposit + " preserves short grass");
    }
    level.removeBlock(DEPOSITS, false);
    growth.tryGrowing(canopy, leaves, level, () -> level.getBiome(canopy));
    require(
        level.getBlockState(DEPOSITS).is(leafPile), "Canopy leaf fall still lands on empty turf");
    BlockState decomposing =
        leafPile
            .defaultBlockState()
            .setValue(LeafPileBlock.AGE, 1)
            .setValue(LeafPileBlock.LAYERS, 8);
    for (int attempt = 0; attempt < 96; attempt++) {
      level.setBlockAndUpdate(DEPOSITS, decomposing);
      decomposing.randomTick(level, DEPOSITS, level.random);
      require(level.getBlockState(ground) == turf, "Composting leaf piles preserve turf");
    }
    level.setBlockAndUpdate(ground, Blocks.DIRT.defaultBlockState());
    for (int attempt = 0; attempt < 96 && level.getBlockState(ground).is(Blocks.DIRT); attempt++) {
      level.setBlockAndUpdate(DEPOSITS, decomposing);
      decomposing.randomTick(level, DEPOSITS, level.random);
    }
    require(
        level.getBlockState(ground).is(ModBlocks.LOAM.get()),
        "Composting still makes loam from dirt");
    level.setBlockAndUpdate(ground, turf);
    for (BlockState full : List.of(decomposing, sand.setValue(LayerBlock.LAYERS_8, 8))) {
      level.setBlockAndUpdate(DEPOSITS, full);
      turf.randomTick(level, ground, level.random);
      require(level.getBlockState(ground) == turf, "Full deposits preserve turf");
      level.destroyBlock(DEPOSITS, false);
      require(
          level.getBlockState(DEPOSITS).isAir() && level.getBlockState(ground) == turf,
          "Removal reveals the original turf");
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
