package dev.aeroscapes.worldgen;

import static dev.aeroscapes.worldgen.AirborneExpedition.nativeState;

import dev.aeroscapes.worldgen.AirborneExpedition.Site;
import dev.aeroscapes.worldgen.AirborneExpeditionStructure.Kind;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

record AirborneShips(AirborneExpedition expedition) {
  private static final ResourceKey<LootTable> GLASSHOUSE_STOCK =
      AirborneExpedition.loot("chests/airborne_glasshouse_stock");
  private static final Hull BARGE =
      new Hull(Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS);
  private static final Hull SKIFF =
      new Hull(Blocks.BIRCH_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS);
  private static final Hull TUG =
      new Hull(Blocks.SPRUCE_PLANKS, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_STAIRS);
  private static final Hull CATAMARAN =
      new Hull(Blocks.BIRCH_PLANKS, Blocks.WAXED_WEATHERED_CUT_COPPER, Blocks.BIRCH_STAIRS);
  private static final Hull CUTTER =
      new Hull(Blocks.JUNGLE_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_STAIRS);
  private static final Hull FERRY =
      new Hull(Blocks.BIRCH_PLANKS, Blocks.CYAN_TERRACOTTA, Blocks.BIRCH_STAIRS);
  private static final Hull WEATHER =
      new Hull(Blocks.SPRUCE_PLANKS, Blocks.MANGROVE_PLANKS, Blocks.SPRUCE_STAIRS);
  private static final Hull GLASSHOUSE =
      new Hull(Blocks.JUNGLE_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_STAIRS);

  static BoundingBox bounds(Site site) {
    return switch (site.kind()) {
      case CARGO_BARGE -> BoundingBox.fromCorners(site.ship(-7, -3, -12), site.ship(7, 14, 12));
      case CLIFF_SKIFF -> BoundingBox.fromCorners(site.ship(-3, -2, -9), site.ship(4, 13, 8));
      case SALVAGE_TUG -> BoundingBox.fromCorners(site.ship(-5, -3, -13), site.ship(5, 16, 12));
      case SURVEY_CATAMARAN ->
          BoundingBox.fromCorners(site.ship(-6, -2, -11), site.ship(6, 14, 11));
      case MAIL_CUTTER -> BoundingBox.fromCorners(site.ship(-4, -2, -14), site.ship(3, 15, 11));
      case PASSENGER_FERRY -> BoundingBox.fromCorners(site.ship(-7, -2, -9), site.ship(7, 14, 10));
      case WEATHER_SHIP -> BoundingBox.fromCorners(site.ship(-5, -3, -5), site.ship(5, 21, 8));
      case GLASSHOUSE_CARRIER ->
          BoundingBox.fromCorners(site.ship(-7, -3, -10), site.ship(7, 14, 11));
      default -> throw new IllegalArgumentException("No bounds for " + site.kind());
    };
  }

  void build(RandomSource random) {
    switch (expedition.site().kind()) {
      case CARGO_BARGE -> cargoBarge(random);
      case CLIFF_SKIFF -> cliffSkiff(random);
      case SALVAGE_TUG -> salvageTug(random);
      case SURVEY_CATAMARAN -> surveyCatamaran(random);
      case MAIL_CUTTER -> mailCutter(random);
      case PASSENGER_FERRY -> passengerFerry(random);
      case WEATHER_SHIP -> weatherShip(random);
      case GLASSHOUSE_CARRIER -> glasshouseCarrier(random);
      default -> throw new IllegalArgumentException("No variant for " + expedition.site().kind());
    }
  }

