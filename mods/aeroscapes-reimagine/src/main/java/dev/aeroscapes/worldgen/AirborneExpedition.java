package dev.aeroscapes.worldgen;

import dev.aeroscapes.worldgen.AirborneExpeditionStructure.Kind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

record AirborneExpedition(WorldGenLevel level, BoundingBox chunk, Site site) {
  private static final ResourceKey<LootTable> CARGO = loot("chests/derelict_cargo");
  private static final ResourceKey<LootTable> SURVEY = loot("chests/observation_balloon");

  private static final int BOW = -11;
  private static final int[] DECK_HALF_WIDTHS = {
    1, 1, 2, 2, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 2, 1
  };
  private static final int[] COURIER_SECTIONS = {
    1, 2, 3, 4, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 5, 5, 4, 4, 3, 2, 1, 0
  };
  private static final int[] BALLOON_RADII = {1, 2, 3, 4, 5, 5, 5, 4, 4, 3, 2, 1};

  record Site(BlockPos origin, Direction forward, Kind kind) {
    BlockPos ledge(int x, int y, int z) {
      return origin.relative(forward, z).relative(forward.getCounterClockWise(), x).above(y);
    }

    BlockPos ship(int x, int y, int z) {
      return kind == Kind.TETHERED ? ledge(x, y + 8, z + 20) : ledge(x, y, z);
    }

    BoundingBox pieceBounds() {
      if (kind == Kind.TETHERED) {
        return BoundingBox.fromCorners(ledge(-6, 0, -2), ship(6, 17, 14));
      }
      int width = kind == Kind.OBSERVATION ? 5 : 6;
      return BoundingBox.fromCorners(
          ship(-width, kind == Kind.OBSERVATION ? -2 : -4, kind == Kind.OBSERVATION ? -5 : BOW),
          ship(width, 17, kind == Kind.OBSERVATION ? 5 : 14));
    }

    BoundingBox flightSpace() {
      if (kind == Kind.OBSERVATION)
        return BoundingBox.fromCorners(ship(-8, -4, -8), ship(8, 20, 8));
      return BoundingBox.fromCorners(ship(-10, -6, -12), ship(10, 20, 16));
    }

    BoundingBox ledgeApproach() {
      return BoundingBox.fromCorners(ledge(-2, 1, -2), ledge(2, 10, 8));
    }

    Direction lateral(int side) {
      return side < 0 ? forward.getClockWise() : forward.getCounterClockWise();
    }
  }

  void build(RandomSource random) {
    if (site.kind() == Kind.OBSERVATION) {
      observation(random);
      return;
    }
    courierHull();
    courierEnvelope();
    courierRig();
    cargoDeck(random);
    if (site.kind() == Kind.TETHERED) mooring();
  }

  private void courierHull() {
    for (int section = 0; section < DECK_HALF_WIDTHS.length; section++) {
      int z = BOW + section;
      int width = DECK_HALF_WIDTHS[section];
      for (int x = -width; x <= width; x++) {
        put(
            x,
            0,
            z,
            Math.abs(x) == width
                ? Blocks.DARK_OAK_PLANKS
                : x == 0 ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.SPRUCE_PLANKS);
      }
      for (int x = -width + 1; x < width; x++) {
        put(x, -1, z, Blocks.DARK_OAK_PLANKS);
      }
      for (int side : new int[] {-1, 1}) {
        put(side * width, -1, z, stair(Blocks.SPRUCE_STAIRS, site.lateral(-side), Half.TOP));
        if (width > 1) {
          put(
              side * (width - 1),
              -2,
              z,
              stair(Blocks.DARK_OAK_STAIRS, site.lateral(-side), Half.TOP));
        }
      }
      if (width >= 3) put(0, -3, z, timber(site.forward().getAxis()));
      if (z >= -5 && z <= 8 && z != 0 && z != 1) {
        for (int side : new int[] {-1, 1}) {
          put(side * width, 1, z, railing(z != -5 && z != 2, z != -1 && z != 8));
        }
      } else if (z >= -9 && z <= -6 || z >= 9 && z <= 11) {
        for (int side : new int[] {-1, 1}) {
          put(side * width, 1, z, stair(Blocks.DARK_OAK_STAIRS, site.lateral(-side), Half.BOTTOM));
        }
      }
    }
    for (int z : new int[] {-5, 5}) {
      for (int x = -4; x <= 4; x++) put(x, 0, z, timber(site.lateral(1).getAxis()));
    }
    for (int z : new int[] {0, 1}) {
      for (int x = -5; x <= 5; x++) put(x, 0, z, timber(site.lateral(1).getAxis()));
    }
    put(-3, 1, -2, Blocks.LANTERN);
    put(3, 1, 3, Blocks.LANTERN);
  }

