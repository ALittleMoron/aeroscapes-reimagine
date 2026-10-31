package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native precipitation in one real empty FULL column; does not test client weather rendering. */
public final class VoidWeatherRegression {
  private static final ResourceLocation VOID = ResourceLocation.parse("aeroscapes_reimagine:void");
  private static final TicketType<ChunkPos> TICKET =
      TicketType.create("aeroscapes_void_weather", Comparator.comparingLong(ChunkPos::toLong));
  private static final List<String> WEATHER_LEVELS =
      List.of("oRainLevel", "rainLevel", "oThunderLevel", "thunderLevel");

  private VoidWeatherRegression() {}

  public static void biomeContract(ServerLevel level) {
    Biome biome =
        Objects.requireNonNull(
            level.registryAccess().registryOrThrow(Registries.BIOME).get(VOID),
            "Missing runtime void biome");
    require(biome.hasPrecipitation(), "Runtime void biome hasPrecipitation must be true");
    require(
        level.getMinBuildHeight() == -128 && level.getMaxBuildHeight() == 384,
        "Void weather contract needs the actual extended Overworld height");
    for (int[] column :
        new int[][] {
          {0, 0},
          {-4096, -3072},
          {4096, 3072},
          {-31, 47},
          {31, -47},
          {-65536, 65536},
          {65536, -65536}
        })
      for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++)
        require(
            biome.getPrecipitationAt(new BlockPos(column[0], y, column[1]))
                == Biome.Precipitation.RAIN,
            "Runtime void precipitation is not RAIN at Y=" + y);
    require(
        biome.modifiableBiomeInfo().getOriginalBiomeInfo().generationSettings().features().stream()
            .allMatch(features -> features.size() == 0),
        "Void biome gained declared generation features");
    // Zeta installs its deferred dispatcher at every stage, including otherwise empty biomes.
    var steps = GenerationStep.Decoration.values();
    var features = biome.getGenerationSettings().features();
    require(features.size() <= steps.length, "Unexpected generation step count");
    for (int step = 0; step < features.size(); step++) {
      require(features.get(step).size() <= 1, "Duplicate deferred features at " + steps[step]);
      String expected = "zeta:deferred_feature_" + steps[step].name().toLowerCase(Locale.ROOT);
      for (var feature : features.get(step)) {
        var type = BuiltInRegistries.FEATURE.getKey(feature.value().feature().value().feature());
        require(
            type != null && type.toString().equals(expected),
            "Unexpected direct void feature at " + steps[step] + ": " + type);
      }
    }
    for (GenerationStep.Carving step : GenerationStep.Carving.values())
      require(
          !biome.getGenerationSettings().getCarvers(step).iterator().hasNext(),
          "Void biome gained carvers");
    var declaredSpawns = biome.modifiableBiomeInfo().getOriginalBiomeInfo().mobSpawnSettings();
    for (MobCategory category : MobCategory.values())
      require(
          declaredSpawns.getMobs(category).unwrap().isEmpty(),
          "Void biome gained declared spawn entries " + category);
    require(declaredSpawns.getEntityTypes().isEmpty(), "Void biome gained declared spawn costs");
  }

  public static void start(ServerLevel level, Path output) {
    Path destination = output.toAbsolutePath().normalize();
    JsonObject report = new JsonObject();
    report.addProperty("format", 1);
    report.addProperty("seed", level.getSeed());
    report.addProperty(
        "limits",
        "One bounded actual FULL air corridor, native biome precipitation"
            + " and Level weather predicates only. No particle, audio, client or lightning-rendering claim.");
    try {
      require(
          level.getServer().isDedicatedServer()
              && level.getServer().isSameThread()
              && level.dimension() == Level.OVERWORLD,
          "Disposable dedicated Overworld/server thread only");
      Biome biome =
          Objects.requireNonNull(
              level.registryAccess().registryOrThrow(Registries.BIOME).get(VOID),
              "Missing runtime void biome");
      report.addProperty("effective_temperature", biome.getModifiedClimateSettings().temperature());
      report.addProperty("effective_downfall", biome.getModifiedClimateSettings().downfall());
      report.add(
          "declared_biome",
          Biome.DIRECT_CODEC
              .encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), biome)
              .getOrThrow());
      report.add(
          "effective_mob_settings",
          MobSpawnSettings.CODEC
              .codec()
              .encodeStart(
                  RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()),
                  biome.getMobSettings())
              .getOrThrow());
      biomeContract(level);
      report.addProperty("biome_contract_passed", true);
      require(
          level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator,
          "Void weather needs the normal noise generator");
      Corridor test = new Corridor(level, destination, report);
      test.normalSampler();
      write(destination, report, "running");
      NeoForge.EVENT_BUS.addListener(test::tick);
      NeoForge.EVENT_BUS.addListener(test::stopping);
    } catch (RuntimeException failure) {
      report.addProperty("failure", failure.toString());
      write(destination, report, "error");
      throw failure;
    }
  }

  private static final class Corridor {
    private final ServerLevel level;
    private final NoiseBasedChunkGenerator generator;
    private final Path output;
    private final JsonObject report;
    private final JsonArray rejected = new JsonArray();
    private final long started = System.nanoTime();
    private CompletableFuture<ChunkResult<ChunkAccess>> pending;
    private ChunkPos ticket;
    private int x;
    private int z;
    private int candidates;
    private boolean finished;

    private Corridor(ServerLevel level, Path output, JsonObject report) {
      this.level = level;
      this.generator = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
      this.output = output;
      this.report = report;
      report.addProperty("deadline_seconds", 120);
      report.addProperty("candidate_budget", 64);
      report.add("rejected_FULL_candidates", rejected);
    }

    private void normalSampler() {
      IslandBiomeSampler sampler =
          (IslandBiomeSampler) (Object) level.getChunkSource().randomState().sampler();
      require(
          !sampler.aeroscapesReimagine$deferTerrainMask()
              && sampler.aeroscapesReimagine$terrainMask() != null,
          "Normal masked caller sampler required");
    }

    private void tick(ServerTickEvent.Post event) {
      if (finished || event.getServer() != level.getServer()) return;
      try {
        require(
            System.nanoTime() - started < 120_000_000_000L, "Void weather exceeded 120 seconds");
        normalSampler();
        if (pending != null) {
          if (!pending.isDone()) return;
          ChunkAccess full =
              pending.join().orElseThrow(() -> new IllegalStateException("FULL unavailable"));
          pending = null;
          require(
              full.getPersistedStatus() == ChunkStatus.FULL && full.getPos().equals(ticket),
              "Wrong FULL weather candidate");
          try {
            if (!corridor(full)) return;
            weather();
            normalSampler();
            require(
                System.nanoTime() - started < 120_000_000_000L,
                "Void weather exceeded 120 seconds");
            finished = true;
          } finally {
            release();
          }
          save("complete");
          LogUtils.getLogger()
              .info(
                  "Void weather regression passed: seed={} column={},{} output={}",
                  level.getSeed(),
                  x,
                  z,
                  output);
          return;
        }
        require(candidates < 64, "No FULL void air corridor in 64 native raw candidates");
        x = (candidates % 8 - 4) * 2048 + 2;
        z = (candidates / 8 - 4) * 2048 + 2;
        candidates++;
        if (!rawAir(generator.getBaseColumn(x, z, level, level.getChunkSource().randomState())))
          return;
        ticket = new ChunkPos(x >> 4, z >> 4);
        level.getChunkSource().addRegionTicket(TICKET, ticket, 0, ticket);
        ChunkPos requested = ticket;
        pending =
            CompletableFuture.supplyAsync(
                    () ->
                        level
                            .getChunkSource()
                            .getChunkFuture(requested.x, requested.z, ChunkStatus.FULL, true),
                    Util.backgroundExecutor())
                .thenCompose(future -> future);
      } catch (RuntimeException failure) {
        finished = true;
        release();
        report.addProperty("failure", failure.toString());
        save("error");
        throw failure;
      }
    }

    private boolean rawAir(NoiseColumn raw) {
      for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++)
        if (!raw.getBlock(y).isAir()) return false;
      return true;
    }

    private boolean corridor(ChunkAccess full) {
      boolean raw =
          rawAir(generator.getBaseColumn(x, z, level, level.getChunkSource().randomState()));
      boolean air = true;
      for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++)
        air &= full.getBlockState(new BlockPos(x, y, z)).isAir();
      JsonObject candidate = new JsonObject();
      candidate.addProperty("x", x);
      candidate.addProperty("z", z);
      candidate.addProperty("raw_entire_column_AIR", raw);
      candidate.addProperty("FULL_entire_column_AIR", air);
      int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
      candidate.addProperty("motion_blocking_height", height);
      boolean valid = raw && air && height == level.getMinBuildHeight();
      JsonArray flights = new JsonArray();
      for (int y : flightHeights(level)) {
        BlockPos position = new BlockPos(x, y, z);
        String source =
            generator
                .getBiomeSource()
                .getNoiseBiome(
                    x >> 2, y >> 2, z >> 2, level.getChunkSource().randomState().sampler())
                .unwrapKey()
                .orElseThrow()
                .location()
                .toString();
        String stored =
            full.getNoiseBiome(x >> 2, y >> 2, z >> 2)
                .unwrapKey()
                .orElseThrow()
                .location()
                .toString();
        String actual = level.getBiome(position).unwrapKey().orElseThrow().location().toString();
        boolean sky = level.canSeeSky(position);
        JsonObject flight = new JsonObject();
        flight.addProperty("y", y);
        flight.addProperty("source", source);
        flight.addProperty("stored", stored);
        flight.addProperty("level_biome", actual);
        flight.addProperty("can_see_sky", sky);
        flights.add(flight);
        valid &=
            sky && source.equals(VOID.toString()) && stored.equals(source) && actual.equals(source);
      }
      candidate.add("flight_points", flights);
      if (valid) report.add("corridor", candidate);
      else if (rejected.size() < 16) rejected.add(candidate);
      return valid;
    }

    private void weather() {
      Weather saved = Weather.capture(level);
      JsonArray modes = new JsonArray();
      report.add("weather_modes", modes);
      try {
        for (String mode : List.of("clear", "rain", "thunder")) {
          boolean raining = !mode.equals("clear");
          boolean thundering = mode.equals("thunder");
          level.setWeatherParameters(raining ? 0 : 6000, 6000, raining, thundering);
          level.setRainLevel(raining ? 1 : 0);
          level.setThunderLevel(thundering ? 1 : 0);
          JsonObject state = new JsonObject();
          state.addProperty("mode", mode);
          state.addProperty("is_raining", level.isRaining());
          state.addProperty("is_thundering", level.isThundering());
          JsonArray atFlight = new JsonArray();
          for (int y : flightHeights(level)) atFlight.add(level.isRainingAt(new BlockPos(x, y, z)));
          state.add("is_raining_at_flight_points", atFlight);
          modes.add(state);
          require(
              level.isRaining() == raining && level.isThundering() == thundering,
              "Native global weather state mismatch " + mode);
          for (var value : atFlight)
            require(
                value.getAsBoolean() == raining, "Native void rain-at predicate mismatch " + mode);
        }
        BlockPos roof = new BlockPos(x, 161, z);
        var before = level.getBlockState(roof);
        require(before.isAir(), "Roof control would overwrite non-AIR");
        try {
          require(
              level.setBlock(roof, Blocks.STONE.defaultBlockState(), 3),
              "Cannot place roof control");
          require(level.getBlockState(roof).is(Blocks.STONE), "Roof control is not actual STONE");
          boolean rainingBelow = level.isRainingAt(new BlockPos(x, 160, z));
          report.addProperty("rain_at_under_temporary_stone_roof", rainingBelow);
          require(!rainingBelow, "Native rain penetrates the stone roof control");
        } finally {
          level.setBlock(roof, before, 3);
          report.addProperty("temporary_roof_restored", level.getBlockState(roof).equals(before));
        }
        require(level.getBlockState(roof).equals(before), "Temporary roof was not restored");
      } finally {
        saved.restore(level);
        report.addProperty("weather_restored", true);
      }
    }

    private void release() {
      if (ticket != null) {
        level.getChunkSource().removeRegionTicket(TICKET, ticket, 0, ticket);
        ticket = null;
      }
      if (pending != null) {
        pending.cancel(false);
        pending = null;
      }
    }

    private void save(String status) {
      report.addProperty("candidate_columns_examined", candidates);
      report.addProperty("elapsed_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
      report.addProperty("ticket_active", ticket != null);
      write(output, report, status);
    }

    private void stopping(ServerStoppingEvent event) {
      if (finished || event.getServer() != level.getServer()) return;
      finished = true;
      release();
      report.addProperty("failure", "Server stopped before void weather completion");
      save("aborted");
    }
  }

  private static int[] flightHeights(ServerLevel level) {
    return new int[] {level.getMinBuildHeight(), -64, 64, 160, 256, level.getMaxBuildHeight() - 1};
  }

  private record Weather(
      int clear,
      int rainTime,
      int thunderTime,
      boolean raining,
      boolean thundering,
      float[] levels) {
    private static Weather capture(ServerLevel level) {
      var data = (ServerLevelData) level.getLevelData();
      float[] levels = new float[4];
      try {
        for (int i = 0; i < levels.length; i++) levels[i] = weatherField(i).getFloat(level);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("Cannot snapshot native weather amplitudes", failure);
      }
      return new Weather(
          data.getClearWeatherTime(),
          data.getRainTime(),
          data.getThunderTime(),
          data.isRaining(),
          data.isThundering(),
          levels);
    }

    private void restore(ServerLevel level) {
      var data = (ServerLevelData) level.getLevelData();
      data.setClearWeatherTime(clear);
      data.setRainTime(rainTime);
      data.setThunderTime(thunderTime);
      data.setRaining(raining);
      data.setThundering(thundering);
      try {
        // Public setters collapse interpolation and getThunderLevel multiplies by rain.
        for (int i = 0; i < levels.length; i++) weatherField(i).setFloat(level, levels[i]);
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("Cannot restore native weather amplitudes", failure);
      }
    }
  }

  private static Field weatherField(int index) throws ReflectiveOperationException {
    Field field = Level.class.getDeclaredField(WEATHER_LEVELS.get(index));
    field.setAccessible(true);
    return field;
  }

  private static void write(Path output, JsonObject report, String status) {
    report.addProperty("status", status);
    try {
      Files.createDirectories(output.getParent());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write void weather regression " + output, failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
