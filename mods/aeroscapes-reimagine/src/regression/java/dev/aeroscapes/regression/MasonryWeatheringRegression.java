package dev.aeroscapes.regression;

import com.ordana.immersive_weathering.blocks.cracked.Crackable;
import com.ordana.immersive_weathering.blocks.mossy.Mossable;
import com.ordana.immersive_weathering.events.ModEvents;
import com.ordana.immersive_weathering.reg.ModItems;
import com.ordana.immersive_weathering.reg.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

final class MasonryWeatheringRegression {
  private static final BlockPos FIXTURE = new BlockPos(8, 303, 8);
  private static final String TRAVERTINE_SLAB = "natures_spirit:cobbled_travertine_slab";
  private static final String MOSSY_SLAB = "natures_spirit:mossy_cobbled_travertine_slab";
  private static final String CRACKED_TILES = "natures_spirit:cracked_chert_tiles";

  private MasonryWeatheringRegression() {}

  static void verify(ServerLevel level, boolean reload) {
    BlockState cleanSlab = slab(block(TRAVERTINE_SLAB));
    BlockState mossySlab = slab(block(MOSSY_SLAB));
    if (reload) {
      require(level.getBlockState(FIXTURE) == mossySlab, "Saved wet mossy slab retains its state");
      require(
          level.getBlockState(FIXTURE.east()).is(block(CRACKED_TILES)),
          "Saved cracked tiles persist");
      return;
    }
    require(
        level.getBlockState(FIXTURE) == cleanSlab, "Pre-expansion save retains the exact wet slab");
    require(
        level.getBlockState(FIXTURE.east()).is(block("natures_spirit:chert_tiles")),
        "Pre-expansion save retains chert tiles");
    for (String shape : new String[] {"", "_slab", "_stairs", "_wall"}) {
      verifyMoss("natures_spirit", "cobbled_travertine" + shape);
      verifyMoss(
          "natures_spirit", shape.isEmpty() ? "travertine_bricks" : "travertine_brick" + shape);
      verifyMoss("quark", "cobblestone_bricks" + shape);
    }
    verifyMoss("quark", "cobblestone_bricks_vertical_slab");
    verifyMoss("quark", "cobblestone_vertical_slab");
    verifyMoss("quark", "stone_brick_vertical_slab");
    for (String material : new String[] {"travertine", "chert"}) {
      for (String finish : new String[] {"bricks", "tiles"}) {
        String name = material + "_" + finish;
        BlockState original = block("natures_spirit:" + name).defaultBlockState();
        BlockState cracked = block("natures_spirit:cracked_" + name).defaultBlockState();
        require(
            original.isRandomlyTicking() && original.is(ModTags.CRACKABLE),
            "Cracking tick " + original);
        require(
            cracked.is(ModTags.CRACKED)
                && Crackable.getCrackedBlock(original) == cracked
                && Crackable.getUncrackedCrackBlock(cracked) == original,
            "Reversible crack " + name);
      }
    }
    require(
        !Blocks.SANDSTONE.defaultBlockState().isRandomlyTicking(), "Unrelated masonry stays inert");
    requireNaturalChange(level, cleanSlab, mossySlab, Blocks.WATER);
    var chert = block("natures_spirit:chert_tiles").defaultBlockState();
    var cracked = block(CRACKED_TILES).defaultBlockState();
    requireNaturalChange(level, chert, cracked, Blocks.LAVA);
    level.setBlockAndUpdate(FIXTURE, mossySlab);
    var shears = new ItemStack(Items.SHEARS);
    int clumps =
        WeatheringDrops.spawnedBy(
            level, FIXTURE, ModItems.MOSS_CLUMP.get(), () -> use(level, FIXTURE, shears));
    require(
        level.getBlockState(FIXTURE) == cleanSlab && shears.getDamageValue() == 1,
        "Shears restore the exact slab and spend one durability");
    require(clumps == 1, "Shearing drops exactly one clump, observed " + clumps);
    level.setBlockAndUpdate(FIXTURE.east(), chert);
    var pickaxe = new ItemStack(Items.IRON_PICKAXE);
    use(level, FIXTURE.east(), pickaxe);
    require(
        level.getBlockState(FIXTURE.east()) == cracked && pickaxe.getDamageValue() == 1,
        "Pickaxe cracks a foreign block and spends one durability");
    var mortar = new ItemStack(ModItems.MORTAR.get(), 2);
    use(level, FIXTURE.east(), mortar);
    require(
        level.getBlockState(FIXTURE.east()) == chert && mortar.getCount() == 1,
        "Mortar restores the clean block and consumes one item");
    level.setBlockAndUpdate(FIXTURE, mossySlab);
    level.setBlockAndUpdate(FIXTURE.east(), cracked);
  }

  private static void verifyMoss(String namespace, String name) {
    Block base = block(namespace + ":" + name);
    Block mossy = block(namespace + ":mossy_" + name);
    require(mossy.defaultBlockState().is(ModTags.MOSSY), "Moss source tag " + name);
    for (BlockState state : base.getStateDefinition().getPossibleStates()) {
      BlockState converted = Mossable.getMossyBlock(state);
      require(
          state.isRandomlyTicking() && state.is(ModTags.MOSSABLE), "Moss tick and tag " + state);
      require(
          converted.is(mossy) && Mossable.getUnaffectedMossBlock(converted) == state,
          "Moss round trip, including all shared properties " + state);
    }
  }

  private static void requireNaturalChange(
      ServerLevel level, BlockState base, BlockState expected, Block source) {
    RandomSource random = RandomSource.create(53);
    for (int trial = 0; trial < 24; trial++) {
      BlockPos pos = new BlockPos(8 + trial % 4 * 8, 298, 8 + trial / 4 * 8);
      level.setBlockAndUpdate(pos, base);
      for (Direction face : Direction.Plane.HORIZONTAL)
        level.setBlockAndUpdate(pos.relative(face), source.defaultBlockState());
      for (int tick = 0; tick < 80 && level.getBlockState(pos) == base; tick++)
        base.randomTick(level, pos, random);
      boolean grew = level.getBlockState(pos) == expected;
      for (Direction face : Direction.Plane.HORIZONTAL)
        level.removeBlock(pos.relative(face), false);
      level.removeBlock(pos, false);
      if (grew) return;
    }
    throw new AssertionError("No native weathering transition from " + base + " beside " + source);
  }

  private static void use(ServerLevel level, BlockPos pos, ItemStack stack) {
    var player = FakePlayerFactory.getMinecraft(level);
    ItemStack old = player.getMainHandItem();
    boolean crouched = player.isShiftKeyDown();
    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    player.setShiftKeyDown(true);
    try {
      var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      require(
          ModEvents.onBlockCLicked(stack, player, level, InteractionHand.MAIN_HAND, hit)
              .consumesAction(),
          "Native Weathering interaction for " + stack);
    } finally {
      player.setItemInHand(InteractionHand.MAIN_HAND, old);
      player.setShiftKeyDown(crouched);
    }
  }

  private static BlockState slab(Block block) {
    return block
        .defaultBlockState()
        .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP)
        .setValue(BlockStateProperties.WATERLOGGED, true);
  }

  private static Block block(String id) {
    return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
