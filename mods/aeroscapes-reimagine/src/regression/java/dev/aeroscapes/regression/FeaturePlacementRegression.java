package dev.aeroscapes.regression;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.feature.BasaltClusterFeature;
import dev.aeroscapes.worldgen.feature.CheckedBlockPlacement;
import dev.aeroscapes.worldgen.feature.DistanceTestType;
import dev.aeroscapes.worldgen.feature.PillarFeature;
import dev.aeroscapes.worldgen.feature.PillarFeatureConfig;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.DripstoneClusterConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;

/** Controlled terrain with real feature implementations, block tags and seeded providers. */
final class FeaturePlacementRegression {
  private FeaturePlacementRegression() {}

  private static final JsonObject EXPECTED = expected();
  private static final Set<String> CHECKED = new HashSet<>();

  static void verify(ServerLevel level) {
    verifyControlled(level);
    MineshaftPlacementRegression.verify(level);
    if (CHECKED.size() != EXPECTED.size()) {
      throw new IllegalStateException("Feature parity case coverage changed");
    }
    LogUtils.getLogger().info("Feature placement regression passed: {} cases", CHECKED.size());
  }

  static void verifyControlled(ServerLevel level) {
    if (level.getSeed() != 1) throw new IllegalStateException("Use host seed 1 for feature parity");
    CHECKED.clear();
    for (long seed : List.of(1L, 42L, -1L, 777L)) {
      for (DistanceTestType metric : DistanceTestType.values()) {
        for (int variant = 0; variant < 3; variant++) {
          var materials =
              new CheckedBlockPlacement(
                  List.of(
                      Pair.of(
                          BlockPredicate.alwaysTrue(),
                          new WeightedStateProvider(
                              SimpleWeightedRandomList.<BlockState>builder()
                                  .add(Blocks.STONE.defaultBlockState(), 2)
                                  .add(Blocks.BASALT.defaultBlockState(), 1))),
                      Pair.of(
                          BlockPredicate.matchesBlocks(Blocks.BASALT),
                          BlockStateProvider.simple(Blocks.TUFF))));
          var config =
              new PillarFeatureConfig(
                  materials,
                  UniformInt.of(8, 14),
                  UniformInt.of(3, 5),
                  ConstantInt.of(variant == 0 ? 0 : 4),
                  ConstantInt.of(variant == 2 ? 10 : 0),
                  UniformFloat.of(0.04F, 0.13F),
                  UniformFloat.of(0.4F, 0.8F),
                  SimpleWeightedRandomList.<DistanceTestType>builder().add(metric, 1).build(),
                  Optional.empty());
          run(
              level,
              "pillar/" + metric + "/" + variant,
              seed,
              0,
              new PillarFeature(PillarFeatureConfig.CODEC),
              config);
        }
      }
      for (int terrain = 0; terrain < 8; terrain++) {
        var config =
            new DripstoneClusterConfiguration(
                20,
                UniformInt.of(6, 16),
                UniformInt.of(2, 4),
                3,
                2,
                UniformInt.of(1, 3),
                ConstantFloat.of(1),
                ConstantFloat.of(1),
                1,
                2,
                4);
        run(
            level,
            "cluster/" + terrain,
            seed,
            terrain,
            new BasaltClusterFeature(DripstoneClusterConfiguration.CODEC),
            config);
      }
    }
    long expected =
        EXPECTED.keySet().stream()
            .filter(name -> name.startsWith("pillar/") || name.startsWith("cluster/"))
            .count();
    if (CHECKED.size() != expected) {
      throw new IllegalStateException("Feature parity case coverage changed");
    }
    LogUtils.getLogger().info("Controlled feature regression passed: {} cases", CHECKED.size());
  }

