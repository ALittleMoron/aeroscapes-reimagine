package dev.aeroscapes.worldgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

enum LandingScene implements StringRepresentable {
  PLAIN,
  BIVOUAC,
  REPAIR_BAY,
  FREIGHT_STAGE,
  PILOT_SHELTER,
  WATER_STOP,
  FIELD_KITCHEN,
  RESCUE_CACHE,
  SIGNAL_POINT,
  CARTOGRAPHY_SHELTER,
  ABANDONED_DEPOT;

  @Override
  public String getSerializedName() {
    return name().toLowerCase(Locale.ROOT);
  }

  record Terrace(IslandStop.Site site, int side, int rise) {
    private static final int WIDTH = 4;
    private static final int DEPTH = 4;
    private static final int HEIGHT = 6;
    private static final int FIRST_COLUMN = 5;
    private static final int BACK_EDGE = -6;
    private static final int FOOTING_DEPTH = 3;
    private static final int BRACE_SPAN = 2;

    static Terrace find(IslandStop.Site site, ChunkPos chunk, NativeTerrainVolume raw) {
      for (int rise : new int[] {0, -1, 1}) {
        for (int side : new int[] {1, -1}) {
          var terrace = new Terrace(site, side, rise);
          if (terrace.fits(chunk, raw)) return terrace;
        }
      }
      return null;
    }

    BoundingBox bounds() {
      return BoundingBox.fromCorners(pos(0, -IslandStop.FOUNDATION_DEPTH, 0), pos(3, HEIGHT, 3));
    }

    BlockPos pos(int column, int y, int row) {
      return site.pos(side * (FIRST_COLUMN + column), rise + y, BACK_EDGE + row);
    }

    private BlockPos path(int distance) {
      return site.pos(side * distance, distance < 3 ? 0 : rise, BACK_EDGE);
    }

    private int pathHeadroom(int distance) {
      return (rise == 1 && distance == 2) || (rise == -1 && distance == 3) ? 3 : 2;
    }

    private boolean fits(ChunkPos chunk, NativeTerrainVolume raw) {
      if (!chunk.equals(new ChunkPos(pos(WIDTH - 1, 0, 0)))
          || !chunk.equals(new ChunkPos(pos(WIDTH - 1, 0, DEPTH - 1)))) return false;
      for (int distance = 0; distance < FIRST_COLUMN; distance++) {
        if (!supported(raw, path(distance))
            || !clear(raw, path(distance), pathHeadroom(distance))) {
          return false;
        }
      }
      for (int column = 0; column < WIDTH; column++) {
        for (int row = 0; row < DEPTH; row++) {
          var floor = pos(column, 0, row);
          if (!supported(raw, floor) || !clear(raw, floor, HEIGHT)) return false;
        }
      }
      return true;
    }

    private static boolean supported(NativeTerrainVolume raw, BlockPos floor) {
      for (int depth = 0; depth <= FOOTING_DEPTH; depth++) {
        var state = raw.block(floor.getX(), floor.getY() - depth, floor.getZ());
        if (state.isAir()) continue;
        if (!NativeTerrainVolume.solid(state)) return false;
        if (depth > 0) return true;
      }
      return false;
    }

    private static boolean clear(NativeTerrainVolume raw, BlockPos floor, int height) {
      for (int y = 1; y <= height; y++) {
        if (!raw.block(floor.getX(), floor.getY() + y, floor.getZ()).isAir()) return false;
      }
      return true;
    }

    void prepare(WorldGenLevel level, LandingScene scene) {
      layFloor(level, scene, false);
    }

    void restoreFloor(WorldGenLevel level, LandingScene scene) {
      layFloor(level, scene, true);
    }

