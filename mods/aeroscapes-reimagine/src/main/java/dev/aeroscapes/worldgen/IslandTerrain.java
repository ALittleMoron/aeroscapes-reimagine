package dev.aeroscapes.worldgen;

import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Adds lower terrain and bounded island families to the retained upper noise. */
public final class IslandTerrain implements DensityFunction.SimpleFunction {
  public static final MapCodec<IslandTerrain> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      DensityFunction.HOLDER_HELPER_CODEC
                          .fieldOf("original")
                          .forGetter(value -> value.original),
                      DensityFunction.HOLDER_HELPER_CODEC
                          .fieldOf("lower")
                          .forGetter(value -> value.lower),
                      DensityFunction.HOLDER_HELPER_CODEC
                          .fieldOf("continents")
                          .forGetter(value -> value.continents))
                  .and(
                      DensityFunction.HOLDER_HELPER_CODEC
                          .fieldOf("form")
                          .forGetter(value -> value.form))
                  .and(
                      DensityFunction.HOLDER_HELPER_CODEC
                          .fieldOf("caves")
                          .forGetter(value -> value.caves))
                  .apply(instance, IslandTerrain::new));
  private static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES =
      DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, "aeroscapes_reimagine");
  private final DensityFunction original;
  private final DensityFunction lower;
  private final DensityFunction continents;
  private final DensityFunction form;
  private final DensityFunction caves;
  private final Supplier<IslandLayout> layout;
  private final Supplier<Double> detailThreshold;
  // Fixed-size, full-coordinate-keyed cache: no world-size growth and no shared mutable shape.
  private final AtomicReferenceArray<Column> columns = new AtomicReferenceArray<>(256);

  public record Column(
      int x,
      int z,
      IslandLayout.VerticalShape small0,
      IslandLayout.VerticalShape small1,
      IslandLayout.VerticalShape small2,
      IslandLayout.VerticalShape small3,
      IslandLayout.VerticalShape large,
      IslandLayout.Basin basin,
      double radial,
      double floor,
      IslandLayout.VerticalShape basinShape) {
    public boolean water(int y) {
      return basin != null && radial < 1 && y > floor && y <= basin.waterline();
    }

    public double islands(int y) {
      return Math.max(
          large.density(y),
          Math.max(
              Math.max(small0.density(y), small1.density(y)),
              Math.max(small2.density(y), small3.density(y))));
    }

    public boolean hasDetail() {
      return small0.cap() > 0
          || small1.cap() > 0
          || small2.cap() > 0
          || small3.cap() > 0
          || large.cap() > 0
          || basinShape.cap() > 0;
    }

    public double islands(int y, double detail) {
      return Math.max(
          large.terrainDensity(y, detail),
          Math.max(
              Math.max(small0.terrainDensity(y, detail), small1.terrainDensity(y, detail)),
              Math.max(small2.terrainDensity(y, detail), small3.terrainDensity(y, detail))));
    }
  }

  public IslandTerrain(
      DensityFunction original,
      DensityFunction lower,
      DensityFunction continents,
      DensityFunction form,
      DensityFunction caves) {
    this.original = original;
    this.lower = lower;
    this.continents = continents;
    this.form = form;
    this.caves = caves;
    // Registry holders may be forward references during decoding. Native RandomState
    // maps the functions before the first sample; defer the seeded fingerprint until then.
    layout = Suppliers.memoize(() -> layout(continents));
    // Mapped wrappers have invariant bounds, but resolving them during registry decoding
    // can touch forward references. Resolve once on the first detailed terrain sample.
    detailThreshold = Suppliers.memoize(() -> -form.maxValue());
  }

  public static void register(IEventBus bus) {
    TYPES.register("island_terrain", () -> CODEC);
    TYPES.register(bus);
  }

  public IslandLayout layout() {
    return layout.get();
  }

  public DensityFunction original() {
    return original;
  }

  public DensityFunction lower() {
    return lower;
  }

  public DensityFunction continents() {
    return continents;
  }

  public DensityFunction form() {
    return form;
  }

  public DensityFunction caves() {
    return caves;
  }

  public static IslandLayout layout(DensityFunction continents) {
    // Quart alignment gives native FlatCache the same coordinates as raw queries.
    return new IslandLayout(
        Double.doubleToLongBits(continents.compute(new SinglePointContext(314156, 0, -161804))));
  }

  public static IslandLayout.Basin basin(
      IslandLayout layout, DensityFunction continents, int x, int z) {
    IslandLayout.Basin basin = layout.basin(x, z);
    return basin != null
            && continents.compute(
                    new SinglePointContext(
                        Math.floorDiv(basin.centerX(), 4) * 4,
                        0,
                        Math.floorDiv(basin.centerZ(), 4) * 4))
                < -0.16
        ? basin
        : null;
  }

  public Column column(int x, int z) {
    int index = (x & 15) | ((z & 15) << 4);
    Column cached = columns.get(index);
    if (cached != null && cached.x() == x && cached.z() == z) return cached;
    IslandLayout islands = layout();
    var small = new IslandLayout.VerticalShape[4];
    for (int layer = 0; layer < 4; layer++) small[layer] = islands.smallShape(x, z, layer);
    var island = islands.largeIsland(x, z);
    var large = island == null ? IslandLayout.VerticalShape.EMPTY : island.shape(x, z);
    var basin = basin(islands, continents, x, z);
    double radial = basin == null ? 2 : basin.radius(x, z);
    double floor = basin == null ? 0 : basin.floor(x, z, radial);
    var basinShape =
        basin == null ? IslandLayout.VerticalShape.EMPTY : basin.shape(x, z, radial, floor);
    Column computed =
        new Column(
            x, z, small[0], small[1], small[2], small[3], large, basin, radial, floor, basinShape);
    columns.set(index, computed);
    return computed;
  }

  public IslandLayout.Basin basin(int x, int z) {
    return column(x, z).basin();
  }

  @Override
  public double compute(FunctionContext context) {
    int x = context.blockX();
    int y = context.blockY();
    int z = context.blockZ();
    double retained = y >= -64 && y < 304 ? original.compute(context) : -1;
    if (y < 32) retained = Math.max(retained, lower.compute(context));
    Column column = column(x, z);
    double added = column.islands(y);
    if (column.basin() != null && column.radial() < 1.22) {
      added = Math.max(added, column.basinShape().density(y));
    }
    if (column.hasDetail() && added > detailThreshold.get()) {
      // The envelope bounds location, not a solid island: 3D form and caves survive
      // composition. Native interpolation evaluates both noise fields on its 4x8x4 grid.
      double detail = form.compute(context);
      added = column.islands(y, detail);
      if (column.basin() != null)
        added = Math.max(added, column.basinShape().sealedDensity(y, detail));
      boolean liner =
          column.basin() != null
              && column.radial() < 1.08
              && y >= column.floor() - 12
              && y <= column.floor() + 5;
      if (!liner) added = Math.min(added, caves.compute(context));
    }
    if (y < -116) added = Math.min(added, (y + 125) / 12.0);
    // Basin water fills empty density through its aquifer; it must not cut a flat
    // waterline underside into original Aero islands or erase rocky shoals.
    return Math.max(retained, added);
  }

  @Override
  public DensityFunction mapAll(Visitor visitor) {
    return visitor.apply(
        new IslandTerrain(
            original.mapAll(visitor),
            lower.mapAll(visitor),
            continents.mapAll(visitor),
            form.mapAll(visitor),
            caves.mapAll(visitor)));
  }

  @Override
  public double minValue() {
    return Math.min(-1, Math.min(original.minValue(), lower.minValue()));
  }

  @Override
  public double maxValue() {
    return Math.max(5 + form.maxValue(), Math.max(original.maxValue(), lower.maxValue()));
  }

  @Override
  public KeyDispatchDataCodec<? extends DensityFunction> codec() {
    return KeyDispatchDataCodec.of(CODEC);
  }

  public static IslandTerrain from(RandomState state) {
    if ((Object) state instanceof RetainedIslandTerrain retained) {
      IslandTerrain terrain = retained.aeroscapesReimagine$islandTerrain();
      if (terrain != null) return terrain;
    }
    DensityFunction density = state.router().finalDensity();
    while (density
        instanceof net.minecraft.world.level.levelgen.DensityFunctions.HolderHolder holder)
      density = holder.function().value();
    return density instanceof IslandTerrain terrain ? terrain : null;
  }
}
