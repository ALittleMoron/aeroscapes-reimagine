package dev.aeroscapes.regression;

import com.ishland.c2me.base.mixin.access.IChunkNoiseSampler;
import com.ishland.c2me.opts.dfc.common.ast.EvalType;
import com.ishland.c2me.opts.dfc.common.ast.misc.ConstantNode;
import com.ishland.c2me.opts.dfc.common.ast.misc.YClampedGradientNode;
import com.ishland.c2me.opts.dfc.common.gen.DelegatingBlendingCachingAwareVisitor;
import com.ishland.c2me.opts.dfc.common.gen.jvm.AbstractCompiledDensityFunction;
import com.ishland.c2me.opts.dfc.common.gen.jvm.CompiledEntry;
import com.ishland.c2me.opts.dfc.common.gen.jvm.util.DfcObjectCache;
import com.ishland.c2me.opts.dfc.common.gen.jvm.vif.EachApplierVanillaInterface;
import com.ishland.c2me.opts.dfc.common.gen.jvm.vif.NoisePosVanillaInterface;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.regression.noise.mixin.BlenderConstructor;
import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.RetainedIslandTerrain;
import dev.aeroscapes.worldgen.c2me.C2meIslandCompiler;
import dev.aeroscapes.worldgen.c2me.IslandTerrainNode;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Checks real seeded roots and compiled child graphs in a disposable server. */
final class C2meIntegrationRegression {
  private static final long[] SEEDS = {1, -8315743817914385108L, 42, -1};

  private C2meIntegrationRegression() {}