  private static <C extends FeatureConfiguration> void run(
      ServerLevel level, String name, long seed, int terrain, Feature<C> feature, C config) {
    var region = new Region(terrain);
    var randomTrace = new Fingerprint();
    RandomSource delegate = RandomSource.create(seed);
    RandomSource random =
        proxy(
            RandomSource.class,
            (instance, method, arguments) -> {
              randomTrace.add(method.getName() + Arrays.toString(arguments));
              return method.invoke(delegate, arguments);
            });
    boolean placed =
        feature.place(
            new FeaturePlaceContext<>(
                Optional.empty(),
                region.level(),
                level.getChunkSource().getGenerator(),
                random,
                new BlockPos(seed < 0 ? -12 : 12, 40, -18),
                config));
    var result = new Fingerprint();
    result.add(Boolean.toString(placed));
    result.add(region.writes.hex());
    region.blocks.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> result.add(entry.getKey() + ":" + entry.getValue()));
    result.add(randomTrace.hex());
    for (int index = 0; index < 8; index++) result.add(Long.toString(delegate.nextLong()));
    check(name, seed, result.hex(), region.writeCount);
  }

  static void check(String name, long seed, String hash, int writes) {
    String key = name + "/" + seed;
    if (!CHECKED.add(key)) throw new IllegalStateException("Duplicate parity case " + key);
    if (!EXPECTED.has(key) || !hash.equals(EXPECTED.get(key).getAsString())) {
      throw new IllegalStateException("Changed feature output/RNG " + key + ": " + hash);
    }
    LogUtils.getLogger()
        .info("Feature parity {} seed={} hash={} writes={}", name, seed, hash, writes);
  }

  private static JsonObject expected() {
    var stream = FeaturePlacementRegression.class.getResourceAsStream("/feature-placement.json");
    if (stream == null) throw new IllegalStateException("Missing frozen feature parity baseline");
    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("cases");
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot read feature parity baseline", exception);
    }
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
  }

  private static final class Region {
    private final int terrain;
    private final Map<Long, BlockState> blocks = new HashMap<>();
    private final Fingerprint writes = new Fingerprint();
    private int writeCount;

    private Region(int terrain) {
      this.terrain = terrain;
    }

    private BlockState state(BlockPos position) {
      BlockState changed = blocks.get(position.asLong());
      if (changed != null) return changed;
      int y = position.getY();
      if (terrain == 7) return Blocks.STONE.defaultBlockState();
      if (terrain == 6) return Blocks.AIR.defaultBlockState();
      if (terrain != 2 && y <= 32)
        return (terrain == 4 ? Blocks.LAVA : Blocks.STONE).defaultBlockState();
      if (terrain != 1 && y >= (terrain == 5 ? 45 : 52)) return Blocks.BASALT.defaultBlockState();
      if (terrain == 3 && y <= 39) return Blocks.WATER.defaultBlockState();
      return Blocks.AIR.defaultBlockState();
    }

    private WorldGenLevel level() {
      return proxy(
          WorldGenLevel.class,
          (instance, method, arguments) -> {
            Object[] args = arguments == null ? new Object[0] : arguments;
            return switch (method.getName()) {
              case "getBlockState" -> state((BlockPos) args[0]);
              case "getFluidState" -> state((BlockPos) args[0]).getFluidState();
              case "isWaterAt" -> state((BlockPos) args[0]).is(Blocks.WATER);
              case "isStateAtPosition" -> test(args[1], state((BlockPos) args[0]));
              case "getHeight" -> args.length == 0 ? 384 : 33;
              case "getMinBuildHeight" -> -64;
              case "getMaxBuildHeight" -> 320;
              case "setBlock" -> {
                BlockPos position = (BlockPos) args[0];
                BlockState value = (BlockState) args[1];
                blocks.put(position.asLong(), value);
                writes.add(position.asLong() + ":" + value + ":" + args[2]);
                writeCount++;
                yield true;
              }
              default ->
                  throw new UnsupportedOperationException(
                      "Unexpected feature world call " + method);
            };
          });
    }

    @SuppressWarnings(
        "unchecked") // WorldGenLevel.isStateAtPosition's erased Predicate<BlockState>.
    private static boolean test(Object predicate, BlockState state) {
      return ((Predicate<BlockState>) predicate).test(state);
    }
  }

  private static final class Fingerprint {
    private long hash = 0xcbf29ce484222325L;

    private void add(String text) {
      for (int index = 0; index < text.length(); index++)
        hash = (hash ^ text.charAt(index)) * 1099511628211L;
      hash = (hash ^ 0xff) * 1099511628211L;
    }

    private String hex() {
      return Long.toUnsignedString(hash, 16);
    }
  }
}