    private void layFloor(WorldGenLevel level, LandingScene scene, boolean repair) {
      var gaps = new ArrayList<BlockPos>();
      for (int distance = 0; distance < FIRST_COLUMN; distance++) {
        foundation(level, path(distance), Blocks.PACKED_MUD, gaps, repair);
      }
      var material =
          switch (scene) {
            case FREIGHT_STAGE, REPAIR_BAY -> Blocks.COBBLED_DEEPSLATE;
            case WATER_STOP, PILOT_SHELTER, SIGNAL_POINT -> Blocks.ANDESITE;
            case CARTOGRAPHY_SHELTER -> Blocks.SPRUCE_PLANKS;
            case FIELD_KITCHEN, ABANDONED_DEPOT -> Blocks.PACKED_MUD;
            default -> Blocks.MUD_BRICKS;
          };
      for (int column = 0; column < WIDTH; column++) {
        for (int row = 0; row < DEPTH; row++) {
          var pavers =
              scene == FREIGHT_STAGE && column >= 2 && row == 0
                  ? Blocks.YELLOW_TERRACOTTA
                  : material;
          foundation(level, pos(column, 0, row), pavers, gaps, repair);
        }
      }
      brace(level, gaps, new ChunkPos(site.anchor()));
    }

    private static void foundation(
        WorldGenLevel level, BlockPos top, Block material, List<BlockPos> gaps, boolean repair) {
      if (repair) {
        var state = level.getBlockState(top);
        if (!state.isAir() && !(state.getBlock() instanceof LiquidBlock)) return;
      }
      int depth = 1;
      while (depth <= FOOTING_DEPTH + 1) {
        var point = top.below(depth);
        var state = level.getBlockState(point);
        if (state.isFaceSturdy(level, point, Direction.UP)
            && !state.is(BlockTags.LOGS)
            && !state.is(BlockTags.LEAVES)) break;
        depth++;
      }
      if (depth <= FOOTING_DEPTH + 1) {
        for (int below = depth - 1; below >= 1; below--) {
          level.setBlock(top.below(below), Blocks.COBBLESTONE.defaultBlockState(), 3);
        }
      } else {
        gaps.add(top.below());
      }
      level.setBlock(top, material.defaultBlockState(), 3);
    }

    private static void brace(WorldGenLevel level, List<BlockPos> gaps, ChunkPos chunk) {
      // A carver can cut a localized gap under a fitted path. Transfer the load
      // to adjacent rock instead of running columns down the void.
      for (int span = 0; span < BRACE_SPAN && !gaps.isEmpty(); span++) {
        var beams = new HashMap<BlockPos, Direction.Axis>();
        for (var point : gaps) {
          for (var direction : Direction.Plane.HORIZONTAL) {
            var anchor = point.relative(direction);
            if (!chunk.equals(new ChunkPos(anchor))) continue;
            var state = level.getBlockState(anchor);
            if (!state.isFaceSturdy(level, anchor, direction.getOpposite())
                || state.is(BlockTags.LEAVES)) continue;
            if (state.is(BlockTags.LOGS)
                && !(state.is(Blocks.STRIPPED_SPRUCE_LOG)
                    && state.getValue(RotatedPillarBlock.AXIS) == direction.getAxis())) continue;
            beams.put(point, direction.getAxis());
            break;
          }
        }
        beams.forEach(
            (point, axis) ->
                level.setBlock(
                    point,
                    Blocks.STRIPPED_SPRUCE_LOG
                        .defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, axis),
                    3));
        gaps.removeAll(beams.keySet());
      }
    }