  private void cargoBarge(RandomSource random) {
    hull(
        BARGE,
        0,
        -10,
        0,
        3,
        new int[] {4, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 5, 5, 4, 4, 3});
    fill(-2, -1, -12, 2, -1, -11, Blocks.POLISHED_ANDESITE);
    for (int x = -2; x <= 2; x++)
      put(x, 0, -11, stair(Blocks.POLISHED_ANDESITE_STAIRS, stern(), Half.BOTTOM));
    fill(-2, -1, 1, 2, -1, 4, BARGE.deck());
    fill(-2, 0, 1, 2, 0, 4, Blocks.AIR);
    for (int x = -2; x <= 2; x++) put(x, 0, 0, stair(Blocks.SPRUCE_STAIRS, bow(), Half.BOTTOM));

    for (int side : new int[] {-1, 1}) {
      int x = side * 4;
      var cell = new Cell(x, 11, -1, 3, 3, 10);
      envelope(cell, "green", "brown", "lime", 2, 7);
      for (int z : new int[] {-5, 5}) {
        beam(-5, 5, 0, z, Blocks.STRIPPED_DARK_OAK_LOG);
        suspend(cell, x, 0, z, Blocks.IRON_BLOCK);
      }
      rail(side * 6, -7, 7, Blocks.DARK_OAK_FENCE);
      fill(side * 4, 1, 0, side * 3, 2, 1, Blocks.BARREL);
      fill(side * 4, 1, 6, side * 3, 1, 7, Blocks.BARREL);
      put(side * 3, 1, -8, Blocks.LANTERN);
    }
    cabin(-2, 2, 7, 10, 3, Blocks.DARK_OAK_PLANKS, Blocks.GLASS_PANE, Blocks.DARK_OAK_SLAB);
    helm(1, 8);
    put(-4, 1, -3, Blocks.CRAFTING_TABLE);
    propeller(0, -1, 11, stern(), "andesite");
    expedition.barrel(1, 0, 3, random, AirborneExpedition.CARGO);
  }

  private void cliffSkiff(RandomSource random) {
    hull(SKIFF, 0, -9, 4, 0, new int[] {0, 1, 1, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 1});
    var cell = new Cell(0, 10, -1, 3, 3, 8);
    envelope(cell, "blue", "cyan", "light_blue", -3, 3);
    for (int side : new int[] {-1, 1}) {
      for (int z : new int[] {-4, 3}) {
        beam(-2, 2, 0, z, Blocks.STRIPPED_BIRCH_LOG);
        suspend(cell, side * 2, 0, z, Blocks.WAXED_CUT_COPPER);
      }
      rail(side * 3, -3, -1, Blocks.BIRCH_FENCE);
      rail(side * 3, 2, 4, Blocks.BIRCH_FENCE);
    }
    for (int z = 0; z <= 1; z++) {
      put(4, -1, z, Blocks.BIRCH_PLANKS);
      put(3, 0, z, stair(Blocks.BIRCH_STAIRS, expedition.site().lateral(-1), Half.BOTTOM));
    }
    put(4, -2, 1, stair(Blocks.SPRUCE_STAIRS, expedition.site().lateral(-1), Half.TOP));
    put(-2, 1, -1, Blocks.LANTERN);
    helm(0, -3);
    put(0, 1, 2, nativeState("create:blue_seat"));
    put(0, 1, 5, nativeState("create:hand_crank", "facing", "up"));
    propeller(0, -1, 8, stern(), "wooden");
    expedition.barrel(-1, 1, 4, random, AirborneExpedition.SURVEY);
  }

