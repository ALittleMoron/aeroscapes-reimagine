package dev.aeroscapes.sky;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import org.jetbrains.annotations.Nullable;

/**
 * Places the bones of a sky whale that died long ago: on a natural island surface at any layer, or
 * protruding from an island's underside. Every write stays within the origin chunk and its direct
 * neighbours, and only natural ground or open air is replaced.
 */
public final class SkyWhaleFallFeature extends Feature<SkyWhaleFallFeature.Config> {
  private static final int MAXIMUM_SURFACE_STEP = 3;
  private static final int PLACEMENT_TRIES = 8;
  public static final int CENTRE_JITTER = 2;
  private static final int SPACING = 6;
  private static final int UNDERSIDE_DROP = 24;
  private static final int UNDERSIDE_THICKNESS = 4;
  private static final ResourceLocation WHALE_BONE =
      ResourceLocation.fromNamespaceAndPath("skywhales", "whale_bone_block");
  private static final ResourceLocation CRACKED_WHALE_BONE =
      ResourceLocation.fromNamespaceAndPath("skywhales", "cracked_whale_bone_block");

  public SkyWhaleFallFeature(Codec<Config> codec) {
    super(codec);
  }

  /** Attempts and nanoseconds spent placing, read by the headless generation-cost check. */
  public static final java.util.concurrent.atomic.LongAdder ATTEMPTS =
      new java.util.concurrent.atomic.LongAdder();

  public static final java.util.concurrent.atomic.LongAdder NANOS =
      new java.util.concurrent.atomic.LongAdder();

  @Override
  public boolean place(FeaturePlaceContext<Config> context) {
    long started = System.nanoTime();
    try {
      return placeTimed(context);
    } finally {
      ATTEMPTS.increment();
      NANOS.add(System.nanoTime() - started);
    }
  }

  private boolean placeTimed(FeaturePlaceContext<Config> context) {
    WorldGenLevel level = context.level();
    RandomSource random = context.random();
    var chunk = new ChunkPos(context.origin());
    if (!candidate(level.getSeed(), chunk, context.config().site())) return false;
    var plan = plan(level, chunk, random, context.config().site());
    if (plan == null) return false;
    for (int index = 0; index < plan.positions().size(); index++) {
      level.setBlock(plan.positions().get(index), plan.states().get(index), 2);
    }
    if (context.config().site() == Site.SURFACE) dressSurface(level, plan.positions(), random);
    return true;
  }

  /**
   * One chunk in each 2x2 square, chosen from the seed per site kind, may host a whale fall, so two
   * skeletons never start in neighbouring chunks.
   */
  public static boolean candidate(long seed, ChunkPos chunk, Site site) {
    long mixed = seed * 0x9E3779B97F4A7C15L + (site == Site.SURFACE ? 0x51ED27L : 0xC3A5C85CL);
    mixed ^= mixed >>> 31;
    return (chunk.x & 1) == (int) (mixed & 1) && (chunk.z & 1) == (int) (mixed >>> 1 & 1);
  }

  /** Planned bone blocks, or nothing when no column of the chunk can host the skeleton. */
  public record Plan(Site site, List<BlockPos> positions, List<BlockState> states) {}