  private void cargoDeck(RandomSource random) {
    barrel(-2, 1, 3, random, CARGO);
    put(-3, 1, 4, Blocks.CRAFTING_TABLE);
    put(-3, 1, 6, Blocks.WAXED_EXPOSED_CUT_COPPER);
    put(2, 1, -5, Blocks.CARTOGRAPHY_TABLE);
    put(2, 1, -4, nativeState("create:brown_seat"));
    put(
        0,
        1,
        -5,
        nativeState(
            "simulated:steering_wheel", "facing", site.forward().getName(), "on_floor", "true"));
    put(
        -1,
        1,
        -5,
        nativeState(
            "simulated:throttle_lever", "facing", site.forward().getName(), "face", "floor"));
    put(
        0,
        1,
        -1,
        nativeState("aeronautics:adjustable_burner", "powered", "false", "variant", "fire"));

    String longitudinal = site.forward().getAxis().getName();
    String lateral = site.lateral(1).getAxis().getName();
    put(
        0,
        1,
        5,
        nativeState(
            "simulated:brown_portable_engine", "facing", site.forward().getName(), "lit", "false"));
    put(0, 1, 6, nativeState("create:shaft", "axis", longitudinal));
    put(0, 1, 7, nativeState("create:gearbox", "axis", "y"));
    // This missing coupling makes the inactive stern machinery legible without supplying a turnkey
    // ship.
    for (int z = 9; z <= 10; z++)
      put(0, 1, z, nativeState("create:andesite_encased_shaft", "axis", longitudinal));
    put(0, 1, 11, nativeState("create:gearbox", "axis", lateral));
    for (int y = -1; y <= 0; y++) put(0, y, 11, nativeState("create:shaft", "axis", "y"));
    put(0, -2, 11, nativeState("create:gearbox", "axis", lateral));
    put(
        0,
        -2,
        13,
        nativeState("aeronautics:propeller_bearing", "facing", site.forward().getName()));
    put(0, -2, 12, nativeState("create:shaft", "axis", longitudinal));
    put(0, -2, 14, nativeState("create:shaft", "axis", longitudinal));
    var sail = nativeState("simulated:white_symmetric_sail", "axis", longitudinal);
    for (int side : new int[] {-1, 1}) {
      for (int radius = 1; radius <= 2; radius++) {
        put(side * radius, -2, 14, sail);
        put(0, -2 + side * radius, 14, sail);
      }
    }
  }

  private void courierEnvelope() {
    var ivory = nativeState("aeronautics:white_envelope");
    var underside = nativeState("aeronautics:cyan_envelope");
    var seam = nativeState("aeronautics:light_blue_envelope");
    var patch = nativeState("aeronautics:gray_envelope");
    for (int z = BOW; z < BOW + COURIER_SECTIONS.length; z++) {
      for (int y = 7; y <= 17; y++) {
        for (int x = -6; x <= 6; x++) {
          if (!courierSkin(x, y, z) || x == 0 && y == 7 && z == -1) continue;
          var material = y <= 10 || y == 11 && Math.abs(x) >= 5 ? underside : ivory;
          if (y == 11 || x == 0 && y >= 15) material = seam;
          if (Math.abs(z) == 5 && y >= 8) material = seam;
          if (x <= -4 && z >= 7 && z <= 8 && y >= 13 && y <= 14) material = patch;
          put(x, y, z, material);
        }
      }
    }
  }

  private static boolean courierSkin(int x, int y, int z) {
    return courierVolume(x, y, z)
        && (!courierVolume(x - 1, y, z)
            || !courierVolume(x + 1, y, z)
            || !courierVolume(x, y - 1, z)
            || !courierVolume(x, y + 1, z)
            || !courierVolume(x, y, z - 1)
            || !courierVolume(x, y, z + 1));
  }

