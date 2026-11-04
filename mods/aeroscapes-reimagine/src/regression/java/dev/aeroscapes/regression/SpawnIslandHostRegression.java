package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.ninni.spawn.server.level.structure.BaseIslandPiece;
import dev.aeroscapes.ecology.SpawnIslandSites;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.zip.ZipFile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Compare real host decisions against the previous compiled implementation, not a copied model. */
final class SpawnIslandHostRegression {
  private static final String HOST = "dev.aeroscapes.ecology.SpawnIslandSites";

  private record Site(int x, int z, int radius) {}

  private static final class ReferenceLoader extends ClassLoader {
    private final byte[] host;

    private ReferenceLoader(byte[] host) {
      super(SpawnIslandSites.class.getClassLoader());
      this.host = host;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (!name.equals(HOST)) return super.loadClass(name, resolve);
      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        if (loaded == null) loaded = defineClass(name, host, 0, host.length);
        if (resolve) resolveClass(loaded);
        return loaded;
      }
    }
  }

  private final ServerLevel level;
  private final Path output;
  private final Method reference;
  private final ArrayDeque<Site> sites = new ArrayDeque<>();
  private final JsonObject report = new JsonObject();
  private final JsonArray rows = new JsonArray();
  private Site previousAccepted;
  private Site active;
  private boolean expected;
  private int phase;
  private long nextCheck;

  private SpawnIslandHostRegression(ServerLevel level, Path plan, Path baseline, Path output)
      throws IOException, ReflectiveOperationException {
    this.level = level;
    this.output = output;
    try (ZipFile archive = new ZipFile(baseline.toFile())) {
      var entry = archive.getEntry(HOST.replace('.', '/') + ".class");
      if (entry == null) throw new IOException("Missing previous host implementation");
      try (var input = archive.getInputStream(entry)) {
        reference =
            new ReferenceLoader(input.readAllBytes())
                .loadClass(HOST)
                .getMethod(
                    "accepts",
                    ChunkGenerator.class,
                    RandomState.class,
                    LevelHeightAccessor.class,
                    int.class,
                    int.class,
                    int.class);
      }
    }
    var data = JsonParser.parseString(Files.readString(plan)).getAsJsonObject();
    require(data.get("seed").getAsLong() == level.getSeed(), "Host plan seed differs");
    for (var element : data.getAsJsonArray("sites")) {
      var site = element.getAsJsonObject();
      ResourceLocation id = ResourceLocation.parse(site.get("structure").getAsString());
      if (!id.getNamespace().equals("spawn")) continue;
      var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
      var chunk =
          level.getChunk(
              site.get("chunk_x").getAsInt(),
              site.get("chunk_z").getAsInt(),
              ChunkStatus.STRUCTURE_STARTS);
      var start = chunk.getStartForStructure(structure);
      require(start != null && start.isValid(), "Missing saved host start: " + id);
      if (!(start.getPieces().getFirst() instanceof BaseIslandPiece piece)) continue;
      int radius = id.getPath().equals("dodo_island") ? 51 : piece.getRadius();
      int x = piece.getOrigin().getX();
      int z = piece.getOrigin().getZ();
      sites.add(new Site(x, z, radius));
      sites.add(new Site(x + 8, z + 8, radius));
      sites.add(new Site(x + 160, z - 160, radius));
    }
    require(sites.size() == 18, "Expected six native island hosts and nearby controls");
    report.addProperty("seed", level.getSeed());
    report.add("sites", rows);
  }

  static void start(ServerLevel level, Path plan, Path baseline, Path output) {
    try {
      var fixture = new SpawnIslandHostRegression(level, plan, baseline, output);
      NeoForge.EVENT_BUS.addListener(fixture::tick);
    } catch (IOException | ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot prepare previous host comparison", failure);
    }
  }

  private void tick(ServerTickEvent.Post event) {
    if (event.getServer() != level.getServer()
        || active == null && sites.isEmpty()
        || System.nanoTime() < nextCheck) return;
    if (active == null) active = sites.removeFirst();
    Site site = active;
    var generator = level.getChunkSource().getGenerator();
    var random = level.getChunkSource().randomState();
    try {
      if (phase == 0) {
        Object value =
            reference.invoke(null, generator, random, level, site.x(), site.z(), site.radius());
        require(value instanceof Boolean, "Invalid previous decision");
        expected = Boolean.TRUE.equals(value);
        phase = 1;
        return;
      }
      if (phase == 1) {
        require(
            SpawnIslandSites.accepts(generator, random, level, site.x(), site.z(), site.radius())
                == expected,
            "Changed uncached host: " + site);
        phase = 2;
        return;
      }
      Site neighbor = previousAccepted;
      SpawnIslandSites.search(
          generator,
          random,
          level,
          () -> {
            require(
                SpawnIslandSites.accepts(
                        generator, random, level, site.x(), site.z(), site.radius())
                    == expected,
                "Changed cached host: " + site);
            if (neighbor != null)
              require(
                  SpawnIslandSites.accepts(
                      generator, random, level, neighbor.x(), neighbor.z(), neighbor.radius()),
                  "Changed previous accepted host");
            require(
                SpawnIslandSites.accepts(
                        generator, random, level, site.x(), site.z(), site.radius())
                    == expected,
                "Changed host after another site");
            return null;
          });
      if (expected) previousAccepted = site;
      JsonObject row = new JsonObject();
      row.addProperty("x", site.x());
      row.addProperty("z", site.z());
      row.addProperty("radius", site.radius());
      row.addProperty("accepted", expected);
      row.addProperty("uncached_and_cached_cross_site_decisions_match", true);
      rows.add(row);
      report.addProperty("complete", sites.isEmpty());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      if (sites.isEmpty()) LogUtils.getLogger().info("Spawn island host parity regression passed");
      active = null;
      phase = 0;
    } catch (IOException | ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot compare previous host decision", failure);
    } finally {
      // Let native tick scheduling recover before the next expensive reference calculation.
      nextCheck = System.nanoTime() + 1_000_000_000L;
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
