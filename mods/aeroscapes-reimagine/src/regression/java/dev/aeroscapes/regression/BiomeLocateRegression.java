package dev.aeroscapes.regression;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.aeroscapes.worldgen.BiomeTerrainMask;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Compares the transformed native locate loop with Minecraft 1.21.1 traversal and tied caches. */
public final class BiomeLocateRegression {
  private BiomeLocateRegression() {}

  private record Case(
      String name,
      BlockPos origin,
      int radius,
      int horizontalStep,
      Predicate<Holder<Biome>> predicate) {}

  private record Search(Pair<BlockPos, Holder<Biome>> result, int queries) {}

  public static void verify(ServerLevel level) {
    require(level.getServer().isDedicatedServer(), "Use a disposable dedicated server");
    BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
    Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
    Holder<Biome> plains = biome(level, "minecraft:plains");
    Holder<Biome> forest = biome(level, "minecraft:forest");
    Holder<Biome> empty = biome(level, "aeroscapes_reimagine:void");
    Holder<Biome> rare = biome(level, "terralith:glacial_chasm");
    require(source.possibleBiomes().contains(rare), "Rare locate control is not source-eligible");
    long deadline = System.nanoTime() + 180_000_000_000L;
    List<Case> cases =
        List.of(
            new Case("empty-predicate", new BlockPos(0, 96, 0), 512, 64, ignored -> false),
            new Case("common", new BlockPos(0, 96, 0), 128, 32, plains::equals),
            new Case(
                "negative-coordinates", new BlockPos(-1025, 96, -769), 256, 32, forest::equals),
            new Case("rare-eligible", new BlockPos(357, 112, -1345), 512, 64, rare::equals),
            new Case(
                "top-origin",
                new BlockPos(-231, level.getMaxBuildHeight(), -407),
                128,
                32,
                empty::equals),
            new Case(
                "bottom-origin",
                new BlockPos(-513, level.getMinBuildHeight() + 1, 257),
                256,
                64,
                empty::equals),
            new Case(
                "overworld-tag-or-void",
                new BlockPos(-83, 318, -137),
                128,
                32,
                holder -> holder.is(BiomeTags.IS_OVERWORLD) || holder.equals(empty)));
    for (Case test : cases) {
      prime(source, sampler, test.origin());
      int[] nativePredicateCalls = {0};
      Search expected =
          nativeTraversal(
              source,
              sampler,
              level,
              test,
              counted(test.predicate(), nativePredicateCalls),
              deadline);
      prime(source, sampler, test.origin());
      int[] optimizedPredicateCalls = {0};
      checkDeadline(deadline);
      Pair<BlockPos, Holder<Biome>> actual =
          source.findClosestBiome3d(
              test.origin(),
              test.radius(),
              test.horizontalStep(),
              64,
              counted(test.predicate(), optimizedPredicateCalls),
              sampler,
              level);
      checkDeadline(deadline);
      require(
          !((IslandBiomeSampler) (Object) sampler).aeroscapesReimagine$deferTerrainMask(),
          "Locate mutated the live sampler's deferral state");
      require(
          Objects.equals(expected.result(), actual),
          "Locate result mismatch " + test.name() + ": " + expected.result() + " / " + actual);
      require(
          nativePredicateCalls[0] == optimizedPredicateCalls[0],
          "Locate repeated its predicate " + test.name());
      if (test.name().equals("empty-predicate"))
        require(expected.queries() == 0 && actual == null, "Empty matching set queried biomes");
      LogUtils.getLogger()
          .info(
              "Biome locate parity case={} origin={} radius={} controlQueries={} result={}",
              test.name(),
              test.origin(),
              test.radius(),
              expected.queries(),
              actual);
    }
    synthetic(level, sampler, plains, forest, empty, deadline);
    LogUtils.getLogger()
        .info(
            "Biome locate regression passed: {} live cases; independent tied-cache traces; shared live"
                + " source primed identically without claiming cache isolation",
            cases.size());
  }