  static void verify(ServerLevel level) {
    transformedChild();
    var generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
    var settings = generator.generatorSettings().value();
    var noises = level.registryAccess().lookupOrThrow(Registries.NOISE);
    int samples = 0;
    for (long seed : SEEDS) {
      RandomState random = RandomState.create(settings, noises, seed);
      IslandTerrain raw = IslandTerrain.from(random);
      require(raw != null, "Compiled router lost seeded island metadata");
      require(
          ((RetainedIslandTerrain) (Object) random).aeroscapesReimagine$islandTerrain() == raw,
          "Island lookup did not return the captured seeded root");
      require(
          random.router().finalDensity() instanceof AbstractCompiledDensityFunction,
          "C2ME regression needs the density compiler enabled");
      var fingerprint = new DensityFunction.SinglePointContext(314156, 0, -161804);
      require(
          Double.doubleToLongBits(raw.continents().compute(fingerprint))
              == Double.doubleToLongBits(random.sampler().continentalness().compute(fingerprint)),
          "Compiled biome sampler changed the exact island-layout fingerprint");
      require(
          DensityFunction.HOLDER_HELPER_CODEC
              .encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), raw)
              .result()
              .isPresent(),
          "Retained source density is no longer serializable");
      IslandTerrainNode node = C2meIslandCompiler.node(raw);
      IslandTerrain accelerated = (IslandTerrain) node.getDelegate();
      bounds(raw.original(), accelerated.original());
      bounds(raw.lower(), accelerated.lower());
      bounds(raw.continents(), accelerated.continents());
      bounds(raw.form(), accelerated.form());
      bounds(raw.caves(), accelerated.caves());
      Coordinates coordinates = coordinates();
      samples += compare(raw, random.router().finalDensity(), coordinates);
      samples += compare(raw, accelerated, coordinates);
      var visits = new AtomicInteger();
      DensityFunction.Visitor visitor =
          function -> {
            visits.incrementAndGet();
            return function;
          };
      DensityFunction mapped = accelerated.mapAll(visitor);
      require(visits.get() > 5, "Compiled child visitor did not reach its runtime arguments");
      samples += compare(raw.mapAll(function -> function), mapped, coordinates);
      BasinAquiferCacheRegression.verify(random);
      OpenClColumnRegression.verify(random);
      if (seed == 1) blendingBounds(generator, random);
      if (seed == 1 && Boolean.getBoolean("aeroscapes.regression.c2me.noise"))
        mappedNoiseParity(generator, random, raw);
    }
    LogUtils.getLogger()
        .info(
            "C2ME integration regression passed: {} seeds, {} scalar/batched/mapped samples",
            SEEDS.length,
            samples);
  }

  private static void mappedNoiseParity(
      NoiseBasedChunkGenerator generator, RandomState random, IslandTerrain raw) {
    var parity = new NoiseParity();
    for (int[] chunk : new int[][] {{764, 767}, {772, 772}, {765, 772}}) {
      var noise = new ProbeNoiseChunk(generator, random, chunk[0] * 16, chunk[1] * 16);
      var mappedRaw = (IslandTerrain) noise.map(raw);
      var mappedAdapter = (IslandTerrain) noise.map(C2meIslandCompiler.node(raw).getDelegate());
      DensityFunction mappedCompiled = noise.map(random.router().finalDensity());
      PrefillParity[] prefills = {
        new PrefillParity("original", mappedRaw.original(), mappedAdapter.original()),
        new PrefillParity("lower", mappedRaw.lower(), mappedAdapter.lower()),
        new PrefillParity("form", mappedRaw.form(), mappedAdapter.form()),
        new PrefillParity("caves", mappedRaw.caves(), mappedAdapter.caves())
      };
      for (PrefillParity prefill : prefills) noise.map(DensityFunctions.cacheAllInCell(prefill));
      Climate.Sampler climate = noise.climate(random);
      var fingerprint = new DensityFunction.SinglePointContext(314156, 0, -161804);
      long expectedFingerprint = Double.doubleToLongBits(raw.continents().compute(fingerprint));
      require(
          expectedFingerprint
                  == Double.doubleToLongBits(mappedRaw.continents().compute(fingerprint))
              && expectedFingerprint
                  == Double.doubleToLongBits(mappedAdapter.continents().compute(fingerprint))
              && expectedFingerprint
                  == Double.doubleToLongBits(climate.continentalness().compute(fingerprint)),
          "NoiseChunk visitor changed the exact island-layout fingerprint");
      compareInterpolated(
          generator.generatorSettings().value(),
          noise,
          mappedRaw,
          mappedAdapter,
          mappedCompiled,
          parity);
      for (PrefillParity prefill : prefills) {
        require(prefill.batches > 0, "Native cell prefill did not exercise " + prefill.name);
        parity.prefillBatches += prefill.batches;
      }
    }
    LogUtils.getLogger()
        .info(
            "C2ME NoiseChunk parity: samples={}, mismatches={}, legacy-normal-differences={}, batches={}, native-prefill-batches={}",
            parity.samples,
            parity.mismatches,
            parity.legacyDifferences,
            parity.batches,
            parity.prefillBatches);
    require(parity.mismatches == 0, "Compiled density changed actual NoiseChunk interpolation");
    require(
        parity.legacyDifferences > 0, "NoiseChunk fixture did not exercise nested context mode");
  }

  private static void compareInterpolated(
      NoiseGeneratorSettings settings,
      ProbeNoiseChunk noise,
      IslandTerrain raw,
      IslandTerrain adapter,
      DensityFunction compiled,
      NoiseParity parity) {
    int width = settings.noiseSettings().getCellWidth();
    int height = settings.noiseSettings().getCellHeight();
    int minimum = settings.noiseSettings().minY();
    var interpolated = new NoisePosVanillaInterface();
    var normal = new NoisePosVanillaInterface();
    noise.initializeForFirstCellX();
    try {
      for (int cellX = 0; cellX < 16 / width; cellX++) {
        noise.advanceCellX(cellX);
        for (int cellZ = 0; cellZ < 16 / width; cellZ++) {
          for (int cellY = settings.noiseSettings().height() / height - 1; cellY >= 0; cellY--) {
            noise.selectCellYZ(cellY, cellZ);
            for (int localY = height - 1; localY >= 0; localY--) {
              noise.updateForY(minimum + cellY * height + localY, (double) localY / height);
              for (int localX = 0; localX < width; localX++) {
                noise.updateForX(noise.originX + cellX * width + localX, (double) localX / width);
                for (int localZ = 0; localZ < width; localZ++) {
                  noise.updateForZ(noise.originZ + cellZ * width + localZ, (double) localZ / width);
                  interpolated.at(
                      noise.blockX(),
                      noise.blockY(),
                      noise.blockZ(),
                      EvalType.INTERPOLATION,
                      DfcObjectCache.Noop.INSTANCE);
                  double expected = raw.compute(noise);
                  double direct = compiled.compute(noise);
                  double nested = adapter.compute(interpolated);
                  parity.samples++;
                  if (different(expected, direct) || different(expected, nested)) {
                    parity.mismatches++;
                    if (parity.mismatches <= 12)
                      logInterpolationDifference(
                          noise, raw, adapter, interpolated, expected, direct, nested);
                  }
                  if (localX == 0 && localZ == 0 && localY == height - 1) {
                    normal.at(
                        noise.blockX(),
                        noise.blockY(),
                        noise.blockZ(),
                        EvalType.NORMAL,
                        DfcObjectCache.Noop.INSTANCE);
                    if (different(expected, adapter.compute(normal))) parity.legacyDifferences++;
                    compareNestedBatch(noise, "root", raw, compiled, expected);
                    compareNestedBatch(
                        noise,
                        "original",
                        raw.original(),
                        adapter.original(),
                        raw.original().compute(noise));
                    compareNestedBatch(
                        noise, "lower", raw.lower(), adapter.lower(), raw.lower().compute(noise));
                    compareNestedBatch(
                        noise, "form", raw.form(), adapter.form(), raw.form().compute(noise));
                    compareNestedBatch(
                        noise, "caves", raw.caves(), adapter.caves(), raw.caves().compute(noise));
                    parity.batches++;
                  }
                }
              }
            }
          }
        }
        noise.swapSlices();
      }
    } finally {
      noise.stopInterpolation();
    }
  }

  private static void compareNestedBatch(
      NoiseChunk noise,
      String name,
      DensityFunction raw,
      DensityFunction compiled,
      double expected) {
    int[] x = {noise.blockX(), noise.blockX(), noise.blockX()};
    int[] y = {noise.blockY(), noise.blockY(), noise.blockY()};
    int[] z = {noise.blockZ(), noise.blockZ(), noise.blockZ()};
    double[] values = new double[x.length];
    compiled.fillArray(
        values,
        new EachApplierVanillaInterface(
            x, y, z, EvalType.INTERPOLATION, DfcObjectCache.Noop.INSTANCE));
    for (double value : values)
      if (different(expected, value)) {
        var point = new DensityFunction.SinglePointContext(x[0], y[0], z[0]);
        throw new IllegalStateException(
            "Nested interpolation batch mismatch: child="
                + name
                + " position="
                + x[0]
                + ","
                + y[0]
                + ","
                + z[0]
                + " expected="
                + expected
                + " actual="
                + value
                + " raw-normal="
                + raw.compute(point)
                + " compiled-normal="
                + compiled.compute(point));
      }
  }

  private static void logInterpolationDifference(
      NoiseChunk noise,
      IslandTerrain raw,
      IslandTerrain adapter,
      DensityFunction.FunctionContext nestedContext,
      double expected,
      double direct,
      double nested) {
    LogUtils.getLogger()
        .warn(
            "C2ME interpolation mismatch at {},{},{}: raw={}, actual-root={}, nested-adapter={}, original={}/{}, lower={}/{}, form={}/{}, caves={}/{}",
            noise.blockX(),
            noise.blockY(),
            noise.blockZ(),
            expected,
            direct,
            nested,
            raw.original().compute(noise),
            adapter.original().compute(nestedContext),
            raw.lower().compute(noise),
            adapter.lower().compute(nestedContext),
            raw.form().compute(noise),
            adapter.form().compute(nestedContext),
            raw.caves().compute(noise),
            adapter.caves().compute(nestedContext));
  }

  private static boolean different(double expected, double actual) {
    return !Double.isFinite(actual)
        || Math.abs(expected - actual) > 1.0e-8
        || (expected > 0) != (actual > 0);
  }

  private static final class NoiseParity {
    private long samples;
    private long mismatches;
    private long legacyDifferences;
    private long batches;
    private long prefillBatches;
  }

  /** Already mapped children; the native CacheAllInCell supplies the real prefill context. */
  private static final class PrefillParity implements DensityFunction.SimpleFunction {
    private final String name;
    private final DensityFunction raw;
    private final DensityFunction compiled;
    private int batches;

    private PrefillParity(String name, DensityFunction raw, DensityFunction compiled) {
      this.name = name;
      this.raw = raw;
      this.compiled = compiled;
    }

    @Override
    public double compute(FunctionContext context) {
      return raw.compute(context);
    }

    @Override
    public void fillArray(double[] values, ContextProvider provider) {
      require(
          provider instanceof NoiseChunk noise
              && ((IChunkNoiseSampler) noise).getIsSamplingForCaches(),
          "Expected actual native cell prefill for " + name);
      compiled.fillArray(values, provider);
      for (int index = 0; index < values.length; index++) {
        FunctionContext point = provider.forIndex(index);
        double expected = raw.compute(point);
        require(
            !different(expected, values[index]),
            "Native prefill mismatch: child="
                + name
                + " position="
                + point.blockX()
                + ","
                + point.blockY()
                + ","
                + point.blockZ()
                + " expected="
                + expected
                + " actual="
                + values[index]);
      }
      batches++;
    }

    @Override
    public double minValue() {
      return raw.minValue();
    }

    @Override
    public double maxValue() {
      return raw.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
      throw new UnsupportedOperationException("Runtime prefill regression");
    }
  }

  private static final class ProbeNoiseChunk extends NoiseChunk {
    private final int originX;
    private final int originZ;
    private final Map<CompiledEntry, CompiledEntry> entries = new IdentityHashMap<>();

    private ProbeNoiseChunk(NoiseBasedChunkGenerator generator, RandomState random, int x, int z) {
      this(generator, random, x, z, Blender.empty());
    }

    private ProbeNoiseChunk(
        NoiseBasedChunkGenerator generator, RandomState random, int x, int z, Blender blender) {
      super(
          16 / generator.generatorSettings().value().noiseSettings().getCellWidth(),
          random,
          x,
          z,
          generator.generatorSettings().value().noiseSettings(),
          DensityFunctions.BeardifierMarker.INSTANCE,
          generator.generatorSettings().value(),
          generator.globalFluidPicker.get(),
          blender);
      originX = x;
      originZ = z;
    }

    private DensityFunction map(DensityFunction function) {
      return function.mapAll(
          new DelegatingBlendingCachingAwareVisitor(
              this::wrap, entries, getBlender() != Blender.empty()));
    }

    private DensityFunction mapCounting(DensityFunction function, AtomicInteger visits) {
      return function.mapAll(
          new DelegatingBlendingCachingAwareVisitor(
              mapped -> {
                visits.incrementAndGet();
                return wrap(mapped);
              },
              entries,
              getBlender() != Blender.empty()));
    }

    private Climate.Sampler climate(RandomState random) {
      return cachedClimateSampler(random.router(), random.sampler().spawnTarget());
    }
  }

  private static void blendingBounds(NoiseBasedChunkGenerator generator, RandomState random) {
    Blender blender =
        BlenderConstructor.aeroscapesRegression$create(
            new Long2ObjectOpenHashMap<>(), new Long2ObjectOpenHashMap<>());
    require(blender != Blender.empty(), "Blending regression needs active native wrappers");
    var noise = new ProbeNoiseChunk(generator, random, 0, 0, blender);
    var terrain =
        new IslandTerrain(
            DensityFunctions.blendAlpha(),
            DensityFunctions.blendOffset(),
            DensityFunctions.constant(0),
            DensityFunctions.constant(0),
            DensityFunctions.constant(1));
    var accelerated = (IslandTerrain) C2meIslandCompiler.node(terrain).getDelegate();
    var visits = new AtomicInteger();
    DensityFunction alpha = noise.mapCounting(accelerated.original(), visits);
    require(visits.get() == 1, "Blending applied the BlendAlpha root visitor twice");
    require(alpha.minValue() == 0 && alpha.maxValue() == 1, "Lost native BlendAlpha bounds");
    bounds(noise.map(terrain.original()), alpha);
    visits.set(0);
    DensityFunction offset = noise.mapCounting(accelerated.lower(), visits);
    require(visits.get() == 1, "Blending applied the BlendOffset root visitor twice");
    require(
        offset.minValue() == Double.NEGATIVE_INFINITY
            && offset.maxValue() == Double.POSITIVE_INFINITY,
        "Lost native BlendOffset bounds");
    bounds(noise.map(terrain.lower()), offset);
  }

  private static void transformedChild() {
    var terrain =
        new IslandTerrain(
            DensityFunctions.yClampedGradient(-128, 384, -1, 1),
            DensityFunctions.constant(-1),
            DensityFunctions.constant(0),
            DensityFunctions.constant(0),
            DensityFunctions.constant(1));
    IslandTerrainNode original = C2meIslandCompiler.node(terrain);
    var position = new DensityFunction.SinglePointContext(0, 0, 0);
    equal(
        terrain.original().compute(position),
        ((IslandTerrain) original.getDelegate()).original().compute(position));
    var transformed =
        (IslandTerrainNode)
            original.transform(
                child -> child == original.original() ? new ConstantNode(0.75) : child);
    equal(0.75, ((IslandTerrain) transformed.getDelegate()).original().compute(position));
    require(original != transformed, "Transformed island graph retained its old CPU delegate");
    var changedGradient =
        (IslandTerrainNode)
            original.transform(
                child ->
                    child == original.original()
                        ? new YClampedGradientNode(-128, 384, -0.5, 0.5)
                        : child);
    equal(-0.25, ((IslandTerrain) changedGradient.getDelegate()).original().compute(position));
  }

  private static Coordinates coordinates() {
    int[] x = new int[256];
    int[] y = new int[256];
    int[] z = new int[256];
    for (int index = 0; index < x.length; index++) {
      x[index] = index * 137 % 8192 - 4096;
      z[index] = index * 367 % 8192 - 4096;
      y[index] = index * 31 % 512 - 128;
    }
    return new Coordinates(x, y, z);
  }

  private static int compare(
      DensityFunction source, DensityFunction compiled, Coordinates coordinates) {
    double[] batched = new double[coordinates.x().length];
    compiled.fillArray(batched, coordinates);
    for (int index = 0; index < batched.length; index++) {
      DensityFunction.FunctionContext point = coordinates.forIndex(index);
      double expected = source.compute(point);
      equal(expected, compiled.compute(point));
      equal(expected, batched[index]);
    }
    return batched.length;
  }

  private static void bounds(DensityFunction source, DensityFunction compiled) {
    require(
        Double.doubleToLongBits(source.minValue()) == Double.doubleToLongBits(compiled.minValue())
            && Double.doubleToLongBits(source.maxValue())
                == Double.doubleToLongBits(compiled.maxValue()),
        "Compiled child changed semantic bounds");
  }

  private static void equal(double expected, double actual) {
    require(
        Double.isFinite(actual) && Math.abs(expected - actual) <= 1.0e-8,
        "Compiled density differs: expected=" + expected + ", actual=" + actual);
  }

  private static void require(boolean accepted, String failure) {
    if (!accepted) throw new IllegalStateException(failure);
  }

  private record Coordinates(int[] x, int[] y, int[] z) implements DensityFunction.ContextProvider {
    @Override
    public DensityFunction.FunctionContext forIndex(int index) {
      return new DensityFunction.SinglePointContext(x[index], y[index], z[index]);
    }

    @Override
    public void fillAllDirectly(double[] values, DensityFunction function) {
      for (int index = 0; index < values.length; index++)
        values[index] = function.compute(forIndex(index));
    }
  }
}
