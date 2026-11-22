package dev.aeroscapes.ecology.cliff;

import com.mojang.serialization.Codec;
import dev.aeroscapes.worldgen.ExposedIslandSurfaces;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import org.jetbrains.annotations.Nullable;

public final class CliffHabitatFeature extends Feature<CliffHabitatFeature.Configuration> {
  public static final LongAdder ATTEMPTS = new LongAdder();
  public static final LongAdder NANOS = new LongAdder();

  private static final int COLUMN_ATTEMPTS = 8;
  private static final int MINIMUM_AIR_DROP = 9;
  private static final int CURTAIN_RADIUS = 3;
  private static final int MINIMUM_ROOTS = 3;
  private static final int MINIMUM_VINE_LENGTH = 3;
  private static final int[] ROOF_STEPS = {0, -1, 1, -2, 2};
  private static final TagKey<Biome> ICICLE_BIOMES =
      TagKey.create(
          Registries.BIOME, ResourceLocation.fromNamespaceAndPath("immersive_weathering", "icy"));

  record Configuration(CliffSite.Kind kind) implements FeatureConfiguration {
    private static final Codec<Configuration> CODEC =
        CliffSite.Kind.CODEC
            .optionalFieldOf("kind", CliffSite.Kind.ROOTS)
            .codec()
            .xmap(Configuration::new, Configuration::kind);
  }

  public CliffHabitatFeature() {
    super(Configuration.CODEC);
  }

  @Override
  public boolean place(FeaturePlaceContext<Configuration> context) {
    long started = System.nanoTime();
    try {
      return placeHabitat(context);
    } finally {
      ATTEMPTS.increment();
      NANOS.add(System.nanoTime() - started);
    }
  }

  private static boolean placeHabitat(FeaturePlaceContext<Configuration> context) {
    var level = context.level();
    var terrain = IslandTerrain.from(level.getLevel().getChunkSource().randomState());
    if (terrain == null) return false;
    var chunk = level.getChunk(context.origin());
    var kind = context.config().kind();
    if (CliffHabitats.site(chunk, kind) != null) return false;

    var random = context.random();
    boolean preferUpper = random.nextInt(4) != 0;
    boolean lit = kind == CliffSite.Kind.ROOTS && random.nextInt(10) == 0;
    CliffSite site = find(level, terrain, chunk.getPos(), random, kind, preferUpper, lit);
    if (site == null) {
      site = find(level, terrain, chunk.getPos(), random, kind, !preferUpper, lit);
    }
    if (site == null) return false;

    CliffHabitats.store(chunk, site);
    return true;
  }

