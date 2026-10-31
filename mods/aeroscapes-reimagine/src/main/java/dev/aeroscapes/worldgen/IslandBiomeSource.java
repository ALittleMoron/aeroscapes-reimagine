package dev.aeroscapes.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import terrablender.mixin.MultiNoiseBiomeSourceAccess;
import terrablender.util.LevelUtils;
import terrablender.worldgen.IExtendedMultiNoiseBiomeSource;
import terrablender.worldgen.IExtendedParameterList;

/** Matches the added physical habitats while retaining the original biome source elsewhere. */
public final class IslandBiomeSource extends BiomeSource {
  public static final MapCodec<IslandBiomeSource> CODEC =
      RecordCodecBuilder.mapCodec(
          instance ->
              instance
                  .group(
                      BiomeSource.CODEC.fieldOf("delegate").forGetter(value -> value.delegate),
                      Biome.CODEC.listOf().fieldOf("oceans").forGetter(value -> value.oceans),
                      Biome.CODEC
                          .listOf()
                          .fieldOf("deep_oceans")
                          .forGetter(value -> value.deepOceans),
                      Biome.CODEC.listOf().fieldOf("shores").forGetter(value -> value.shores),
                      Biome.CODEC.listOf().fieldOf("mountains").forGetter(value -> value.mountains),
                      Biome.CODEC
                          .optionalFieldOf("void_biome")
                          .forGetter(value -> Optional.ofNullable(value.voidBiome)))
                  .apply(instance, IslandBiomeSource::new));
  private static final DeferredRegister<MapCodec<? extends BiomeSource>> TYPES =
      DeferredRegister.create(Registries.BIOME_SOURCE, "aeroscapes_reimagine");
  private final BiomeSource delegate;
  private final List<Holder<Biome>> oceans;
  private final List<Holder<Biome>> deepOceans;
  private final List<Holder<Biome>> shores;
  private final List<Holder<Biome>> mountains;
  private Holder<Biome> voidBiome;
  private volatile Habitat habitat;

  private record Habitat(
      DensityFunction continents,
      int cellX,
      int cellZ,
      IslandLayout layout,
      IslandLayout.Basin basin,
      IslandLayout.LargeIsland island) {}

  private Habitat habitat(int x, int z, DensityFunction continents) {
    // Both layout cell widths (1024 and 1536) are multiples of 512.
    int cellX = Math.floorDiv(x, 512);
    int cellZ = Math.floorDiv(z, 512);
    Habitat cached = habitat;
    if (cached != null
        && cached.continents() == continents
        && cached.cellX() == cellX
        && cached.cellZ() == cellZ) return cached;
    IslandLayout layout =
        cached != null && cached.continents() == continents
            ? cached.layout()
            : IslandTerrain.layout(continents);
    Habitat computed =
        new Habitat(
            continents,
            cellX,
            cellZ,
            layout,
            IslandTerrain.basin(layout, continents, x, z),
            layout.largeIsland(x, z));
    habitat = computed;
    return computed;
  }

  public IslandBiomeSource(
      BiomeSource delegate,
      List<Holder<Biome>> oceans,
      List<Holder<Biome>> deepOceans,
      List<Holder<Biome>> shores,
      List<Holder<Biome>> mountains,
      Optional<Holder<Biome>> voidBiome) {
    if (oceans.size() != 5 || deepOceans.size() != 5 || shores.size() != 2 || mountains.size() != 4)
      throw new IllegalArgumentException(
          "Island habitats need 5 ocean pairs, 2 shores and 4 mountain biomes");
    this.delegate = delegate;
    BiolithBiomeCompatibility.initialize(delegate);
    this.oceans = List.copyOf(oceans);
    this.deepOceans = List.copyOf(deepOceans);
    this.shores = List.copyOf(shores);
    this.mountains = List.copyOf(mountains);
    this.voidBiome = voidBiome.orElse(null);
  }

  public static void register(IEventBus bus) {
    TYPES.register("island_biomes", () -> CODEC);
    TYPES.register(bus);
    NeoForge.EVENT_BUS.addListener(IslandBiomeSource::initializeDelegate);
  }

