package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.BasinWaterline;
import dev.aeroscapes.worldgen.IslandLayout;
import dev.aeroscapes.worldgen.IslandTerrain;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;

/** Real transformed native ice algorithms on controlled water, without modifying saved terrain. */
public final class FrozenBasinRegression {
  private FrozenBasinRegression() {}

  public static void verify(ServerLevel level) {
    var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
    IslandLayout.Basin basin = basin(level);
    BlockPos center = new BlockPos(basin.centerX(), basin.waterline(), basin.centerZ());
    placements(level, center, "iceberg_packed");
    placements(level, center, "iceberg_blue");
    placements(level, center, "blue_ice");
    placements(level, BlockPos.ZERO, "iceberg_packed");
    placements(level, BlockPos.ZERO, "iceberg_blue");
    placements(level, BlockPos.ZERO, "blue_ice");
    ServerLevel nativeLevel = Objects.requireNonNull(level.getServer().getLevel(Level.NETHER));
    require(
        IslandTerrain.from(nativeLevel.getChunkSource().randomState()) == null,
        "Frozen parity needs a native dimension without island basin adapters");
    var nativeGenerator = nativeGenerator(generator);
    for (long seed : new long[] {1, 42, -1, 777}) {
      feature(
          level,
          nativeLevel,
          generator,
          nativeGenerator,
          center,
          seed,
          Feature.ICEBERG,
          new BlockStateConfiguration(Blocks.PACKED_ICE.defaultBlockState()));
      feature(
          level,
          nativeLevel,
          generator,
          nativeGenerator,
          center,
          seed,
          Feature.BLUE_ICE,
          NoneFeatureConfiguration.INSTANCE);
    }
    surface(level, generator, center);
    LogUtils.getLogger()
        .info(
            "Frozen basin regression passed: native placement/RNG, 8 translated feature cases, local frozen surface at {}",
            center);
  }

  private static IslandLayout.Basin basin(ServerLevel level) {
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    require(terrain != null, "Frozen basin regression needs the island generator");
    for (int x = -16; x <= 16; x++) {
      for (int z = -16; z <= 16; z++) {
        IslandLayout.Basin basin = terrain.basin(x * 1024, z * 1024);
        if (basin != null
            && basin.waterline() - 64 > level.getMinBuildHeight()
            && basin.waterline() + 64 < level.getMaxBuildHeight()) return basin;
      }
    }
    throw new IllegalStateException("No bounded wet basin for the frozen fixture");
  }