  /** Tries several columns and headings near the chunk centre without writing any block. */
  public static @Nullable Plan plan(
      WorldGenLevel level, ChunkPos chunk, RandomSource random, Site site) {
    var bones = SkyWhaleFallShape.create(random);
    for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++) {
      int x = chunk.getMiddleBlockX() - CENTRE_JITTER + random.nextInt(2 * CENTRE_JITTER + 1);
      int z = chunk.getMiddleBlockZ() - CENTRE_JITTER + random.nextInt(2 * CENTRE_JITTER + 1);
      Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
      var plan = plan(level, chunk, bones, x, z, facing, site, random);
      if (plan != null) return plan;
    }
    return null;
  }

  private static @Nullable Plan plan(
      WorldGenLevel level,
      ChunkPos chunk,
      List<SkyWhaleFallShape.Bone> bones,
      int x,
      int z,
      Direction facing,
      Site site,
      RandomSource random) {
    boolean underside = site == Site.UNDERSIDE;
    Integer base =
        underside ? underside(level, x, z, facing) : surface(level, x, z, facing, random);
    if (base == null) return null;
    List<BlockPos> positions = new ArrayList<>();
    List<SkyWhaleFallShape.Bone> placed = new ArrayList<>();
    int blocked = 0;
    for (var bone : bones) {
      BlockPos column = SkyWhaleFallShape.rotate(x, z, bone.x(), bone.z(), facing);
      Integer y;
      if (bone.grounded()) {
        // Ground-level bones follow the local surface: half sunk into the ground, or set into the
        // underside rock where they show from below.
        y = underside ? ceilingAt(level, column, base) : groundAt(level, column, base);
      } else {
        // The arch keeps its shape: above the ground, or hanging below the underside.
        y = underside ? base - bone.y() : base - 1 + bone.y();
      }
      BlockPos position = y == null ? null : column.atY(y);
      if (position == null
          || !(level.getBlockState(position).canBeReplaced()
              || bone.grounded() && natural(level.getBlockState(position)))) {
        blocked++;
        continue;
      }
      positions.add(position);
      placed.add(bone);
    }
    if (blocked * 4 >= bones.size() || nearOtherBones(level, chunk, positions)) return null;
    List<BlockState> states = new ArrayList<>();
    for (var bone : SkyWhaleFallShape.withMaterials(placed, random))
      states.add(state(bone, facing));
    return new Plan(site, positions, states);
  }

  /**
   * Keeps whale falls apart: a site whose readable area already holds bone blocks, such as a
   * neighbouring chunk's skeleton, is rejected. The search stays inside the decoration region.
   */
  private static boolean nearOtherBones(
      WorldGenLevel level, ChunkPos origin, List<BlockPos> positions) {
    int minimumX = Integer.MAX_VALUE;
    int minimumY = Integer.MAX_VALUE;
    int minimumZ = Integer.MAX_VALUE;
    int maximumX = Integer.MIN_VALUE;
    int maximumY = Integer.MIN_VALUE;
    int maximumZ = Integer.MIN_VALUE;
    for (var position : positions) {
      minimumX = Math.min(minimumX, position.getX());
      minimumY = Math.min(minimumY, position.getY());
      minimumZ = Math.min(minimumZ, position.getZ());
      maximumX = Math.max(maximumX, position.getX());
      maximumY = Math.max(maximumY, position.getY());
      maximumZ = Math.max(maximumZ, position.getZ());
    }
    int regionMinimumX = (origin.x - 1) << 4;
    int regionMinimumZ = (origin.z - 1) << 4;
    var cursor = new BlockPos.MutableBlockPos();
    for (int x = Math.max(minimumX - SPACING, regionMinimumX);
        x <= Math.min(maximumX + SPACING, regionMinimumX + 47);
        x++) {
      for (int z = Math.max(minimumZ - SPACING, regionMinimumZ);
          z <= Math.min(maximumZ + SPACING, regionMinimumZ + 47);
          z++) {
        for (int y = minimumY - SPACING; y <= maximumY + SPACING; y++) {
          var state = level.getBlockState(cursor.set(x, y, z));
          if (state.is(Blocks.BONE_BLOCK)
              || state.is(provided(WHALE_BONE))
              || state.is(provided(CRACKED_WHALE_BONE))) return true;
        }
      }
    }
    return false;
  }

  private static @Nullable Integer surface(
      WorldGenLevel level, int x, int z, Direction facing, RandomSource random) {
    // Open void between islands has no surface at all: reject it before scanning the column.
    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    if (top <= level.getMinBuildHeight() + 8) return null;
    List<Integer> candidates = new ArrayList<>();
    for (int y = Math.min(top, level.getMaxBuildHeight() - 8);
        y > level.getMinBuildHeight() + 8;
        y--) {
      BlockPos position = new BlockPos(x, y, z);
      if (natural(level.getBlockState(position))
          && level.getFluidState(position).isEmpty()
          && open(level, position.above(), 8)) candidates.add(y + 1);
    }
    if (candidates.isEmpty()) return null;
    int base = candidates.get(random.nextInt(candidates.size()));
    // The body needs land under its whole length and both rib rows, not a narrow rim.
    for (int[] sample :
        new int[][] {
          {0, -19}, {0, -12}, {0, -4}, {0, 4}, {0, 12}, {0, 17}, {-5, 0}, {5, 0}, {-4, -8}, {4, -8},
          {-4, 6}, {4, 6}
        }) {
      Integer ground =
          groundAt(level, SkyWhaleFallShape.rotate(x, z, sample[0], sample[1], facing), base);
      if (ground == null || Math.abs(ground - base) > MAXIMUM_SURFACE_STEP) return null;
    }
    return base;
  }

  private static @Nullable Integer underside(WorldGenLevel level, int x, int z, Direction facing) {
    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    if (top <= level.getMinBuildHeight() + UNDERSIDE_DROP + 2) return null;
    for (int y = level.getMinBuildHeight() + UNDERSIDE_DROP + 2;
        y < Math.min(top, level.getMaxBuildHeight() - 8);
        y++) {
      if (undersideAt(level, new BlockPos(x, y, z)) && undersideFootprint(level, x, z, y, facing))
        return y;
    }
    return null;
  }

  private static boolean undersideFootprint(
      WorldGenLevel level, int x, int z, int base, Direction facing) {
    for (int[] sample :
        new int[][] {
          {0, -19}, {0, -10}, {0, 0}, {0, 8}, {0, 16}, {-4, -6}, {4, -6}, {-4, 4}, {4, 4}
        }) {
      BlockPos column = SkyWhaleFallShape.rotate(x, z, sample[0], sample[1], facing);
      boolean found = false;
      for (int offset = -MAXIMUM_SURFACE_STEP; offset <= MAXIMUM_SURFACE_STEP && !found; offset++) {
        found = undersideAt(level, column.atY(base + offset));
      }
      if (!found) return false;
    }
    return true;
  }

  /** Solid natural rock with enough thickness above and open sky below it. */
  private static boolean undersideAt(WorldGenLevel level, BlockPos position) {
    for (int above = 0; above < UNDERSIDE_THICKNESS; above++) {
      if (!natural(level.getBlockState(position.above(above)))) return false;
    }
    for (int below = 1; below <= UNDERSIDE_DROP; below++) {
      if (!level.getBlockState(position.below(below)).isAir()) return false;
    }
    return true;
  }

  /** The height where a grounded bone sits: the top natural block near the arch base. */
  private static @Nullable Integer groundAt(WorldGenLevel level, BlockPos column, int base) {
    for (int y = base + MAXIMUM_SURFACE_STEP; y >= base - MAXIMUM_SURFACE_STEP - 1; y--) {
      BlockPos position = column.atY(y);
      if (natural(level.getBlockState(position))
          && level.getFluidState(position).isEmpty()
          && level.getBlockState(position.above()).canBeReplaced()
          && level.getFluidState(position.above()).isEmpty()) return y;
    }
    return null;
  }

  /** The underside block nearest the arch base, with open air directly below it. */
  private static @Nullable Integer ceilingAt(WorldGenLevel level, BlockPos column, int base) {
    for (int y = base - MAXIMUM_SURFACE_STEP; y <= base + MAXIMUM_SURFACE_STEP; y++) {
      BlockPos position = column.atY(y);
      if (natural(level.getBlockState(position)) && level.getBlockState(position.below()).isAir())
        return y;
    }
    return null;
  }

  private static boolean open(WorldGenLevel level, BlockPos position, int height) {
    for (int offset = 0; offset < height; offset++) {
      var state = level.getBlockState(position.above(offset));
      if (!state.canBeReplaced() || !state.getFluidState().isEmpty()) return false;
    }
    return true;
  }

  static boolean natural(BlockState state) {
    return state.is(BlockTags.DIRT)
        || state.is(BlockTags.SAND)
        || state.is(BlockTags.BASE_STONE_OVERWORLD)
        || state.is(BlockTags.TERRACOTTA)
        || state.is(Blocks.GRAVEL)
        || state.is(Blocks.CLAY)
        || state.is(Blocks.CALCITE)
        || state.is(Blocks.SNOW_BLOCK)
        || state.is(Blocks.PACKED_ICE)
        || state.is(Blocks.SANDSTONE)
        || state.is(Blocks.RED_SANDSTONE);
  }

  private static void dressSurface(WorldGenLevel level, List<BlockPos> bones, RandomSource random) {
    for (var position : bones) {
      if (random.nextFloat() >= 0.18F) continue;
      BlockPos above = position.above();
      if (level.getBlockState(above).isAir() && !bones.contains(above)) {
        level.setBlock(above, Blocks.MOSS_CARPET.defaultBlockState(), 2);
      }
    }
  }

  private static BlockState state(SkyWhaleFallShape.Bone bone, Direction facing) {
    Block block =
        switch (bone.material()) {
          case WHALE_BONE -> provided(WHALE_BONE);
          case CRACKED_WHALE_BONE -> provided(CRACKED_WHALE_BONE);
          case BONE -> Blocks.BONE_BLOCK;
        };
    BlockState state = block.defaultBlockState();
    if (state.hasProperty(RotatedPillarBlock.AXIS)) {
      Direction.Axis axis = bone.axis();
      if (axis != Direction.Axis.Y && facing.getAxis() == Direction.Axis.X) {
        axis = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
      }
      state = state.setValue(RotatedPillarBlock.AXIS, axis);
    }
    return state;
  }

  private static Block provided(ResourceLocation id) {
    Block block = BuiltInRegistries.BLOCK.get(id);
    return block == Blocks.AIR ? Blocks.BONE_BLOCK : block;
  }

  public enum Site implements StringRepresentable {
    SURFACE("surface"),
    UNDERSIDE("underside");

    public static final Codec<Site> CODEC = StringRepresentable.fromEnum(Site::values);
    private final String name;

    Site(String name) {
      this.name = name;
    }

    @Override
    public String getSerializedName() {
      return name;
    }
  }

  public record Config(Site site) implements FeatureConfiguration {
    public static final Codec<Config> CODEC =
        RecordCodecBuilder.create(
            instance ->
                instance
                    .group(Site.CODEC.fieldOf("site").forGetter(Config::site))
                    .apply(instance, Config::new));
  }
}