  private static @Nullable CliffSite find(
      WorldGenLevel level,
      IslandTerrain terrain,
      ChunkPos chunk,
      RandomSource random,
      CliffSite.Kind kind,
      boolean upper,
      boolean lit) {
    int minimum = level.getMinBuildHeight() + MINIMUM_AIR_DROP + 2;
    int bottom = upper ? Math.max(minimum, 0) : minimum;
    int ceiling = upper ? level.getMaxBuildHeight() - 2 : -1;
    var cursor = new BlockPos.MutableBlockPos();
    for (int attempt = 0; attempt < COLUMN_ATTEMPTS; attempt++) {
      int x = chunk.getBlockX(4 + random.nextInt(8));
      int z = chunk.getBlockZ(4 + random.nextInt(8));
      int top = Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), ceiling);
      var facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
      for (int y = top; y >= bottom; y--) {
        cursor.set(x, y, z);
        if (kind == CliffSite.Kind.GULLS) {
          if (!perch(level, cursor)) continue;
          for (Direction direction : Direction.Plane.HORIZONTAL) {
            var site = new CliffSite(kind, cursor.immutable(), direction);
            if (gullLedge(level, terrain, site)) return site;
          }
        } else if (sturdyRoof(level, cursor) && underside(level, terrain, cursor)) {
          if (kind == CliffSite.Kind.ROOTS
              && !level.getBiome(cursor.above()).value().hasPrecipitation()) {
            continue;
          }
          for (int turn = 0; turn < 4; turn++) {
            var site = new CliffSite(kind, cursor.immutable(), facing);
            if (kind == CliffSite.Kind.BATS && batRoof(level, terrain, site)) {
              if (icicleRisk(level, site.anchor())) break;
              return site;
            }
            if (kind == CliffSite.Kind.ROOTS && placeCurtain(level, site, random, lit)) {
              return lit ? site.withLight() : site;
            }
            facing = facing.getClockWise();
          }
        }
      }
    }
    return null;
  }

  private static boolean gullLedge(WorldGenLevel level, IslandTerrain terrain, CliffSite site) {
    BlockPos anchor = site.anchor();
    Direction facing = site.facing();
    BlockPos back = anchor.relative(facing.getOpposite());
    BlockPos front = anchor.relative(facing);
    BlockPos edge = anchor.relative(facing, 2);
    return (level.getBlockState(back).isFaceSturdy(level, back, facing)
            || level.getBlockState(back.above()).isFaceSturdy(level, back.above(), facing))
        && flightSpace(level, front)
        && flightSpace(level, front.above())
        && clearBelow(level, edge)
        && !insideTerrain(terrain, edge.below(5))
        && perch(level, site.ledge(-1))
        && perch(level, site.ledge(1));
  }

  static boolean perch(LevelReader level, BlockPos position) {
    var base = position.below();
    return level.getBlockState(base).isFaceSturdy(level, base, Direction.UP)
        && flightSpace(level, position)
        && flightSpace(level, position.above());
  }

  static boolean flightSpace(LevelReader level, BlockPos position) {
    var state = level.getBlockState(position);
    return state.isAir()
        || (state.getFluidState().isEmpty() && state.getCollisionShape(level, position).isEmpty());
  }

  private static boolean batRoof(WorldGenLevel level, IslandTerrain terrain, CliffSite site) {
    return stableBatRest(level, site.anchor())
        && stableBatRest(level, site.ledge(-1))
        && stableBatRest(level, site.ledge(1))
        && openBatCorridor(level, site)
        && !insideTerrain(terrain, site.anchor().relative(site.facing(), 2));
  }

  static boolean usableBatRoof(LevelReader level, CliffSite site) {
    return batRest(level, site.anchor())
        && batRest(level, site.ledge(-1))
        && batRest(level, site.ledge(1))
        && openBatCorridor(level, site);
  }

  private static boolean openBatCorridor(LevelReader level, CliffSite site) {
    return level.isEmptyBlock(site.anchor().relative(site.facing()))
        && clearBelow(level, site.anchor().relative(site.facing(), 2));
  }

  private static boolean stableBatRest(WorldGenLevel level, BlockPos position) {
    return batRest(level, position) && !level.getBlockState(position.above()).is(BlockTags.ICE);
  }

  private static boolean icicleRisk(WorldGenLevel level, BlockPos anchor) {
    // Icicle clusters sample the biome only at their origin and can reach across biome borders.
    int minY = Math.max(92, anchor.getY() - 20);
    int maxY = Math.min(256, anchor.getY() + 20);
    return minY <= maxY
        && nearbyBiome(level, anchor, minY - 4, maxY + 4, biome -> biome.is(ICICLE_BIOMES));
  }

  private static boolean nearbyBiome(
      WorldGenLevel level, BlockPos anchor, int minY, int maxY, Predicate<Holder<Biome>> matches) {
    for (int x = QuartPos.fromBlock(anchor.getX() - 12);
        x <= QuartPos.fromBlock(anchor.getX() + 12);
        x++) {
      for (int z = QuartPos.fromBlock(anchor.getZ() - 12);
          z <= QuartPos.fromBlock(anchor.getZ() + 12);
          z++) {
        for (int y = QuartPos.fromBlock(minY); y <= QuartPos.fromBlock(maxY); y++) {
          if (matches.test(level.getNoiseBiome(x, y, z))) return true;
        }
      }
    }
    return false;
  }

  static boolean batRest(LevelReader level, BlockPos position) {
    return level.isEmptyBlock(position)
        && level.getBlockState(position.above()).isRedstoneConductor(level, position);
  }

  private static boolean sturdyRoof(LevelReader level, BlockPos position) {
    var cap = position.above();
    return level.isEmptyBlock(position)
        && level.getBlockState(cap).isFaceSturdy(level, cap, Direction.DOWN);
  }

  private static boolean underside(WorldGenLevel level, IslandTerrain terrain, BlockPos position) {
    return level.getBlockState(position.above(2)).canOcclude()
        && clearBelow(level, position)
        && !insideTerrain(terrain, position.below(5));
  }

  private static boolean clearBelow(LevelReader level, BlockPos position) {
    var cursor = position.mutable();
    for (int depth = 0; depth < MINIMUM_AIR_DROP; depth++) {
      if (!level.isEmptyBlock(cursor)) return false;
      cursor.move(Direction.DOWN);
    }
    return true;
  }

  private static boolean insideTerrain(IslandTerrain terrain, BlockPos position) {
    return ExposedIslandSurfaces.interpolated(
            position.getX(),
            position.getY(),
            position.getZ(),
            (x, y, z) -> terrain.biomeDensity(new SinglePointContext(x, y, z)))
        > 0;
  }

  private static boolean placeCurtain(
      WorldGenLevel level, CliffSite site, RandomSource random, boolean lit) {
    Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    int roots = 0;
    int vines = 0;
    for (int offset = -CURTAIN_RADIUS; offset <= CURTAIN_RADIUS; offset++) {
      boolean vine = offset != 0 && offset % 2 == 0;
      BlockPos column = curtainColumn(level, site.ledge(offset), vine ? MINIMUM_VINE_LENGTH : 2);
      if (column == null) continue;
      if (!vine) {
        blocks.put(column, Blocks.HANGING_ROOTS.defaultBlockState());
        roots++;
      } else {
        int length = 3 + random.nextInt(5);
        int clear = MINIMUM_VINE_LENGTH;
        while (clear < length && level.isEmptyBlock(column.below(clear))) clear++;
        for (int depth = 0; depth < clear - 1; depth++) {
          blocks.put(
              column.below(depth),
              Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(CaveVines.BERRIES, false));
        }
        blocks.put(
            column.below(clear - 1),
            Blocks.CAVE_VINES
                .defaultBlockState()
                .setValue(GrowingPlantHeadBlock.AGE, GrowingPlantHeadBlock.MAX_AGE));
        vines++;
      }
    }
    if (roots < MINIMUM_ROOTS || vines < 2) return false;
    var anchor = site.anchor();
    if (nearbyBiome(
        level, anchor, anchor.getY() - 8, anchor.getY() + 8, biome -> biome.is(Biomes.DEEP_DARK)))
      return false;
    if (lit && !addLight(level, site, blocks)) return false;
    blocks.forEach((position, state) -> level.setBlock(position, state, Block.UPDATE_CLIENTS));
    return true;
  }

  private static @Nullable BlockPos curtainColumn(WorldGenLevel level, BlockPos target, int depth) {
    for (int step : ROOF_STEPS) {
      var position = target.above(step);
      if (!sturdyRoof(level, position) || level.getBlockState(position.above()).is(BlockTags.ICE))
        continue;
      int clear = 1;
      while (clear < depth && level.isEmptyBlock(position.below(clear))) clear++;
      if (clear == depth) return position;
    }
    return null;
  }

  private static boolean addLight(
      WorldGenLevel level, CliffSite site, Map<BlockPos, BlockState> blocks) {
    var lichen = (MultifaceBlock) Blocks.GLOW_LICHEN;
    for (Direction direction : new Direction[] {site.facing(), site.facing().getOpposite()}) {
      var position = curtainColumn(level, site.anchor().relative(direction), 1);
      if (position == null) continue;
      var state =
          lichen.getStateForPlacement(
              Blocks.AIR.defaultBlockState(), level, position, Direction.UP);
      if (state == null) continue;
      blocks.put(position, state);
      return true;
    }
    return false;
  }
}
