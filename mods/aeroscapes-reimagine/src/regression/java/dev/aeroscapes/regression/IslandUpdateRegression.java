package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.CityCavern;
import dev.aeroscapes.worldgen.IslandBiomeSampler;
import dev.aeroscapes.worldgen.IslandBiomeSource;
import dev.aeroscapes.worldgen.IslandLayout;
import dev.aeroscapes.worldgen.IslandTerrain;
import dev.aeroscapes.worldgen.SupportedStructure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Bounded natural witnesses for island caves, habitat biomes, lava and building stones. */
public final class IslandUpdateRegression {
  private static final String VOLCANIC = "aeroscapes_reimagine:volcanic_island";
  private final ServerLevel level;
  private final IslandTerrain terrain;
  private final BiomeSource source;
  private final Map<ChunkPos, LevelChunk> chunks = new LinkedHashMap<>();
  private final JsonObject report = new JsonObject();

  private IslandUpdateRegression(ServerLevel level) {
    this.level = level;
    terrain = IslandTerrain.from(level.getChunkSource().randomState());
    require(terrain != null, "Missing owned island terrain");
    source = level.getChunkSource().getGenerator().getBiomeSource();
  }

  public static void verify(ServerLevel level) {
    require(level.getServer().isDedicatedServer(), "Disposable dedicated server only");
    require(level.getServer().isSameThread(), "Run on the server thread");
    require(level.getServer().getPlayerList().getPlayerCount() == 0, "Fixture has real players");
    require(level.getSeed() == 1, "Island update fixture needs seed 1");
    var fixture = new IslandUpdateRegression(level);
    fixture.report.addProperty("seed", level.getSeed());
    fixture.report.addProperty("complete", false);
    try {
      fixture.progress("cache_parity");
      IslandTerrainCacheRegression.verify(level.getChunkSource().randomState());
      fixture.progress("caves");
      fixture.caves();
      fixture.progress("monobiome");
      fixture.monobiome();
      fixture.progress("lava");
      fixture.lava();
      fixture.progress("stones");
      fixture.stones();
      fixture.progress("complete");
      fixture.report.addProperty("complete", true);
    } catch (RuntimeException failure) {
      fixture.report.addProperty("failure", failure.toString());
      throw failure;
    } finally {
      fixture.report.addProperty("full_chunks_inspected", fixture.chunks.size());
      String output = System.getProperty("aeroscapes.regression.islandUpdateReport");
      if (output != null) fixture.write(Path.of(output));
    }
    LogUtils.getLogger()
        .info(
            "Island update regression passed: natural caves, monobiome, volcanic lava/liner and granite/diorite; {} FULL chunks",
            fixture.chunks.size());
    VolcanicFluidRegression.start(level, fixture.lavaCandidate());
  }

  private void caves() {
    CaveNetworkRegression.verify(level);
  }

