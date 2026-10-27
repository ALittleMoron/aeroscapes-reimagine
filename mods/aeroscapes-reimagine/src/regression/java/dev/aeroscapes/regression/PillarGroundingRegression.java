package dev.aeroscapes.regression;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.worldgen.feature.CheckedBlockPlacement;
import dev.aeroscapes.worldgen.feature.DistanceTestType;
import dev.aeroscapes.worldgen.feature.PillarFeature;
import dev.aeroscapes.worldgen.feature.PillarFeatureConfig;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/** Real pillar writes over neighboring island heights, without loading or editing world chunks. */
public final class PillarGroundingRegression {
  private PillarGroundingRegression() {}

  public static void verify(ServerLevel level) {
    require(
        configured(level, "aeroscapes:basalt_pillar").maxGroundDrop().equals(Optional.of(16)),
        "Basalt pillar lost its scoped ground-drop bound");
    require(
        configured(level, "aeroscapes:jungle_pillar").maxGroundDrop().isEmpty(),
        "Jungle pillar unexpectedly received the basalt ground-drop bound");
    codec();
    for (long seed : List.of(1L, 42L, -1L, 777L)) {
      supported(level, seed);
      separatedIslands(level, seed);
      voidEdge(level, seed, 100, -128, 0);
      voidEdge(level, seed, 68, 64, 0);
      // A nonzero native offset makes writes observable despite the retained negative-Y taper.
      voidEdge(level, seed, -124, -128, 124);
      boundary(level, seed, 84, true);
      boundary(level, seed, 83, false);
      boundary(level, seed, 116, true);
      boundary(level, seed, 117, false);
      negativeHeight(level, seed);
    }
    LogUtils.getLogger().info("Scoped basalt pillar grounding regression passed");
  }

  private static PillarFeatureConfig configured(ServerLevel level, String id) {
    var feature =
        level
            .registryAccess()
            .registryOrThrow(Registries.CONFIGURED_FEATURE)
            .get(ResourceLocation.parse(id));
    if (feature == null || !(feature.config() instanceof PillarFeatureConfig config))
      throw new IllegalStateException("Missing pillar configuration " + id);
    return config;
  }

  private static void codec() {
    var encoded =
        PillarFeatureConfig.CODEC
            .encodeStart(JsonOps.INSTANCE, config(Optional.empty(), 0))
            .getOrThrow()
            .getAsJsonObject();
    require(
        PillarFeatureConfig.CODEC
            .parse(JsonOps.INSTANCE, encoded)
            .getOrThrow()
            .maxGroundDrop()
            .isEmpty(),
        "Absent pillar ground-drop bound changed the default");
    encoded.addProperty("max_ground_drop", -1);
    require(
        PillarFeatureConfig.CODEC.parse(JsonOps.INSTANCE, encoded).error().isPresent(),
        "Pillar ground-drop codec accepted a negative bound");
    for (int limit : List.of(0, 16, Integer.MAX_VALUE)) {
      encoded.addProperty("max_ground_drop", limit);
      require(
          PillarFeatureConfig.CODEC
              .parse(JsonOps.INSTANCE, encoded)
              .getOrThrow()
              .maxGroundDrop()
              .equals(Optional.of(limit)),
          "Pillar ground-drop codec lost a nonnegative bound");
    }
  }

  private static void supported(ServerLevel level, long seed) {
    for (IntBinaryOperator surface :
        List.<IntBinaryOperator>of((x, z) -> 100, (x, z) -> 100 + x + z)) {
      var original = place(level, seed, 100, -128, surface, Optional.empty(), 0);
      var guarded = place(level, seed, 100, -128, surface, Optional.of(16), 0);
      require(!guarded.blocks().isEmpty(), "Supported basalt formation became empty");
      require(original.equals(guarded), "Supported pillar output or RNG changed");
    }
  }

  private static void separatedIslands(ServerLevel level, long seed) {
    IntBinaryOperator surface = (x, z) -> x > 0 ? 40 : 100;
    var original = place(level, seed, 100, -128, surface, Optional.empty(), 0);
    var unlimited = place(level, seed, 100, -128, surface, Optional.of(Integer.MAX_VALUE), 0);
    require(original.equals(unlimited), "Unlimited ground-drop bound changed native placement");
    require(
        original.blocks().keySet().stream()
            .anyMatch(position -> position.getX() > 0 && position.getY() == 70),
        "Separated-island fixture no longer reproduces a native void bridge");
    var guarded = place(level, seed, 100, -128, surface, Optional.of(16), 0);
    require(!guarded.blocks().isEmpty(), "Island-edge guard removed the supported formation");
    require(
        guarded.blocks().keySet().stream().noneMatch(position -> position.getX() > 0),
        "Basalt footprint bridged the void to a lower island");
  }