  private void salvageTug(RandomSource random) {
    hull(
        TUG,
        0,
        -12,
        5,
        3,
        new int[] {0, 1, 2, 3, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 4, 4, 3, 2, 2});
    cabin(
        -2,
        2,
        -7,
        -2,
        3,
        Blocks.WAXED_EXPOSED_CUT_COPPER,
        Blocks.GLASS_PANE,
        Blocks.WAXED_CUT_COPPER_SLAB);
    helm(-1, -5);
    put(
        -1,
        1,
        -6,
        nativeState("simulated:throttle_lever", "facing", stern().getName(), "face", "floor"));
    fill(-1, 1, -9, 1, 1, -9, Blocks.POLISHED_ANDESITE);
    var cell = new Cell(0, 12, -2, 5, 4, 11);
    envelope(cell, "yellow", "orange", "brown", -5, 1, 6);
    for (int side : new int[] {-1, 1}) {
      fill(side * 5, 0, -6, side * 5, 0, 6, Blocks.POLISHED_DEEPSLATE);
      fill(side * 5, 1, -6, side * 5, 1, 6, Blocks.POLISHED_ANDESITE_SLAB);
      for (int z : new int[] {-6, 2}) {
        beam(-4, 4, 0, z, Blocks.STRIPPED_SPRUCE_LOG);
        suspend(cell, side * 3, 0, z, Blocks.WAXED_EXPOSED_CUT_COPPER);
      }
      put(side * 3, 1, 8, Blocks.LANTERN);
    }
    put(0, -1, 12, Blocks.POLISHED_ANDESITE);
    put(0, 0, 11, stair(Blocks.POLISHED_ANDESITE_STAIRS, bow(), Half.BOTTOM));
    put(1, -1, 11, Blocks.POLISHED_ANDESITE);
    put(-1, -1, 11, Blocks.POLISHED_ANDESITE);
    put(1, -1, 12, Blocks.POLISHED_ANDESITE);
    put(-1, -1, 12, Blocks.POLISHED_ANDESITE);

    put(2, 1, 3, Blocks.WAXED_EXPOSED_CUT_COPPER);
    put(2, 2, 3, upright());
    put(2, 3, 3, Blocks.WAXED_EXPOSED_CUT_COPPER);
    put(2, 3, 4, girder(stern().getAxis()));
    put(2, 3, 5, nativeState("create:gearbox", "axis", crossAxis().getName()));
    for (int y : new int[] {1, 2, 4})
      put(2, y, 5, nativeState("create:andesite_encased_shaft", "axis", "y"));
    put(2, 5, 5, nativeState("create:gearbox", "axis", crossAxis().getName()));
    put(2, 6, 5, Blocks.WAXED_EXPOSED_CUT_COPPER);
    String lengthwise = stern().getAxis().getName();
    for (int z = 6; z <= 9; z++) {
      put(
          2,
          5,
          z,
          nativeState("create:metal_girder_encased_shaft", "axis", lengthwise, "top", "true"));
      put(
          2,
          6,
          z,
          nativeState(
              "create:metal_girder", "axis", lengthwise, lengthwise, "true", "bottom", "true"));
    }
    put(2, 5, 10, nativeState("create:rope_pulley", "axis", lengthwise));
    put(2, 6, 10, Blocks.WAXED_EXPOSED_CUT_COPPER);
    put(2, 4, 10, Blocks.CHAIN);
    put(2, 3, 10, Blocks.CHAIN);
    put(
        2,
        2,
        10,
        Blocks.LIGHTNING_ROD
            .defaultBlockState()
            .setValue(LightningRodBlock.FACING, Direction.DOWN));
    put(1, 0, 5, nativeState("create:gearbox", "axis", lengthwise));
    put(2, 0, 5, nativeState("create:gearbox", "axis", lengthwise));
    put(1, 1, 5, Blocks.WAXED_EXPOSED_CUT_COPPER);
    put(-3, 1, 4, Blocks.CRAFTING_TABLE);
    propeller(0, -2, 12, stern(), "andesite");
    expedition.barrel(-3, 1, 5, random, AirborneExpedition.CARGO);
  }

  private void surveyCatamaran(RandomSource random) {
    for (int side : new int[] {-1, 1}) {
      int x = side * 4;
      hull(
          CATAMARAN,
          x,
          -9,
          0,
          0,
          new int[] {0, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 0});
      propeller(x, -1, 10, stern(), "wooden");
      rail(side * 6, -5, 5, Blocks.BIRCH_FENCE);
    }
    fill(-3, 0, -4, 3, 0, 2, Blocks.BIRCH_PLANKS);
    for (int z : new int[] {-4, 2}) beam(-5, 5, 0, z, Blocks.STRIPPED_BIRCH_LOG);
    beam(-3, 3, -1, 5, Blocks.STRIPPED_BIRCH_LOG);
    fill(-1, -1, 3, 1, -1, 4, Blocks.BIRCH_PLANKS);
    fill(-1, -1, 6, 1, -1, 6, Blocks.BIRCH_PLANKS);
    for (int x = -1; x <= 1; x++) put(x, 0, 3, stair(Blocks.BIRCH_STAIRS, bow(), Half.BOTTOM));
    for (int z : new int[] {-6, 6}) {
      var cell = new Cell(0, 10, z, 4, 4, 5);
      envelope(cell, "white", "cyan", "light_gray", z - 2, z + 2);
      for (int side : new int[] {-1, 1})
        suspend(cell, side * 4, 0, z, Blocks.WAXED_WEATHERED_CUT_COPPER);
    }
    put(-2, 1, -2, Blocks.CARTOGRAPHY_TABLE);
    put(-1, 1, -2, nativeState("create:cyan_seat"));
    put(2, 1, 1, Blocks.LANTERN);
    helm(0, -3);
    expedition.barrel(1, 1, 0, random, AirborneExpedition.SURVEY);
  }