  private void monobiome() {
    BiomeSource delegate = delegate();
    for (int cellX = -10; cellX <= 10; cellX++) {
      for (int cellZ = -10; cellZ <= 10; cellZ++) {
        IslandLayout.SmallIsland island = terrain.layout().smallIsland(cellX * 256, cellZ * 256, 0);
        if (island == null
            || !island.monobiome()
            || terrain.layout().largeIsland(island.centerX(), island.centerZ()) != null
            || terrain.layout().lavaIsland(island.centerX(), island.centerZ()) != null) continue;
        var shape = island.shape(terrain.layout().seed(), island.centerX(), island.centerZ());
        int y = Math.floorDiv((int) Math.floor((shape.top() + shape.bottom()) / 2), 4) * 4;
        if (shape.density(y - 4) <= 0.05 || shape.density(y + 4) <= 0.05) continue;
        String expected =
            id(
                delegate.getNoiseBiome(
                    Math.floorDiv(island.centerX(), 4),
                    64,
                    Math.floorDiv(island.centerZ(), 4),
                    level.getChunkSource().randomState().sampler()));
        habitatEnvelope(island.centerX(), island.centerZ(), shape, expected);
        JsonArray witnesses = new JsonArray();
        for (int[] offset : new int[][] {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
          for (int vertical : new int[] {-4, 0, 4}) {
            BlockPos position =
                new BlockPos(
                    island.centerX() + offset[0], y + vertical, island.centerZ() + offset[1]);
            if (island
                    .shape(terrain.layout().seed(), position.getX(), position.getZ())
                    .density(position.getY())
                <= 0.05) continue;
            require(
                expected.equals(biome(position)), "Small-island source changed its center biome");
            String stored =
                id(
                    chunk(position)
                        .getNoiseBiome(
                            position.getX() >> 2, position.getY() >> 2, position.getZ() >> 2));
            require(expected.equals(stored), "FULL small-island biome differs from its source");
            JsonObject witness = point(position);
            witness.addProperty("biome", stored);
            witnesses.add(witness);
          }
        }
        report.add("monobiome", witnesses);
        report.addProperty("monobiome_radius", island.radius());
        return;
      }
    }
    throw new IllegalStateException("No bounded lower monobiome island witness");
  }

  private void lava() {
    IslandLayout.Basin island = lavaCandidate();
    lavaCavernSeparation(island);
    habitatEnvelope(
        island.centerX(),
        island.centerZ(),
        island.shape(island.centerX(), island.centerZ()),
        VOLCANIC);
    JsonArray witnesses = new JsonArray();
    JsonArray floorWitnesses = new JsonArray();
    Map<String, Integer> materials = new LinkedHashMap<>();
    for (String material :
        new String[] {"blackstone", "deepslate", "magma", "basalt", "smooth_basalt"})
      materials.put(material, 0);
    int lavaBlocks = 0;
    for (int[] direction :
        new int[][] {
          {0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
        }) {
      BlockPos position = lavaSample(island, direction[0], direction[1]);
      int x = position.getX();
      int z = position.getZ();
      LevelChunk full = chunk(position);
      for (BlockPos sample :
          BlockPos.betweenClosed(
              full.getPos().getMinBlockX(),
              -121,
              full.getPos().getMinBlockZ(),
              full.getPos().getMaxBlockX(),
              island.waterline() + 48,
              full.getPos().getMaxBlockZ())) {
        var state = full.getBlockState(sample);
        if (state.is(Blocks.LAVA)) lavaBlocks++;
        if (state.is(Blocks.BLACKSTONE)) materials.merge("blackstone", 1, Integer::sum);
        if (state.is(Blocks.DEEPSLATE)) materials.merge("deepslate", 1, Integer::sum);
        if (state.is(Blocks.MAGMA_BLOCK)) materials.merge("magma", 1, Integer::sum);
        if (state.is(Blocks.BASALT)) materials.merge("basalt", 1, Integer::sum);
        if (state.is(Blocks.SMOOTH_BASALT)) materials.merge("smooth_basalt", 1, Integer::sum);
      }
      var column = terrain.column(x, z);
      int floor = (int) Math.floor(column.lavaFloor());
      int sealed = 0;
      for (int y = floor - 10; y <= floor - 3; y++) {
        BlockPos below = new BlockPos(x, y, z);
        var state = full.getBlockState(below);
        require(
            !state.isAir() && state.getFluidState().isEmpty(),
            "Lava floor liner has a breach at " + below);
        sealed++;
      }
      JsonObject floorPoint = point(new BlockPos(x, floor, z));
      floorPoint.addProperty("solid_liner_samples", sealed);
      floorWitnesses.add(floorPoint);
      require(VOLCANIC.equals(biome(position)), "Lava island source biome is not volcanic");
      String stored = id(full.getNoiseBiome(x >> 2, island.waterline() >> 2, z >> 2));
      require(VOLCANIC.equals(stored), "FULL lava island biome is not volcanic");
      JsonObject witness = point(position);
      witness.addProperty("biome", stored);
      witnesses.add(witness);
    }
    report.add("lava_biomes", witnesses);
    report.add("lava_liner", floorWitnesses);
    JsonObject rocks = new JsonObject();
    materials.forEach(rocks::addProperty);
    report.add("volcanic_rocks", rocks);
    report.addProperty("lava_blocks", lavaBlocks);
    require(lavaBlocks > 0, "Selected FULL lava island contains no lava");
    require(
        materials.getOrDefault("blackstone", 0) > 0,
        "Selected FULL lava island lacks its volcanic surface material");
    require(
        materials.getOrDefault("basalt", 0) > 0 && materials.getOrDefault("smooth_basalt", 0) > 0,
        "Selected FULL lava island lacks its new basalt columns or cooled surface patches");
  }

  private void lavaCavernSeparation(IslandLayout.Basin lava) {
    int x = lava.centerX();
    int z = lava.centerZ();
    int floor = (int) Math.floor(lava.floor(x, z));
    var unsafe = new BoundingBox(x, floor - 20, z, x, floor + 2, z);
    var safe = new BoundingBox(x, floor - 48, z, x, floor - 40, z);
    try {
      var method =
          SupportedStructure.class.getDeclaredMethod(
              "cavernSeparatesBasins", CityCavern.class, BoundingBox.class, IslandTerrain.class);
      method.setAccessible(true);
      for (var part : List.of(unsafe, safe)) {
        boolean separated =
            (boolean)
                method.invoke(null, new CityCavern(List.of(part)), part.inflatedBy(12), terrain);
        require(
            separated == (part == safe),
            "City/template AIR protection does not respect the local lava liner");
      }
      report.addProperty("lava_cavern_separation_controls", 2);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot verify lava cavern separation", failure);
    }
  }

  private void habitatEnvelope(int x, int z, IslandLayout.VerticalShape shape, String expected) {
    var sampler = (IslandBiomeSampler) (Object) level.getChunkSource().randomState().sampler();
    boolean deferred = sampler.aeroscapesReimagine$deferTerrainMask();
    int growth = (int) Math.ceil(terrain.form().maxValue() * shape.scale());
    sampler.aeroscapesReimagine$deferTerrainMask(true);
    try {
      for (int y :
          new int[] {
            (int) Math.floor(shape.bottom()) - growth, (int) Math.ceil(shape.top()) + growth
          })
        require(
            expected.equals(biome(new BlockPos(x, y, z))),
            "Island habitat does not cover its allowed 3D form envelope");
    } finally {
      sampler.aeroscapesReimagine$deferTerrainMask(deferred);
    }
  }

  private static BlockPos lavaSample(IslandLayout.Basin island, int directionX, int directionZ) {
    if (directionX == 0 && directionZ == 0)
      return new BlockPos(island.centerX(), island.waterline(), island.centerZ());
    int limit = (int) Math.ceil(Math.max(island.radiusX(), island.radiusZ()) * 1.5);
    for (int distance = 16; distance <= limit; distance += 4) {
      int x = island.centerX() + directionX * distance;
      int z = island.centerZ() + directionZ * distance;
      double radial = island.radius(x, z);
      if (radial >= 0.85 && radial < 1) return new BlockPos(x, island.waterline(), z);
    }
    throw new IllegalStateException("No finite lava rim sample");
  }

  private IslandLayout.Basin lavaCandidate() {
    for (int cellX = -8; cellX <= 8; cellX++) {
      for (int cellZ = -8; cellZ <= 8; cellZ++) {
        var island = terrain.layout().lavaIsland(cellX * 1024, cellZ * 1024);
        if (island == null) continue;
        var column = terrain.column(island.centerX(), island.centerZ());
        if (column.lavaRadial() >= 0.35 || column.lavaFloor() >= island.waterline() - 8) continue;
        boolean open = true;
        for (int y = island.waterline(); y < 304; y += 8)
          if (terrain.biomeDensity(new SinglePointContext(island.centerX(), y, island.centerZ()))
              > 0) open = false;
        if (open) return island;
      }
    }
    throw new IllegalStateException("No bounded open lava island candidate");
  }

  private void stones() {
    int inspected = 0;
    int granite = 0;
    int diorite = 0;
    JsonArray witnesses = new JsonArray();
    for (int x = -1024; x <= 1024 && inspected < 40 && (granite == 0 || diorite == 0); x += 32) {
      for (int z = -1024; z <= 1024 && inspected < 40 && (granite == 0 || diorite == 0); z += 32) {
        boolean island = false;
        for (int y = 80; y <= 280; y += 16)
          if (terrain.biomeDensity(new SinglePointContext(x + 8, y, z + 8)) > 0.3) island = true;
        if (!island) continue;
        var full = chunk(new BlockPos(x, 80, z));
        inspected++;
        for (BlockPos position : BlockPos.betweenClosed(x, 65, z, x + 15, 303, z + 15)) {
          var state = full.getBlockState(position);
          if (state.is(Blocks.GRANITE)) {
            if (granite++ == 0) witnesses.add(point(position));
          } else if (state.is(Blocks.DIORITE)) {
            if (diorite++ == 0) witnesses.add(point(position));
          }
        }
      }
    }
    report.addProperty("stone_chunks_inspected", inspected);
    report.addProperty("natural_granite_above_64", granite);
    report.addProperty("natural_diorite_above_64", diorite);
    report.add("stone_witnesses", witnesses);
    require(
        granite > 0 && diorite > 0,
        "Natural upper-island stone witness missing after " + inspected + " FULL chunks");
  }

  private LevelChunk chunk(BlockPos position) {
    return chunks.computeIfAbsent(
        new ChunkPos(position),
        key -> {
          LogUtils.getLogger()
              .info(
                  "Island update FULL chunk request: stage={} chunk={}",
                  report.get("stage").getAsString(),
                  key);
          return level.getChunk(key.x, key.z);
        });
  }

  private void progress(String stage) {
    report.addProperty("stage", stage);
    LogUtils.getLogger()
        .info("Island update stage: {} ({} FULL chunks inspected)", stage, chunks.size());
  }

  private String biome(BlockPos position) {
    return id(
        source.getNoiseBiome(
            position.getX() >> 2,
            position.getY() >> 2,
            position.getZ() >> 2,
            level.getChunkSource().randomState().sampler()));
  }

  private BiomeSource delegate() {
    try {
      BiomeSource current = source;
      Class<?> injector =
          Class.forName(
              "dev.worldgen.lithostitched.impl.worldgen.biomeinjector.internal.InjectorBiomeSource");
      if (!(current instanceof IslandBiomeSource)) {
        Object wrapped = injector.getConstructor(BiomeSource.class).newInstance(current);
        Object unwrapped = injector.getMethod("rootDelegate").invoke(wrapped);
        require(unwrapped instanceof BiomeSource, "Missing root biome source");
        current = (BiomeSource) unwrapped;
      }
      require(current instanceof IslandBiomeSource, "Missing owned biome source");
      var field = IslandBiomeSource.class.getDeclaredField("delegate");
      field.setAccessible(true);
      Object unwrapped = field.get(current);
      require(unwrapped instanceof BiomeSource, "Missing native biome delegate");
      return (BiomeSource) unwrapped;
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot inspect pinned biome source", failure);
    }
  }

  private void write(Path output) {
    require(output.isAbsolute(), "Island update report needs an absolute path");
    try {
      Files.createDirectories(output.getParent());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write island update report " + output, failure);
    }
  }

  private static JsonObject point(BlockPos position) {
    JsonObject point = new JsonObject();
    point.addProperty("x", position.getX());
    point.addProperty("y", position.getY());
    point.addProperty("z", position.getZ());
    return point;
  }

  private static String id(Holder<Biome> biome) {
    return biome.unwrapKey().orElseThrow().location().toString();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