  private static boolean courierVolume(int x, int y, int z) {
    int section = z - BOW;
    if (section < 0 || section >= COURIER_SECTIONS.length) return false;
    int radius = COURIER_SECTIONS[section];
    int height = y - 12;
    if (Math.abs(height) > Math.min(5, (radius * 5 + 5) / 6)) return false;
    int chamfer = Math.max(0, Math.abs(height) - 2);
    return Math.abs(x) + chamfer <= radius;
  }

  private void courierRig() {
    for (int z : new int[] {-5, 5}) {
      for (int side : new int[] {-1, 1}) {
        int x = side * 3;
        put(x, 1, z, nativeState("create:metal_girder", "x", "false", "z", "false"));
        for (int y = 2; y < 6; y++) put(x, y, z, Blocks.CHAIN);
      }
      for (int x = -3; x <= 3; x++) {
        put(
            x,
            6,
            z,
            nativeState("create:metal_girder", site.lateral(1).getAxis().getName(), "true"));
      }
    }
  }

  private void observation(RandomSource random) {
    basketFloor();
    lookoutEnvelope();
    lookoutRig();
    put(
        0,
        1,
        0,
        nativeState("aeronautics:adjustable_burner", "powered", "false", "variant", "fire"));
    put(0, 1, 2, Blocks.CARTOGRAPHY_TABLE);
    put(0, 2, 2, Blocks.LANTERN);
    put(1, 1, 2, nativeState("create:brown_seat"));
    barrel(-1, 1, 2, random, SURVEY);
    for (int side : new int[] {-1, 1}) {
      put(side * 2, 1, 0, nativeState("create:hand_crank", "facing", "up"));
      put(
          side * 2,
          0,
          0,
          nativeState("create:gearbox", "axis", site.forward().getAxis().getName()));
      put(
          side * 3,
          0,
          0,
          nativeState(
              "create:andesite_encased_shaft", "axis", site.lateral(side).getAxis().getName()));
      put(
          side * 4,
          0,
          0,
          nativeState(
              "aeronautics:wooden_propeller",
              "facing",
              site.lateral(side).getName(),
              "reversed",
              "false"));
    }
  }

  private void basketFloor() {
    for (int z = -3; z <= 3; z++) {
      int width = Math.abs(z) == 3 ? 2 : 3;
      for (int x = -width; x <= width; x++) {
        boolean edge = Math.abs(x) == width || Math.abs(z) == 3;
        put(x, 0, z, edge ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.SPRUCE_PLANKS);
        if (!edge) {
          put(x, -1, z, Blocks.DARK_OAK_PLANKS);
        } else if (!(z == -3 && x == 0)) {
          put(
              x,
              1,
              z,
              Blocks.SPRUCE_TRAPDOOR
                  .defaultBlockState()
                  .setValue(
                      TrapDoorBlock.FACING,
                      Math.abs(z) == 3
                          ? (z > 0 ? site.forward() : site.forward().getOpposite())
                          : site.lateral(x))
                  .setValue(TrapDoorBlock.OPEN, true));
        }
      }
    }
    for (int z = -2; z <= 2; z++) put(0, -2, z, timber(site.forward().getAxis()));
  }

  private void lookoutEnvelope() {
    var ivory = nativeState("aeronautics:white_envelope");
    var orange = nativeState("aeronautics:orange_envelope");
    var throat = nativeState("aeronautics:yellow_envelope");
    for (int y = 6; y <= 17; y++) {
      for (int z = -5; z <= 5; z++) {
        for (int x = -5; x <= 5; x++) {
          if (!balloonVolume(x, y, z) || y == 6 && x == 0 && z == 0) continue;
          if (balloonVolume(x - 1, y, z)
              && balloonVolume(x + 1, y, z)
              && balloonVolume(x, y, z - 1)
              && balloonVolume(x, y, z + 1)
              && balloonVolume(x, y - 1, z)
              && balloonVolume(x, y + 1, z)) continue;
          put(x, y, z, y == 6 ? throat : Math.abs(x) <= 1 || Math.abs(z) <= 1 ? orange : ivory);
        }
      }
    }
  }

