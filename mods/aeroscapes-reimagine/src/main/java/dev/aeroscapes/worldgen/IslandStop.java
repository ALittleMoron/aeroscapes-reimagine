package dev.aeroscapes.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

record IslandStop(WorldGenLevel level, Site site) {
  static final int FOUNDATION_DEPTH = 6;
  static final int LANDING_APPROACH_END = 9;
  private static final ResourceKey<LootTable> SURVEY_SUPPLIES =
      ResourceKey.create(
          Registries.LOOT_TABLE,
          ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "chests/survey_camp"));
  private static final TagKey<Block> LOOSE_DECK_DEBRIS =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "loose_deck_debris"));
  private static final int[][] DEN_PROFILE = {
    {1, 2, 2, 2, 0},
    {2, 3, 3, 3, 1},
    {1, 3, 3, 3, 2},
    {0, 2, 3, 3, 1},
    {0, 1, 1, 2, 0}
  };

  record Site(BlockPos anchor, Direction forward) {
    BlockPos pos(int x, int y, int z) {
      return anchor.relative(forward, z).relative(forward.getCounterClockWise(), x).above(y);
    }

    BoundingBox bounds(IslandStopStructure.Kind kind) {
      return kind == IslandStopStructure.Kind.LANDING
          ? BoundingBox.fromCorners(pos(-4, -FOUNDATION_DEPTH, -6), pos(4, 8, LANDING_APPROACH_END))
          : BoundingBox.fromCorners(pos(-2, -FOUNDATION_DEPTH, -2), pos(2, 3, 2));
    }

    boolean fits(NativeTerrainVolume raw, ChunkPos chunk, IslandStopStructure.Kind kind) {
      if (kind == IslandStopStructure.Kind.LANDING) return landingFits(raw, chunk);
      for (int x = -2; x <= 2; x++) {
        for (int z = -2; z <= 2; z++) {
          if (!supported(raw, x, z, 1) || !solid(raw, x, -2, z)) return false;
          if (kind != IslandStopStructure.Kind.DEN && !air(raw, x, z, 1, 3)) return false;
        }
      }
      return switch (kind) {
        case GARDEN -> gardenFits(raw);
        case DEN -> denFits(raw);
        case SURVEY -> true;
        case LANDING -> throw new IllegalStateException();
      };
    }

    private boolean gardenFits(NativeTerrainVolume raw) {
      for (int offset = -2; offset <= 2; offset++) {
        if (!solid(raw, -3, -1, offset)
            || !solid(raw, 3, -1, offset)
            || !solid(raw, offset, -1, -3)
            || !solid(raw, offset, -1, 3)) return false;
      }
      return air(raw, 0, 3, 1, 2) && supported(raw, 0, 3, 1);
    }

    private boolean denFits(NativeTerrainVolume raw) {
      return (solid(raw, -1, 1, -3) || solid(raw, 0, 1, -3) || solid(raw, 1, 1, -3))
          && air(raw, 1, 3, 1, 2)
          && supported(raw, 1, 3, 1);
    }

    private boolean landingFits(NativeTerrainVolume raw, ChunkPos chunk) {
      if (!chunk.equals(new ChunkPos(pos(-4, 0, -6)))
          || !chunk.equals(new ChunkPos(pos(4, 0, LANDING_APPROACH_END)))) return false;
      for (int x = -4; x <= 4; x++) {
        for (int z = -5; z <= LANDING_APPROACH_END; z++) {
          if (post(x, z) && !supported(raw, x, z, 4)) return false;
          if (!air(raw, x, z, z >= 5 ? -2 : 1, 8)) return false;
          var floor = pos(x, 0, z);
          if (z <= 4
              && !raw.block(floor.getX(), floor.getY(), floor.getZ()).getFluidState().isEmpty())
            return false;
        }
      }
      for (int x = -1; x <= 1; x++) {
        if (!supported(raw, x, -6, 1) || !air(raw, x, -6, 1, 2)) return false;
      }
      return true;
    }

    private boolean solid(NativeTerrainVolume raw, int x, int y, int z) {
      var point = pos(x, y, z);
      return NativeTerrainVolume.solid(raw.block(point.getX(), point.getY(), point.getZ()));
    }

    private boolean supported(NativeTerrainVolume raw, int x, int z, int limit) {
      for (int depth = 0; depth <= limit; depth++) {
        var floor = pos(x, -depth, z);
        var state = raw.block(floor.getX(), floor.getY(), floor.getZ());
        if (NativeTerrainVolume.solid(state)) return true;
        if (!state.isAir()) return false;
      }
      return false;
    }

    private boolean air(NativeTerrainVolume raw, int x, int z, int min, int max) {
      for (int y = min; y <= max; y++) {
        var point = pos(x, y, z);
        if (!raw.block(point.getX(), point.getY(), point.getZ()).isAir()) return false;
      }
      return true;
    }
  }

  void build(IslandStopStructure.Kind kind, RandomSource random) {
    if (kind == IslandStopStructure.Kind.LANDING) {
      if (!hasLanding()) layLanding();
      connectLandingRails();
      return;
    }
    for (int x = -2; x <= 2; x++) {
      for (int z = -2; z <= 2; z++) {
        if (!ground(pos(x, 0, z))) {
          var below = level.getBlockState(pos(x, -1, z)).getBlock();
          put(
              x,
              0,
              z,
              below instanceof FallingBlock || !ground(pos(x, -1, z)) ? Blocks.DIRT : below);
        }
      }
    }
    switch (kind) {
      case SURVEY -> survey(random);
      case GARDEN -> garden();
      case DEN -> den();
      case LANDING -> throw new IllegalStateException();
    }
  }

  private boolean ground(BlockPos point) {
    var state = level.getBlockState(point);
    return state.isFaceSturdy(level, point, Direction.UP)
        && !state.is(BlockTags.LOGS)
        && !state.is(BlockTags.LEAVES);
  }

  private void landingPost(int x, int z) {
    for (int depth = 1; depth <= FOUNDATION_DEPTH; depth++) {
      if (ground(pos(x, -depth, z))) {
        for (int y = 1 - depth; y < 0; y++) put(x, y, z, Blocks.SPRUCE_LOG);
        return;
      }
    }
  }

  private static boolean post(int x, int z) {
    return Math.abs(x) == 4 && (z == -5 || z == -2);
  }

  private void survey(RandomSource random) {
    for (int x : new int[] {-2, 2}) {
      for (int z : new int[] {-1, 1}) {
        for (int y = 1; y <= 2; y++) put(x, y, z, Blocks.SPRUCE_FENCE);
      }
    }
    for (int x = -2; x <= 2; x++) {
      for (int z = -1; z <= 1; z++) {
        put(x, 3, z, Math.abs(x) == 1 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL);
      }
    }
    put(0, 1, 0, Blocks.CARTOGRAPHY_TABLE);
    put(-1, 1, 0, Blocks.BARREL);
    RandomizableContainer.setBlockEntityLootTable(level, random, pos(-1, 1, 0), SURVEY_SUPPLIES);
    put(1, 1, -1, Blocks.LANTERN);
    put(1, 0, 2, Blocks.HAY_BLOCK);
    level.setBlock(
        pos(1, 1, 2),
        Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, true),
        3);
  }

  private void garden() {
    // A carver can remove the shallow floor under a broad, otherwise suitable terrace.
    for (int x = -2; x <= 2; x++) {
      for (int z = -2; z <= 2; z++) {
        if (!ground(pos(x, -1, z))) put(x, -1, z, Blocks.COARSE_DIRT);
      }
    }
    for (int x : new int[] {-2, 2}) {
      for (int z = -2; z <= 2; z++) put(x, 1, z, Blocks.MOSSY_COBBLESTONE_WALL);
    }
    put(0, 0, 0, Blocks.WATER);
    for (int z = -1; z <= 1; z++) {
      for (int x : new int[] {-1, 1}) {
        level.setBlock(
            pos(x, 0, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
        var crop = z == 0 ? Blocks.CARROTS : Blocks.WHEAT;
        level.setBlock(pos(x, 1, z), ((CropBlock) crop).getStateForAge(4 + x + z), 3);
      }
    }
    put(0, 1, -2, Blocks.COMPOSTER);
    put(-1, 1, 2, Blocks.LANTERN);
    for (int x : new int[] {-2, 2}) {
      for (int z = -2; z <= 2; z++) connect(pos(x, 1, z), Blocks.MOSSY_COBBLESTONE_WALL);
    }
  }

  private void den() {
    for (int z = -2; z <= 2; z++) {
      for (int x = -2; x <= 2; x++) {
        int height = DEN_PROFILE[z + 2][x + 2];
        if (denAir(x, 1, z)) put(x, 0, z, Blocks.ROOTED_DIRT);
        for (int y = 1; y <= height; y++) {
          if (denAir(x, y, z)) {
            put(x, y, z, Blocks.AIR);
          } else if (!ground(pos(x, y, z))) {
            var material =
                y == height
                    ? (x == -2 || z == -2 ? Blocks.GRASS_BLOCK : Blocks.MOSS_BLOCK)
                    : (y == 1 ? Blocks.ROOTED_DIRT : Blocks.COARSE_DIRT);
            put(x, y, z, material);
          }
        }
      }
    }
    put(1, 0, 2, Blocks.COARSE_DIRT);
    var fox = EntityType.FOX.create(level.getLevel());
    if (fox != null) {
      var home = pos(0, 1, -1);
      fox.setVariant(Fox.Type.byBiome(level.getBiome(home)));
      fox.moveTo(home, site.forward().toYRot(), 0);
      fox.setPersistenceRequired();
      level.addFreshEntity(fox);
    }
  }

  private static boolean denAir(int x, int y, int z) {
    return y <= 2 && (x == 0 || x == 1) && z >= -1 && z <= 1 || x == 1 && y == 1 && z == 2;
  }

  boolean hasDen() {
    return level.getBlockState(pos(1, 0, 2)).is(Blocks.COARSE_DIRT) && ground(pos(1, 2, 2));
  }

  void clearDenOpening() {
    for (int x = 0; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        for (int y = 1; y <= 2; y++) clearDecoration(pos(x, y, z), false);
      }
    }
    clearDecoration(pos(1, 1, 2), false);
  }

  boolean hasLanding() {
    return level.getBlockState(pos(0, 0, 0)).is(Blocks.YELLOW_TERRACOTTA)
        && level.getBlockState(pos(4, 2, -5)).is(Blocks.LANTERN);
  }

  void layLanding() {
    for (int x = -4; x <= 4; x++) {
      for (int z = -5; z <= 4; z++) {
        if (post(x, z)) landingPost(x, z);
        var deck = z == 4 || Math.abs(x) == 4 ? Blocks.STRIPPED_SPRUCE_WOOD : Blocks.SPRUCE_PLANKS;
        if (Math.abs(x) == 3 && (z == 3 || z == -3)) deck = Blocks.YELLOW_TERRACOTTA;
        put(x, 0, z, deck);
      }
    }
    for (int x : new int[] {-4, 4}) {
      for (int z = -5; z <= -2; z++) put(x, 1, z, Blocks.SPRUCE_FENCE);
      put(x, 1, -5, Blocks.SPRUCE_LOG);
      put(x, 2, -5, Blocks.LANTERN);
    }
    put(0, 0, 0, Blocks.YELLOW_TERRACOTTA);
    put(-1, 0, 0, Blocks.YELLOW_TERRACOTTA);
    put(1, 0, 0, Blocks.YELLOW_TERRACOTTA);
    put(0, 0, -1, Blocks.YELLOW_TERRACOTTA);
    put(0, 0, 1, Blocks.YELLOW_TERRACOTTA);
  }

  private void connectLandingRails() {
    for (int x : new int[] {-4, 4}) {
      for (int z = -4; z <= -2; z++) connect(pos(x, 1, z), Blocks.SPRUCE_FENCE);
    }
  }

  void clearLandingApproach() {
    for (int x = -4; x <= 4; x++) {
      for (int z = -5; z <= LANDING_APPROACH_END; z++) {
        for (int y = 1; y <= 8; y++) clearDecoration(pos(x, y, z), true);
      }
    }
  }

  private void clearDecoration(BlockPos point, boolean landing) {
    var state = level.getBlockState(point);
    boolean debris = landing ? state.is(LOOSE_DECK_DEBRIS) : state.is(Blocks.POINTED_DRIPSTONE);
    if (!state.isAir()
        && state.getFluidState().isEmpty()
        && !state.is(BlockTags.LOGS)
        && !state.is(BlockTags.LEAVES)
        && (debris || state.getBlock() instanceof BushBlock || state.is(BlockTags.REPLACEABLE))) {
      level.setBlock(point, Blocks.AIR.defaultBlockState(), 3);
    }
  }

  private void connect(BlockPos point, Block block) {
    var state = level.getBlockState(point);
    if (state.is(block)) {
      level.setBlock(point, Block.updateFromNeighbourShapes(state, level, point), 3);
    }
  }

  private void put(int x, int y, int z, Block block) {
    level.setBlock(pos(x, y, z), block.defaultBlockState(), 3);
  }

  private BlockPos pos(int x, int y, int z) {
    return site.pos(x, y, z);
  }
}