  private void mailCutter(RandomSource random) {
    hull(
        CUTTER,
        0,
        -14,
        5,
        0,
        new int[] {0, 0, 1, 1, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 1, 1, 0});
    var cell = new Cell(0, 12, -2, 3, 3, 11);
    envelope(cell, "red", "white", "light_gray", -6, 3);
    cabin(-2, 2, -5, 4, 4, Blocks.MANGROVE_PLANKS, Blocks.GLASS_PANE, Blocks.JUNGLE_SLAB);
    for (int side : new int[] {-1, 1}) {
      for (int z : new int[] {-5, 4}) {
        beam(-3, 3, 0, z, Blocks.STRIPPED_MANGROVE_LOG);
        suspend(cell, side * 2, 4, z, Blocks.WAXED_EXPOSED_CUT_COPPER);
      }
    }
    fill(-4, -1, 3, -4, -1, 5, Blocks.JUNGLE_PLANKS);
    for (int z = 3; z <= 5; z++)
      put(-3, 0, z, stair(Blocks.JUNGLE_STAIRS, expedition.site().lateral(1), Half.BOTTOM));
    put(-2, 1, 6, Blocks.LANTERN);
    put(1, 1, 0, Blocks.LECTERN);
    put(-1, 1, -3, Blocks.CARTOGRAPHY_TABLE);
    helm(1, -3);
    propeller(0, -1, 11, stern(), "wooden");
    expedition.barrel(1, 1, 1, random, AirborneExpedition.SURVEY);
  }

  private void passengerFerry(RandomSource random) {
    hull(FERRY, 0, -9, 0, 0, new int[] {2, 3, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 4, 3, 2, 2});
    var cell = new Cell(0, 11, 0, 7, 3, 9);
    envelope(cell, "light_gray", "blue", "light_blue", -4, 4);
    cabin(
        -4,
        4,
        -5,
        5,
        4,
        Blocks.BIRCH_PLANKS,
        Blocks.LIGHT_BLUE_STAINED_GLASS_PANE,
        Blocks.BIRCH_SLAB);
    for (int side : new int[] {-1, 1}) {
      for (int z : new int[] {-4, 4}) {
        put(side * 4, 4, z, Blocks.WAXED_EXPOSED_CUT_COPPER);
        suspend(cell, side * 4, 4, z, Blocks.WAXED_EXPOSED_CUT_COPPER);
      }
      for (int z : new int[] {-3, -2, 2, 3}) put(side * 3, 1, z, nativeState("create:gray_seat"));
      put(side * 2, -1, 9, Blocks.CYAN_TERRACOTTA);
      propeller(side * 2, -1, 10, stern(), "andesite");
      rail(side * 5, -4, -2, Blocks.BIRCH_FENCE);
      rail(side * 5, 2, 4, Blocks.BIRCH_FENCE);
      put(side * 3, 1, 4, Blocks.LANTERN);
    }
    fill(7, -1, -1, 7, -1, 1, Blocks.BIRCH_PLANKS);
    fill(6, -1, -1, 6, -1, 1, Blocks.CYAN_TERRACOTTA);
    for (int z = -1; z <= 1; z++)
      put(6, 0, z, stair(Blocks.BIRCH_STAIRS, expedition.site().lateral(-1), Half.BOTTOM));
    helm(-1, -4);
    put(2, 1, -4, Blocks.CARTOGRAPHY_TABLE);
    expedition.barrel(-1, 1, 3, random, AirborneExpedition.CARGO);
  }