  private static Holder<Biome> biome(ServerLevel level, String id) {
    return level
        .registryAccess()
        .registryOrThrow(Registries.BIOME)
        .getHolderOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.parse(id)));
  }

  private static Predicate<Holder<Biome>> counted(Predicate<Holder<Biome>> predicate, int[] calls) {
    return holder -> {
      calls[0]++;
      return predicate.test(holder);
    };
  }

  private static void prime(BiomeSource source, Climate.Sampler sampler, BlockPos origin) {
    for (int offset : new int[] {-64, 32, -16, 0})
      source.getNoiseBiome(
          QuartPos.fromBlock(origin.getX() + offset),
          QuartPos.fromBlock(origin.getY()),
          QuartPos.fromBlock(origin.getZ() - offset),
          sampler);
  }

  private static Search nativeTraversal(
      BiomeSource source,
      Climate.Sampler sampler,
      ServerLevel level,
      Case test,
      Predicate<Holder<Biome>> predicate,
      long deadline) {
    Set<Holder<Biome>> matching =
        source.possibleBiomes().stream().filter(predicate).collect(Collectors.toUnmodifiableSet());
    if (matching.isEmpty()) return new Search(null, 0);
    int radius = Math.floorDiv(test.radius(), test.horizontalStep());
    int[] heights =
        Mth.outFromOrigin(
                test.origin().getY(), level.getMinBuildHeight() + 1, level.getMaxBuildHeight(), 64)
            .toArray();
    int queries = 0;
    for (BlockPos.MutableBlockPos offset :
        BlockPos.spiralAround(BlockPos.ZERO, radius, Direction.EAST, Direction.SOUTH)) {
      int x = test.origin().getX() + offset.getX() * test.horizontalStep();
      int z = test.origin().getZ() + offset.getZ() * test.horizontalStep();
      for (int y : heights) {
        checkDeadline(deadline);
        queries++;
        Holder<Biome> holder =
            source.getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
        if (matching.contains(holder))
          return new Search(Pair.of(new BlockPos(x, y, z), holder), queries);
      }
    }
    return new Search(null, queries);
  }

  private static void synthetic(
      ServerLevel level,
      Climate.Sampler original,
      Holder<Biome> plains,
      Holder<Biome> forest,
      Holder<Biome> empty,
      long deadline) {
    List<Case> cases =
        List.of(
            new Case("tied-matching-candidate", new BlockPos(0, 96, 0), 128, 32, forest::equals),
            new Case("tied-void-candidate", new BlockPos(0, 96, 0), 128, 32, empty::equals),
            new Case("tied-empty-predicate", new BlockPos(0, 96, 0), 128, 32, ignored -> false));
    for (Case test : cases) {
      ProbeSource control = new ProbeSource(plains, forest, empty, original, level);
      ProbeSource optimized = new ProbeSource(plains, forest, empty, original, level);
      Search expected =
          nativeTraversal(control, control.sampler, level, test, test.predicate(), deadline);
      Pair<BlockPos, Holder<Biome>> actual =
          optimized.findClosestBiome3d(
              test.origin(),
              test.radius(),
              test.horizontalStep(),
              64,
              test.predicate(),
              optimized.sampler,
              level);
      require(Objects.equals(expected.result(), actual), "Synthetic locate result " + test.name());
      require(
          control.trace.equals(optimized.trace), "Delegate query/selection order " + test.name());
      require(
          !((IslandBiomeSampler) (Object) optimized.sampler).aeroscapesReimagine$deferTerrainMask(),
          "Locate mutated the caller sampler's deferral state");
      if (test.name().equals("tied-matching-candidate")) {
        require(actual != null, "Matching synthetic control has no positive result");
        require(
            control.nodeComputations > optimized.nodeComputations,
            "Locate did not skip terrain-node work for rejected candidates");
      }
      LogUtils.getLogger()
          .info(
              "Biome locate synthetic case={} equalDelegateQueries={} nativeMaskCalls={} terrainNodes={}/{}",
              test.name(),
              control.trace.size(),
              control.normalMaskCalls,
              control.nodeComputations,
              optimized.nodeComputations);
    }
  }

  private record Query(BlockPos quartPosition, Holder<Biome> selected) {}

  private static final class ProbeSource extends BiomeSource {
    private final Holder<Biome> plains;
    private final Holder<Biome> forest;
    private final Holder<Biome> empty;
    private final Climate.ParameterList<Holder<Biome>> parameters;
    private final Climate.Sampler original;
    private final Climate.Sampler sampler;
    private final BiomeTerrainMask mask;
    private final List<Query> trace = new ArrayList<>();
    private int nodeComputations;
    private int normalMaskCalls;

    private ProbeSource(
        Holder<Biome> plains,
        Holder<Biome> forest,
        Holder<Biome> empty,
        Climate.Sampler original,
        ServerLevel level) {
      this.plains = plains;
      this.forest = forest;
      this.empty = empty;
      this.original = original;
      parameters =
          new Climate.ParameterList<>(
              List.of(
                  Pair.of(Climate.parameters(-0.5F, 0, 0, 0, 0, 0, 0), plains),
                  Pair.of(Climate.parameters(0.5F, 0, 0, 0, 0, 0, 0), forest)));
      select(-0.5F);
      require(select(0).equals(plains), "Native tie control lost its first cached leaf");
      select(0.5F);
      require(select(0).equals(forest), "Native tie control is not cache-sensitive");
      select(-0.5F);
      mask =
          new BiomeTerrainMask(
              (x, y, z) -> {
                nodeComputations++;
                return x >= 16;
              },
              level.getMinBuildHeight(),
              level.getMaxBuildHeight(),
              4,
              8);
      sampler =
          new Climate.Sampler(
              original.temperature(),
              original.humidity(),
              original.continentalness(),
              original.erosion(),
              original.depth(),
              original.weirdness(),
              original.spawnTarget());
      ((IslandBiomeSampler) (Object) sampler).aeroscapesReimagine$terrainMask(mask);
    }

    private Holder<Biome> select(float temperature) {
      return parameters.findValue(Climate.target(temperature, 0, 0, 0, 0, 0));
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler supplied) {
      require(
          supplied.temperature() == original.temperature()
              && supplied.humidity() == original.humidity()
              && supplied.continentalness() == original.continentalness()
              && supplied.erosion() == original.erosion()
              && supplied.depth() == original.depth()
              && supplied.weirdness() == original.weirdness()
              && supplied.spawnTarget() == original.spawnTarget(),
          "Locate changed sampler functions");
      IslandBiomeSampler context = (IslandBiomeSampler) (Object) supplied;
      require(context.aeroscapesReimagine$terrainMask() == mask, "Locate changed the terrain mask");
      context.aeroscapesReimagine$voidBiome(empty);
      Holder<Biome> selected = select(x == 0 ? 0 : x > 0 ? 0.5F : -0.5F);
      trace.add(new Query(new BlockPos(x, y, z), selected));
      if (context.aeroscapesReimagine$deferTerrainMask()) return selected;
      normalMaskCalls++;
      return mask.nearTerrain(x * 4, y * 4, z * 4) ? selected : empty;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
      return Stream.of(plains, forest, empty);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
      throw new UnsupportedOperationException("Fixture source is never serialized");
    }
  }

  private static void checkDeadline(long deadline) {
    require(System.nanoTime() <= deadline, "Biome locate regression exceeded 180 seconds");
  }

  private static void require(boolean accepted, String message) {
    if (!accepted) throw new IllegalStateException(message);
  }
}