  private static void voidEdge(
      ServerLevel level, long seed, int originY, int minimumY, int heightOffset) {
    IntBinaryOperator surface = (x, z) -> x > 0 ? minimumY : originY;
    var guarded = place(level, seed, originY, minimumY, surface, Optional.of(16), heightOffset);
    require(!guarded.blocks().isEmpty(), "Void-edge fixture lost its supported half");
    require(
        guarded.blocks().keySet().stream().noneMatch(position -> position.getX() > 0),
        "Basalt grew above an empty heightmap column");
  }

  private static void boundary(ServerLevel level, long seed, int neighborY, boolean accepted) {
    IntBinaryOperator surface = (x, z) -> x > 0 ? neighborY : 100;
    var result = place(level, seed, 100, -128, surface, Optional.of(16), 0);
    boolean neighborWritten =
        result.blocks().keySet().stream().anyMatch(position -> position.getX() > 0);
    require(
        neighborWritten == accepted,
        "Incorrect pillar ground-drop boundary at neighbor surface " + neighborY);
  }

  private static void negativeHeight(ServerLevel level, long seed) {
    IntBinaryOperator flat = (x, z) -> -40;
    var original = place(level, seed, -40, -128, flat, Optional.empty(), 0);
    var unlimited = place(level, seed, -40, -128, flat, Optional.of(Integer.MAX_VALUE), 0);
    require(original.equals(unlimited), "Negative-Y native default output or RNG changed");
    var offsetOriginal = place(level, seed, -40, -128, flat, Optional.empty(), 40);
    var offsetUnlimited = place(level, seed, -40, -128, flat, Optional.of(Integer.MAX_VALUE), 40);
    require(
        offsetOriginal.equals(offsetUnlimited),
        "Negative-height unrestricted offset output or RNG changed");
    var supported = place(level, seed, -40, -128, flat, Optional.of(16), 40);
    require(!supported.blocks().isEmpty(), "Negative-height offset fixture became vacuous");
    require(
        supported.equals(offsetOriginal), "Supported negative-height offset output or RNG changed");
    IntBinaryOperator nearby = (x, z) -> x > 0 ? -56 : -40;
    var accepted = place(level, seed, -40, -128, nearby, Optional.of(16), 40);
    require(
        accepted.blocks().keySet().stream().anyMatch(position -> position.getX() > 0),
        "Negative-height sixteen-block boundary was rejected");
    IntBinaryOperator edge = (x, z) -> x > 0 ? -57 : -40;
    var guarded = place(level, seed, -40, -128, edge, Optional.of(16), 40);
    require(!guarded.blocks().isEmpty(), "Negative-height guard removed supported columns");
    require(
        guarded.blocks().keySet().stream().noneMatch(position -> position.getX() > 0),
        "Negative-height ground difference bypassed the bound");
  }

  private static PillarFeatureConfig config(Optional<Integer> limit, int heightOffset) {
    return new PillarFeatureConfig(
        new CheckedBlockPlacement(
            List.of(
                Pair.of(
                    BlockPredicate.alwaysTrue(),
                    BlockStateProvider.simple(Blocks.BASALT.defaultBlockState())))),
        ConstantInt.of(16),
        ConstantInt.of(3),
        ConstantInt.of(0),
        ConstantInt.of(heightOffset),
        ConstantFloat.of(0.05F),
        ConstantFloat.of(1),
        SimpleWeightedRandomList.<DistanceTestType>builder()
            .add(DistanceTestType.CHEBYSHEV, 1)
            .build(),
        limit);
  }

  private static Result place(
      ServerLevel level,
      long seed,
      int originY,
      int minimumY,
      IntBinaryOperator surface,
      Optional<Integer> limit,
      int heightOffset) {
    Map<BlockPos, BlockState> blocks = new HashMap<>();
    WorldGenLevel world =
        WorldGenLevel.class.cast(
            Proxy.newProxyInstance(
                WorldGenLevel.class.getClassLoader(),
                new Class<?>[] {WorldGenLevel.class},
                (instance, method, arguments) -> {
                  Object[] args = arguments == null ? new Object[0] : arguments;
                  return switch (method.getName()) {
                    case "getHeight" -> surface.applyAsInt((Integer) args[1], (Integer) args[2]);
                    case "getMinBuildHeight" -> minimumY;
                    case "setBlock" -> {
                      blocks.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                      yield true;
                    }
                    default ->
                        throw new UnsupportedOperationException(
                            "Unexpected pillar world call " + method);
                  };
                }));
    RandomSource random = RandomSource.create(seed);
    boolean placed =
        new PillarFeature(PillarFeatureConfig.CODEC)
            .place(
                new FeaturePlaceContext<>(
                    Optional.empty(),
                    world,
                    level.getChunkSource().getGenerator(),
                    random,
                    new BlockPos(0, originY, 0),
                    config(limit, heightOffset)));
    return new Result(Map.copyOf(blocks), placed, random.nextLong());
  }

  private record Result(Map<BlockPos, BlockState> blocks, boolean placed, long nextRandom) {}

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