  private void weatherShip(RandomSource random) {
    hull(WEATHER, 0, -5, 0, 3, new int[] {2, 3, 4, 4, 4, 4, 4, 4, 4, 3, 2});
    var cell = new Cell(0, 15, 0, 5, 6, 5);
    envelope(cell, "white", "gray", "light_gray", -2, 2);
    put(0, 9, 0, Blocks.AIR);
    put(
        0,
        1,
        0,
        nativeState("aeronautics:adjustable_burner", "powered", "false", "variant", "fire"));
    for (int side : new int[] {-1, 1}) {
      for (int z : new int[] {-2, 2}) {
        beam(-3, 3, 0, z, Blocks.STRIPPED_SPRUCE_LOG);
        suspend(cell, side * 2, 0, z, Blocks.WAXED_EXPOSED_CUT_COPPER);
      }
      rail(side * 4, -3, 3, Blocks.SPRUCE_FENCE);
    }
    sternLadder(5, Blocks.SPRUCE_PLANKS);
    fill(-1, -3, 5, 1, -3, 8, Blocks.SPRUCE_PLANKS);
    put(-2, 1, -3, Blocks.WAXED_EXPOSED_CUT_COPPER);
    put(-2, 2, -3, Blocks.WAXED_EXPOSED_CUT_COPPER);
    put(-2, 3, -3, Blocks.LIGHTNING_ROD);
    put(2, 1, -3, Blocks.WAXED_COPPER_BLOCK);
    put(2, 2, -3, Blocks.DAYLIGHT_DETECTOR);
    put(0, 1, -3, Blocks.CARTOGRAPHY_TABLE);
    put(1, 1, 3, Blocks.LANTERN);
    expedition.barrel(-2, 1, 1, random, AirborneExpedition.SURVEY);
  }

  private void glasshouseCarrier(RandomSource random) {
    hull(
        GLASSHOUSE,
        0,
        -10,
        0,
        0,
        new int[] {1, 2, 3, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 4, 3, 2, 2});
    Block glass = nativeState("create:framed_glass").getBlock();
    for (int side : new int[] {-1, 1}) {
      var cell = new Cell(side * 5, 11, 0, 2, 3, 8);
      envelope(cell, "green", "cyan", "lime", -4, 4);
      for (int z : new int[] {-4, 4}) {
        beam(-5, 5, 0, z, Blocks.STRIPPED_DARK_OAK_LOG);
        suspend(cell, side * 5, 0, z, Blocks.WAXED_WEATHERED_CUT_COPPER);
      }
      int wall = side * 3;
      fill(wall, 1, -5, wall, 1, 5, Blocks.STRIPPED_JUNGLE_WOOD);
      fill(wall, 2, -5, wall, 3, 5, glass);
      fill(side * 2, 3, -5, side * 2, 4, 5, glass);
      fill(side, 4, -5, side, 5, 5, glass);
      for (int z : new int[] {-5, 0, 5}) {
        fill(wall, 2, z, wall, 3, z, Blocks.STRIPPED_JUNGLE_WOOD);
        fill(side * 2, 3, z, side * 2, 4, z, Blocks.STRIPPED_JUNGLE_WOOD);
        fill(side, 4, z, side, 5, z, Blocks.STRIPPED_JUNGLE_WOOD);
      }
      for (int z : new int[] {-5, 5}) {
        fill(side, 1, z, side * 2, 3, z, glass);
        put(side, 4, z, glass);
        put(side * 2, 3, z, Blocks.STRIPPED_JUNGLE_WOOD);
      }
      fill(side * 2, 1, -3, side * 2, 1, 3, Blocks.ROOTED_DIRT);
      put(side * 2, 2, -2, Blocks.POTTED_AZALEA);
      put(side * 2, 2, 0, Blocks.POTTED_FERN);
      put(side * 2, 2, 2, Blocks.POTTED_ALLIUM);
      rail(side * 5, -3, 3, Blocks.DARK_OAK_FENCE);
    }
    fill(0, 5, -5, 0, 5, 5, Blocks.WAXED_WEATHERED_CUT_COPPER);
    put(0, 4, -5, glass);
    put(0, 4, 5, glass);
    put(0, 3, -5, Blocks.STRIPPED_JUNGLE_WOOD);
    put(0, 3, 5, Blocks.STRIPPED_JUNGLE_WOOD);
    sternLadder(9, Blocks.DARK_OAK_PLANKS);
    fill(-1, -3, 9, 1, -3, 11, Blocks.DARK_OAK_PLANKS);
    propeller(-1, -1, 10, stern(), "wooden");
    helm(0, -7);
    put(1, 1, 7, Blocks.LANTERN);
    expedition.barrel(-1, 1, 4, random, GLASSHOUSE_STOCK);
  }