  private static void initializeDelegate(ServerAboutToStartEvent event) {
    var server = event.getServer();
    var stems = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
    for (var entry : stems.entrySet()) {
      var stem = entry.getValue();
      if (stem.generator() instanceof NoiseBasedChunkGenerator generator
          && generator.getBiomeSource() instanceof IslandBiomeSource source) {
        if (source.voidBiome == null)
          source.voidBiome =
              server
                  .registryAccess()
                  .registryOrThrow(Registries.BIOME)
                  .getHolderOrThrow(
                      ResourceKey.create(
                          Registries.BIOME, ResourceLocation.parse("aeroscapes_reimagine:void")));
        // TerraBlender 4.1.0.8 recognizes only a top-level MultiNoiseBiomeSource.
        // Its public initializer must see the retained native delegate and the same settings.
        LevelUtils.initializeBiomes(
            server.registryAccess(),
            stem.type(),
            entry.getKey(),
            new NoiseBasedChunkGenerator(source.delegate, generator.generatorSettings()),
            server.getWorldData().worldGenOptions().seed());
      }
    }
  }

  @SuppressWarnings("unchecked") // Pinned TB adds this same T to typed Climate.ParameterList<T>.
  public IslandBiomeSource forGeneration() {
    if (!(delegate instanceof MultiNoiseBiomeSource multi)) return this;
    var parameters =
        (IExtendedParameterList<Holder<Biome>>)
            BiolithBiomeCompatibility.parameters(
                multi,
                ((MultiNoiseBiomeSourceAccess) multi)
                    .getParameters()
                    .map(value -> value, holder -> holder.value().parameters()));
    if (!parameters.isInitialized()) return this;
    MultiNoiseBiomeSource copy = ((IExtendedMultiNoiseBiomeSource) multi).clone();
    var copiedParameters = parameters.clone();
    ((IExtendedParameterList<Holder<Biome>>) copiedParameters).recreateUniqueness();
    ((MultiNoiseBiomeSourceAccess) copy).setParameters(Either.left(copiedParameters));
    BiolithBiomeCompatibility.bindGenerationCache(copy, copiedParameters);
    return new IslandBiomeSource(
        copy, oceans, deepOceans, shores, mountains, Optional.ofNullable(voidBiome));
  }

  @Override
  protected MapCodec<? extends BiomeSource> codec() {
    return CODEC;
  }

  @Override
  protected Stream<Holder<Biome>> collectPossibleBiomes() {
    return Stream.of(
            delegate.possibleBiomes().stream(),
            oceans.stream(),
            deepOceans.stream(),
            shores.stream(),
            mountains.stream(),
            Stream.ofNullable(voidBiome))
        .flatMap(stream -> stream);
  }

  @Override
  public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
    IslandBiomeSampler context = (IslandBiomeSampler) (Object) sampler;
    BiomeTerrainMask mask = context.aeroscapesReimagine$terrainMask();
    if (mask == null || voidBiome == null)
      throw new IllegalStateException("Island biome terrain was not initialized");
    context.aeroscapesReimagine$voidBiome(voidBiome);
    // Keep delegate calls even in void: its nearest-leaf cache can affect climate ties.
    Holder<Biome> selected = habitatBiome(quartX, quartY, quartZ, sampler);
    return context.aeroscapesReimagine$deferTerrainMask()
            || mask.nearTerrain(quartX * 4, quartY * 4, quartZ * 4)
        ? selected
        : voidBiome;
  }

  private Holder<Biome> habitatBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
    int x = quartX * 4;
    int y = quartY * 4;
    int z = quartZ * 4;
    Habitat current = habitat(x, z, sampler.continentalness());
    IslandLayout.Basin basin = current.basin();
    if (basin != null
        && basin.radius(x, z) < 1.1
        && y >= basin.floor(x, z) - 8
        && y <= basin.waterline() + 16) {
      double temperature = sampler.temperature().compute(new SinglePointContext(x, y, z));
      if (basin.floor(x, z) < basin.waterline()) {
        int index =
            temperature < -0.45
                ? 0
                : temperature < -0.15 ? 1 : temperature < 0.2 ? 2 : temperature < 0.5 ? 3 : 4;
        return basin.waterline() - basin.floor(x, z) >= 30
            ? deepOceans.get(index)
            : oceans.get(index);
      }
      return shores.get(temperature < -0.15 ? 0 : 1);
    }
    IslandLayout.LargeIsland island = current.island();
    if (island != null
        && island.radius(x, z) < 1
        && island.peak(x, z) > 20
        && y >= island.surface(x, z) - 24
        && y <= island.surface(x, z) + 16) {
      double temperature = sampler.temperature().compute(new SinglePointContext(x, y, z));
      if (island.peak(x, z) < 48) return mountains.get(0);
      return mountains.get(temperature < -0.15 ? 1 : temperature > 0.35 ? 3 : 2);
    }
    return delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
  }
}