  private static void placements(ServerLevel level, BlockPos center, String name) {
    PlacedFeature placed =
        level
            .registryAccess()
            .registryOrThrow(Registries.PLACED_FEATURE)
            .get(ResourceLocation.withDefaultNamespace(name));
    require(placed != null, "Missing native frozen feature " + name);
    var context =
        new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.of(placed));
    int waterline =
        BasinWaterline.at(
            level.getChunkSource().randomState(), center.getX(), center.getZ(), Integer.MIN_VALUE);
    for (long seed : new long[] {1, 42, -1, 777}) {
      RandomSource actual = RandomSource.create(seed);
      RandomSource expected = RandomSource.create(seed);
      for (int sample = 0; sample < 128; sample++) {
        if (name.equals("blue_ice")) {
          var range =
              placed.placement().stream()
                  .filter(HeightRangePlacement.class::isInstance)
                  .map(HeightRangePlacement.class::cast)
                  .findFirst()
                  .orElseThrow();
          var positions = range.getPositions(context, actual, center).toList();
          int height =
              (waterline == Integer.MIN_VALUE ? 30 : waterline - 33) + expected.nextInt(32);
          require(
              positions.size() == 1 && positions.getFirst().getY() == height,
              "Blue ice lost native 30..61 draws relative to local sea");
        } else {
          var square =
              placed.placement().stream()
                  .filter(InSquarePlacement.class::isInstance)
                  .map(InSquarePlacement.class::cast)
                  .findFirst()
                  .orElseThrow();
          var positions =
              square.getPositions(context, actual, center.atY(level.getMinBuildHeight())).toList();
          int x = center.getX() + expected.nextInt(16);
          int z = center.getZ() + expected.nextInt(16);
          int height = waterline == Integer.MIN_VALUE ? level.getMinBuildHeight() : waterline;
          require(
              positions.size() == 1 && positions.getFirst().equals(new BlockPos(x, height, z)),
              "Iceberg lost native in-square draws or reaches biome filtering at the wrong height");
        }
      }
      require(actual.nextLong() == expected.nextLong(), "Frozen placement consumes extra RNG");
    }
  }

  @SuppressWarnings("deprecation") // Copy the pinned settings record's legacy mob flag.
  private static NoiseBasedChunkGenerator nativeGenerator(NoiseBasedChunkGenerator generator) {
    NoiseGeneratorSettings settings = generator.generatorSettings().value();
    return new NoiseBasedChunkGenerator(
        generator.getBiomeSource(),
        Holder.direct(
            new NoiseGeneratorSettings(
                settings.noiseSettings(),
                settings.defaultBlock(),
                settings.defaultFluid(),
                settings.noiseRouter(),
                settings.surfaceRule(),
                settings.spawnTarget(),
                63,
                settings.disableMobGeneration(),
                settings.isAquifersEnabled(),
                settings.oreVeinsEnabled(),
                settings.useLegacyRandomSource())));
  }

  private static <C extends FeatureConfiguration> void feature(
      ServerLevel level,
      ServerLevel nativeLevel,
      ChunkGenerator generator,
      ChunkGenerator nativeGenerator,
      BlockPos center,
      long seed,
      Feature<C> feature,
      C configuration) {
    var actual = new Region(level, center, level.getSeaLevel());
    var expected = new Region(nativeLevel, center.atY(63), 63);
    boolean blue = feature == Feature.BLUE_ICE;
    if (blue) {
      BlockPos neighbor = new BlockPos(-1, -2, 0);
      actual.blocks.put(neighbor, Blocks.PACKED_ICE.defaultBlockState());
      expected.blocks.put(neighbor, Blocks.PACKED_ICE.defaultBlockState());
    }
    RandomSource actualRandom = RandomSource.create(seed);
    RandomSource expectedRandom = RandomSource.create(seed);
    boolean actualPlaced =
        feature.place(
            new FeaturePlaceContext<>(
                Optional.empty(),
                actual.level(),
                generator,
                actualRandom,
                blue ? center.below(2) : center,
                configuration));
    boolean expectedPlaced =
        feature.place(
            new FeaturePlaceContext<>(
                Optional.empty(),
                expected.level(),
                nativeGenerator,
                expectedRandom,
                blue ? center.atY(61) : center.atY(63),
                configuration));
    require(actualPlaced && expectedPlaced, "Controlled native frozen feature did not place");
    require(
        !actual.writes.isEmpty()
            && actual.writes.equals(expected.writes)
            && actual.blocks.equals(expected.blocks),
        "Frozen native feature changed translated geometry or write order");
    for (int sample = 0; sample < 8; sample++)
      require(actualRandom.nextLong() == expectedRandom.nextLong(), "Frozen feature RNG differs");
  }

  private static void surface(
      ServerLevel level, NoiseBasedChunkGenerator generator, BlockPos center) {
    var random = level.getChunkSource().randomState();
    var surface =
        new SurfaceSystem(
            random,
            generator.generatorSettings().value().defaultBlock(),
            level.getSeaLevel(),
            new XoroshiroRandomSource(777).forkPositional());
    var region = new Region(level, center, level.getSeaLevel());
    try {
      var extension =
          SurfaceSystem.class.getDeclaredMethod(
              "frozenOceanExtension",
              int.class,
              net.minecraft.world.level.biome.Biome.class,
              net.minecraft.world.level.chunk.BlockColumn.class,
              BlockPos.MutableBlockPos.class,
              int.class,
              int.class,
              int.class);
      extension.setAccessible(true);
      var biome =
          level.registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(Biomes.FROZEN_OCEAN);
      search:
      for (int x = center.getX() - 64; x <= center.getX() + 64; x += 4) {
        for (int z = center.getZ() - 64; z <= center.getZ() + 64; z += 4) {
          if (BasinWaterline.at(random, x, z, Integer.MIN_VALUE) != center.getY()) continue;
          final int columnX = x;
          final int columnZ = z;
          var column =
              new net.minecraft.world.level.chunk.BlockColumn() {
                @Override
                public BlockState getBlock(int y) {
                  return region.block(new BlockPos(columnX, y, columnZ));
                }

                @Override
                public void setBlock(int y, BlockState state) {
                  region.write(new BlockPos(columnX, y, columnZ), state);
                }
              };
          extension.invoke(
              surface,
              center.getY() - 64,
              biome,
              column,
              new BlockPos.MutableBlockPos(),
              x,
              z,
              center.getY() + 1);
          if (region.blocks.values().stream().anyMatch(state -> state.is(Blocks.PACKED_ICE)))
            break search;
        }
      }
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot execute pinned native frozen surface", exception);
    }
    require(
        region.blocks.values().stream().anyMatch(state -> state.is(Blocks.PACKED_ICE)),
        "Native frozen surface never writes ice at the local basin");
    require(
        BasinWaterline.at(random, 0, 0, Integer.MIN_VALUE) == Integer.MIN_VALUE,
        "Dry cell corner unexpectedly receives basin water semantics");
  }

  private static void require(boolean accepted, String message) {
    if (!accepted) throw new IllegalStateException(message);
  }

  private static final class Region {
    private final ServerLevel host;
    private final BlockPos center;
    private final int seaLevel;
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final List<String> writes = new ArrayList<>();

    private Region(ServerLevel host, BlockPos center, int seaLevel) {
      this.host = host;
      this.center = center;
      this.seaLevel = seaLevel;
    }

    private BlockPos relative(BlockPos position) {
      return position.subtract(center);
    }

    private BlockState block(BlockPos position) {
      BlockState stored = blocks.get(relative(position));
      if (stored != null) return stored;
      return position.getY() <= center.getY()
          ? Blocks.WATER.defaultBlockState()
          : Blocks.AIR.defaultBlockState();
    }

    private boolean write(BlockPos position, BlockState state) {
      BlockPos key = relative(position);
      blocks.put(key, state);
      writes.add(key + ":" + state);
      return true;
    }

    private WorldGenLevel level() {
      return (WorldGenLevel)
          Proxy.newProxyInstance(
              WorldGenLevel.class.getClassLoader(),
              new Class<?>[] {WorldGenLevel.class},
              (instance, method, arguments) -> {
                return switch (method.getName()) {
                  case "getLevel" -> host;
                  case "getSeaLevel" -> seaLevel;
                  case "getBlockState" -> block((BlockPos) arguments[0]);
                  case "setBlock" -> write((BlockPos) arguments[0], (BlockState) arguments[1]);
                  default -> throw new UnsupportedOperationException(method.getName());
                };
              });
    }
  }
}