  private static boolean balloonVolume(int x, int y, int z) {
    int layer = y - 6;
    if (layer < 0 || layer >= BALLOON_RADII.length) return false;
    int radius = BALLOON_RADII[layer];
    return Math.abs(x) <= radius
        && Math.abs(z) <= radius
        && Math.abs(x) + Math.abs(z) <= radius + Math.max(1, radius / 2);
  }

  private void lookoutRig() {
    for (int z : new int[] {-1, 1}) {
      for (int side : new int[] {-1, 1}) {
        int x = side * 2;
        put(x, 0, z, timber(site.lateral(1).getAxis()));
        put(x, 1, z, Blocks.WAXED_EXPOSED_CUT_COPPER);
        for (int y = 2; y <= 5; y++) put(x, y, z, Blocks.CHAIN);
        put(x, 6, z, Blocks.WAXED_EXPOSED_CUT_COPPER);
      }
    }
  }

  private void mooring() {
    for (int z = -2; z <= 1; z++) {
      for (int x = -1; x <= 1; x++) {
        var footing = site.ledge(x, 0, z);
        if (chunk.isInside(footing) && level.getBlockState(footing).isAir()) {
          at(footing, timber(Direction.Axis.Y));
        }
        at(site.ledge(x, 1, z), timber(site.lateral(1).getAxis()));
      }
    }
    at(site.ledge(0, 2, -1), Blocks.STRIPPED_SPRUCE_WOOD.defaultBlockState());
    at(site.ledge(0, 3, -1), Blocks.LANTERN.defaultBlockState());
    var remnant =
        Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, site.forward().getAxis());
    for (int z = 0; z <= 1; z++) at(site.ledge(0, 2, z), remnant);

    var rail = timber(site.forward().getAxis());
    put(1, 0, BOW, rail);
    for (int y = -2; y <= 1; y++) {
      put(0, y, BOW + 1, rail);
      put(
          0,
          y,
          BOW,
          Blocks.LADDER
              .defaultBlockState()
              .setValue(LadderBlock.FACING, site.forward().getOpposite()));
    }
    put(1, -1, BOW, Blocks.WAXED_EXPOSED_CUT_COPPER);
    AirborneMooring.place(
        level, chunk, site.ledge(-1, 2, 1), Direction.UP, site.ship(-1, 1, BOW), Direction.UP);
  }

  private BlockState railing(boolean backward, boolean ahead) {
    return Blocks.SPRUCE_FENCE
        .defaultBlockState()
        .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(site.forward()), ahead)
        .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(site.forward().getOpposite()), backward);
  }

  private static BlockState stair(Block block, Direction rising, Half half) {
    return block
        .defaultBlockState()
        .setValue(StairBlock.FACING, rising)
        .setValue(StairBlock.HALF, half);
  }

  private static BlockState timber(Direction.Axis axis) {
    return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
  }

  private void barrel(int x, int y, int z, RandomSource random, ResourceKey<LootTable> table) {
    var pos = site.ship(x, y, z);
    if (!chunk.isInside(pos)) return;
    at(pos, Blocks.BARREL.defaultBlockState());
    RandomizableContainer.setBlockEntityLootTable(level, random, pos, table);
  }

  private void put(int x, int y, int z, Block block) {
    put(x, y, z, block.defaultBlockState());
  }

  private void put(int x, int y, int z, BlockState state) {
    at(site.ship(x, y, z), state);
  }

  private void at(BlockPos pos, BlockState state) {
    if (chunk.isInside(pos)) level.setBlock(pos, state, 2);
  }

  private static BlockState nativeState(String id, String... properties) {
    var block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow();
    BlockState state = block.defaultBlockState();
    for (int index = 0; index < properties.length; index += 2) {
      var property = block.getStateDefinition().getProperty(properties[index]);
      if (property == null) throw new IllegalArgumentException(id + " lacks " + properties[index]);
      state = set(state, property, properties[index + 1]);
    }
    return state;
  }

  private static <T extends Comparable<T>> BlockState set(
      BlockState state, Property<T> property, String value) {
    return state.setValue(property, property.getValue(value).orElseThrow());
  }

  private static ResourceKey<LootTable> loot(String path) {
    return ResourceKey.create(
        Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", path));
  }
}