  private void hull(Hull materials, int center, int bow, int raised, int keel, int[] widths) {
    for (int section = 0; section < widths.length; section++) {
      int z = bow + section;
      int y = section < raised ? (raised - section + 2) / 3 : 0;
      int width = widths[section];
      for (int x = -width; x <= width; x++)
        put(center + x, y, z, Math.abs(x) == width ? materials.side() : materials.deck());
      for (int x = -width + 1; x < width; x++) put(center + x, y - 1, z, materials.side());
      for (int side : new int[] {-1, 1}) {
        if (width > 0)
          put(
              center + side * width,
              y - 1,
              z,
              stair(materials.chine(), expedition.site().lateral(-side), Half.TOP));
        if (width > 1)
          put(
              center + side * (width - 1),
              y - 2,
              z,
              stair(materials.chine(), expedition.site().lateral(-side), Half.TOP));
      }
      if (width == 0) put(center, y - 1, z, materials.side());
      for (int x = -width + 2; x <= width - 2; x++) put(center + x, y - 2, z, materials.side());
      if (keel > 0 && width >= keel) put(center, y - 3, z, materials.side());
    }
  }

  private void envelope(Cell cell, String cloth, String underside, String band, int... seams) {
    var top = nativeState("aeronautics:" + cloth + "_envelope");
    var bottom = nativeState("aeronautics:" + underside + "_envelope");
    var seam = nativeState("aeronautics:" + band + "_envelope");
    for (int z = cell.z() - cell.length(); z <= cell.z() + cell.length(); z++) {
      boolean rib = false;
      for (int line : seams) if (line == z) rib = true;
      for (int y = cell.y() - cell.height(); y <= cell.y() + cell.height(); y++) {
        for (int x = cell.x() - cell.width(); x <= cell.x() + cell.width(); x++) {
          if (cell.skin(x, y, z)) {
            put(x, y, z, y < cell.y() - 1 ? bottom : rib ? seam : top);
          }
        }
      }
    }
  }

  private void suspend(Cell cell, int x, int deck, int z, Block collar) {
    int bottom = cell.y() - cell.height();
    while (bottom < cell.y() && !cell.contains(x, bottom, z)) bottom++;
    if (!cell.contains(x, bottom, z) || bottom - deck < 4)
      throw new IllegalArgumentException("No lift cell above suspension at " + x + "," + z);
    put(x, deck + 1, z, upright());
    for (int y = deck + 2; y <= bottom - 2; y++) put(x, y, z, Blocks.CHAIN);
    put(x, bottom - 1, z, collar);
  }