    void clearRoute(IslandStop stop) {
      for (int distance = 0; distance <= FIRST_COLUMN; distance++) {
        var floor = path(distance);
        for (int y = 1; y <= pathHeadroom(distance); y++)
          stop.clearDecoration(floor.above(y), true);
      }
      for (int column = 0; column <= 2; column++) {
        for (int y = 1; y <= 2; y++) stop.clearDecoration(pos(column, y, 0), true);
      }
      for (int column = 0; column < WIDTH; column++) {
        for (int row = 0; row < DEPTH; row++) {
          for (int y = 1; y <= 2; y++) {
            var point = pos(column, y, row);
            if (stop.level().getBlockState(point).is(Blocks.POINTED_DRIPSTONE)) {
              stop.clearDecoration(point, true);
            }
          }
        }
      }
    }
  }

  void build(WorldGenLevel level, Terrace terrace, RandomSource random) {
    var build = new Build(level, terrace, random);
    switch (this) {
      case BIVOUAC -> build.bivouac();
      case REPAIR_BAY -> build.repairBay();
      case FREIGHT_STAGE -> build.freightStage();
      case PILOT_SHELTER -> build.pilotShelter();
      case WATER_STOP -> build.waterStop();
      case FIELD_KITCHEN -> build.fieldKitchen();
      case RESCUE_CACHE -> build.rescueCache();
      case SIGNAL_POINT -> build.signalPoint();
      case CARTOGRAPHY_SHELTER -> build.cartographyShelter();
      case ABANDONED_DEPOT -> build.abandonedDepot();
      case PLAIN -> throw new IllegalStateException("A plain deck has no terrace");
    }
  }

  private record Build(WorldGenLevel level, Terrace terrace, RandomSource random) {
    private static final ResourceKey<LootTable> LANDING_SUPPLIES = loot("landing_supplies");
    private static final ResourceKey<LootTable> RESCUE_SUPPLIES = loot("landing_rescue_supplies");
    private static final ResourceLocation ROPE =
        ResourceLocation.fromNamespaceAndPath("supplementaries", "rope");

    private static ResourceKey<LootTable> loot(String path) {
      return ResourceKey.create(
          Registries.LOOT_TABLE,
          ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "chests/" + path));
    }

    private void bivouac() {
      for (int column : new int[] {1, 3}) {
        post(column, 1, 2, Blocks.STRIPPED_SPRUCE_LOG);
        post(column, 3, 2, Blocks.STRIPPED_SPRUCE_LOG);
      }
      for (int row = 1; row <= 3; row++) {
        put(1, 3, row, Blocks.WHITE_WOOL);
        put(2, 3, row, Blocks.GREEN_WOOL);
        put(2, 4, row, Blocks.GREEN_WOOL);
        put(3, 3, row, Blocks.WHITE_WOOL);
      }
      var direction = terrace.site().forward();
      var bed = Blocks.GREEN_BED.defaultBlockState().setValue(BedBlock.FACING, direction);
      put(2, 1, 3, bed.setValue(BedBlock.PART, BedPart.HEAD));
      put(2, 1, 2, bed);
      supplies(1, 2, Blocks.CHEST, LANDING_SUPPLIES);
      put(0, 1, 2, Blocks.CAMPFIRE);
      put(0, 1, 3, Blocks.COBBLESTONE);
    }

    private void repairBay() {
      for (int column : new int[] {1, 3}) {
        post(column, 1, 2, Blocks.STRIPPED_DARK_OAK_LOG);
        post(column, 3, 3, Blocks.STRIPPED_DARK_OAK_LOG);
      }
      var inward = terrace.site().forward();
      for (int column = 1; column <= 3; column++) {
        stairs(column, 3, 1, Blocks.DARK_OAK_STAIRS, inward);
        put(column, 3, 2, log(Blocks.STRIPPED_DARK_OAK_LOG, inward.getClockWise().getAxis()));
        stairs(column, 4, 2, Blocks.DARK_OAK_STAIRS, inward);
        put(column, 4, 3, Blocks.DARK_OAK_PLANKS);
      }
      put(1, 1, 2, Blocks.LOOM);
      put(
          1,
          2,
          2,
          BuiltInRegistries.BLOCK
              .getOptional(ROPE)
              .map(Block::defaultBlockState)
              .orElse(Blocks.CHAIN.defaultBlockState()));
      put(2, 1, 3, Blocks.CRAFTING_TABLE);
      put(3, 1, 2, Blocks.WHITE_WOOL);
      put(3, 2, 2, Blocks.BLUE_WOOL);
      put(0, 1, 2, log(Blocks.SPRUCE_LOG, inward.getAxis()));
      put(0, 1, 3, log(Blocks.SPRUCE_LOG, inward.getAxis()));
    }

    private void freightStage() {
      var inland = terrace.site().forward().getOpposite();
      for (int column = 2; column <= 3; column++) {
        stairs(column, 1, 1, Blocks.SPRUCE_STAIRS, inland.getOpposite());
        for (int row = 2; row <= 3; row++) put(column, 1, row, Blocks.STRIPPED_SPRUCE_WOOD);
      }
      put(2, 2, 3, Blocks.BARREL);
      put(3, 2, 2, Blocks.HAY_BLOCK);
      put(3, 2, 3, Blocks.BARREL);
      put(3, 3, 3, Blocks.ORANGE_WOOL);
    }

    private void pilotShelter() {
      for (int row = 1; row <= 3; row++) put(3, 1, row, Blocks.COBBLESTONE);
      for (int column = 1; column <= 3; column++) put(column, 1, 3, Blocks.COBBLESTONE);
      put(3, 2, 2, Blocks.MOSSY_COBBLESTONE);
      put(3, 2, 3, Blocks.COBBLESTONE);
      put(2, 2, 3, Blocks.ANDESITE);
      put(3, 3, 3, Blocks.LANTERN);
      put(0, 1, 2, Blocks.CHISELED_STONE_BRICKS);
      put(0, 2, 2, Blocks.YELLOW_TERRACOTTA);
      stairs(2, 1, 2, Blocks.SPRUCE_STAIRS, terrace.site().forward());
    }

    private void waterStop() {
      for (int column : new int[] {1, 3}) post(column, 3, 3, Blocks.STRIPPED_OAK_LOG);
      for (int column = 1; column <= 3; column++) {
        put(column, 4, 3, Blocks.WAXED_CUT_COPPER);
        stairs(column, 4, 2, Blocks.WAXED_CUT_COPPER_STAIRS, terrace.site().forward());
      }
      var water = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2);
      put(2, 1, 2, water);
      put(3, 1, 2, water);
      put(2, 3, 2, Blocks.CHAIN);
      put(2, 2, 2, Blocks.CHAIN);
      put(0, 1, 3, Blocks.BARREL);
    }

    private void fieldKitchen() {
      var towardFire = terrace.site().forward();
      post(1, 3, 2, Blocks.STRIPPED_MANGROVE_LOG);
      post(3, 1, 2, Blocks.STRIPPED_MANGROVE_LOG);
      post(3, 3, 2, Blocks.STRIPPED_MANGROVE_LOG);
      for (int column = 1; column <= 3; column++) {
        for (int row = 1; row <= 3; row++) {
          put(column, 3, row, column == 2 ? Blocks.YELLOW_WOOL : Blocks.BROWN_WOOL);
        }
      }
      put(2, 1, 2, Blocks.CAMPFIRE);
      put(2, 1, 3, Blocks.SMOKER);
      put(2, 2, 3, Blocks.BRICKS);
      put(2, 3, 3, Blocks.BRICKS);
      put(2, 4, 3, Blocks.BRICK_WALL);
      put(0, 1, 3, Blocks.CAULDRON);
      put(3, 1, 2, log(Blocks.MANGROVE_LOG, towardFire.getAxis()));
      stairs(0, 1, 1, Blocks.MANGROVE_STAIRS, towardFire.getOpposite());
    }

    private void rescueCache() {
      post(3, 3, 5, Blocks.STRIPPED_DARK_OAK_LOG);
      put(2, 5, 3, Blocks.RED_WOOL);
      put(1, 5, 3, Blocks.RED_WOOL);
      put(2, 4, 3, Blocks.WHITE_WOOL);
      put(3, 6, 3, Blocks.LANTERN);
      put(3, 1, 2, Blocks.STONE_BRICKS);
      put(3, 2, 2, Blocks.STONE_BRICKS);
      supplies(2, 2, Blocks.BARREL, RESCUE_SUPPLIES);
      put(2, 1, 3, Blocks.BARREL);
    }

    private void signalPoint() {
      put(2, 1, 2, Blocks.CHISELED_STONE_BRICKS);
      put(2, 2, 2, Blocks.WAXED_WEATHERED_COPPER);
      put(2, 3, 2, Blocks.STRIPPED_SPRUCE_LOG);
      put(2, 4, 2, Blocks.STRIPPED_SPRUCE_LOG);
      put(2, 5, 2, Blocks.WAXED_WEATHERED_COPPER);
      put(1, 5, 2, Blocks.WAXED_WEATHERED_COPPER);
      put(3, 5, 2, Blocks.WAXED_WEATHERED_COPPER);
      put(1, 6, 2, Blocks.LANTERN);
      put(3, 6, 2, Blocks.LANTERN);
      put(2, 5, 3, Blocks.YELLOW_WOOL);
      put(0, 1, 3, Blocks.SMOOTH_STONE);
    }

    private void cartographyShelter() {
      for (int column : new int[] {1, 3}) {
        post(column, 1, 3, Blocks.STRIPPED_BIRCH_LOG);
        post(column, 3, 3, Blocks.STRIPPED_BIRCH_LOG);
      }
      for (int column = 1; column <= 3; column++) {
        for (int row = 1; row <= 3; row++) {
          put(column, 4, row, column == 2 ? Blocks.LIGHT_BLUE_STAINED_GLASS : Blocks.BIRCH_PLANKS);
        }
      }
      put(1, 1, 2, Blocks.CARTOGRAPHY_TABLE);
      put(3, 1, 2, Blocks.BOOKSHELF);
      put(3, 2, 2, Blocks.LANTERN);
      put(0, 1, 2, Blocks.STRIPPED_BIRCH_LOG);
      put(0, 1, 3, Blocks.STRIPPED_BIRCH_LOG);
    }

    private void abandonedDepot() {
      var towardDeck = terrace.site().forward();
      post(3, 1, 2, Blocks.STRIPPED_DARK_OAK_LOG);
      put(3, 3, 1, Blocks.BROWN_WOOL);
      put(3, 3, 2, log(Blocks.STRIPPED_DARK_OAK_LOG, towardDeck.getAxis()));
      put(3, 2, 2, Blocks.BARREL);
      put(3, 1, 2, Blocks.CRACKED_STONE_BRICKS);
      put(3, 2, 3, Blocks.GRAY_WOOL);
      put(3, 1, 3, Blocks.BARREL);
      put(2, 1, 3, log(Blocks.STRIPPED_DARK_OAK_LOG, towardDeck.getClockWise().getAxis()));
      put(1, 1, 3, Blocks.BARREL);
      supplies(2, 2, Blocks.CHEST, LANDING_SUPPLIES);
      put(0, 1, 2, Blocks.MOSS_CARPET);
    }

    private void supplies(int column, int row, Block container, ResourceKey<LootTable> loot) {
      put(column, 1, row, container);
      RandomizableContainer.setBlockEntityLootTable(
          level, random, terrace.pos(column, 1, row), loot);
    }

    private void post(int column, int row, int height, Block block) {
      for (int y = 1; y <= height; y++) put(column, y, row, block);
    }

    private void stairs(int column, int y, int row, Block block, Direction direction) {
      put(column, y, row, block.defaultBlockState().setValue(StairBlock.FACING, direction));
    }

    private static BlockState log(Block block, Direction.Axis axis) {
      return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    private void put(int column, int y, int row, Block block) {
      put(column, y, row, block.defaultBlockState());
    }

    private void put(int column, int y, int row, BlockState block) {
      level.setBlock(terrace.pos(column, y, row), block, 3);
    }
  }
}