  private void cabin(
      int left, int right, int front, int back, int roof, Block frame, Block glass, Block slab) {
    boolean closedStern = expedition.site().kind() == Kind.CARGO_BARGE;
    for (int z = front; z <= back; z++) {
      for (int x = left; x <= right; x++) {
        boolean end = z == front || z == back;
        boolean side = x == left || x == right;
        boolean endPassage = end && (z == front || !closedStern);
        if (end || side) {
          boolean opening = endPassage && x == 0 || side && cabinSideEntrance(x, z);
          put(x, 1, z, opening ? Blocks.AIR : frame);
          for (int y = 2; y < roof; y++) {
            if (opening && (end || y == 2)) put(x, y, z, Blocks.AIR);
            else if (side && (end || Math.abs(z) % 4 == 0)) put(x, y, z, frame);
            else put(x, y, z, cabinWindow(glass, end, endPassage, x, y, z));
          }
        }
        if (end || side) put(x, roof, z, frame);
        else put(x, roof, z, slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
      }
    }
  }

  private boolean cabinSideEntrance(int x, int z) {
    return switch (expedition.site().kind()) {
      case MAIL_CUTTER -> x == -2 && z == 3;
      case PASSENGER_FERRY -> x == 4 && z == 0;
      default -> false;
    };
  }

  private BlockState cabinWindow(
      Block glass, boolean endWall, boolean endPassage, int x, int y, int z) {
    var pane = glass.defaultBlockState();
    if (!endWall)
      return pane.setValue(
              PipeBlock.PROPERTY_BY_DIRECTION.get(bow()), y > 2 || !cabinSideEntrance(x, z - 1))
          .setValue(
              PipeBlock.PROPERTY_BY_DIRECTION.get(stern()), y > 2 || !cabinSideEntrance(x, z + 1));
    return pane.setValue(
            PipeBlock.PROPERTY_BY_DIRECTION.get(expedition.site().lateral(-1)),
            !endPassage || x != 1)
        .setValue(
            PipeBlock.PROPERTY_BY_DIRECTION.get(expedition.site().lateral(1)),
            !endPassage || x != -1);
  }

  private void sternLadder(int transom, Block frame) {
    fill(0, -3, transom, 0, 1, transom, frame);
    fill(-1, 0, transom + 1, -1, 0, transom + 1, frame);
    fill(1, 0, transom + 1, 1, 0, transom + 1, frame);
    for (int y = -2; y <= 1; y++)
      put(
          0,
          y,
          transom + 1,
          Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, stern()));
  }

  private void helm(int x, int z) {
    put(
        x,
        1,
        z,
        nativeState("simulated:steering_wheel", "facing", stern().getName(), "on_floor", "true"));
  }

  private void propeller(int x, int y, int z, Direction facing, String material) {
    put(
        x,
        y,
        z,
        nativeState(
            "aeronautics:" + material + "_propeller",
            "facing",
            facing.getName(),
            "reversed",
            "false"));
  }

  private void rail(int x, int front, int back, Block railing) {
    for (int z = front; z <= back; z++) {
      put(
          x,
          1,
          z,
          railing
              .defaultBlockState()
              .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(bow()), z > front)
              .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(stern()), z < back));
    }
  }

  private void beam(int left, int right, int y, int z, Block log) {
    for (int x = left; x <= right; x++)
      put(x, y, z, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, crossAxis()));
  }

  private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
    for (int z = z1; z <= z2; z++)
      for (int y = y1; y <= y2; y++)
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) put(x, y, z, block);
  }

  private void put(int x, int y, int z, Block block) {
    expedition.put(x, y, z, block);
  }

  private void put(int x, int y, int z, BlockState state) {
    expedition.put(x, y, z, state);
  }

  private Direction stern() {
    return expedition.site().forward();
  }

  private Direction bow() {
    return stern().getOpposite();
  }

  private Direction.Axis crossAxis() {
    return expedition.site().lateral(1).getAxis();
  }

  private static BlockState stair(Block block, Direction facing, Half half) {
    return AirborneExpedition.stair(block, facing, half);
  }

  private static BlockState upright() {
    return nativeState("create:metal_girder", "x", "false", "z", "false");
  }

  private static BlockState girder(Direction.Axis axis) {
    return nativeState("create:metal_girder", axis.getName(), "true");
  }

  private record Hull(Block deck, Block side, Block chine) {}

  private record Cell(int x, int y, int z, int width, int height, int length) {
    boolean contains(int blockX, int blockY, int blockZ) {
      double nx = (blockX - x) / (width + 0.48);
      double ny = (blockY - y) / (height + 0.48);
      double nz = (blockZ - z) / (length + 0.48);
      return nx * nx + ny * ny + nz * nz <= 1;
    }

    boolean skin(int x, int y, int z) {
      return contains(x, y, z)
          && (!contains(x - 1, y, z)
              || !contains(x + 1, y, z)
              || !contains(x, y - 1, z)
              || !contains(x, y + 1, z)
              || !contains(x, y, z - 1)
              || !contains(x, y, z + 1));
    }
  }
}
