package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Natural selected starts, complete FULL assemblies and native saved pieces for the ecology batch.
 */
final class GroupTwoGenerationRegression {
  private GroupTwoGenerationRegression() {}

  static void run(ServerLevel level, Path output) {
    ArrayDeque<Runnable> checks = new ArrayDeque<>();
    checks.add(() -> RuntimeJsonPatchesRegression.verify(level));
    checks.add(
        () ->
            BiolithBiomeRegression.verify(
                level, output.resolveSibling(output.getFileName() + ".biolith.json")));
    checks.add(() -> NativeSurfaceRegression.nativeColumnEdges(level));
    checks.add(() -> LocateRegression.providerRouting(level));
    JsonObject report = new JsonObject();
    report.addProperty("seed", level.getSeed());
    JsonArray sites = new JsonArray();
    report.add("sites", sites);
    String replay = System.getProperty("aeroscapes.regression.groupTwoReplay");
    String targets =
        System.getProperty("aeroscapes.regression.groupTwoTargets", "spawn:dodo_island");
    for (String name : targets.split(",")) {
      checks.add(
          () -> {
            ResourceLocation id = ResourceLocation.parse(name.strip());
            StructureStart start =
                replay == null
                    ? NaturalStructureWitness.find(level, id)
                    : saved(level, id, Path.of(replay));
            JsonObject site = NaturalStructureWitness.inspect(level, id, start);
            sites.add(site);
            write(output, report);
            LogUtils.getLogger().info("Group two natural FULL witness: {}", site);
          });
    }
    checks.add(
        () -> {
          level.getServer().saveAllChunks(true, true, true);
          report.addProperty("complete", true);
          write(output, report);
          LogUtils.getLogger().info("Group two generation regression passed");
        });
    NeoForge.EVENT_BUS.addListener(
        (ServerTickEvent.Post event) -> {
          if (event.getServer() == level.getServer() && !checks.isEmpty())
            checks.removeFirst().run();
        });
  }

  private static StructureStart saved(ServerLevel level, ResourceLocation id, Path plan) {
    try {
      var report = JsonParser.parseString(Files.readString(plan)).getAsJsonObject();
      require(report.get("seed").getAsLong() == level.getSeed(), "Replay seed differs");
      require(report.get("complete").getAsBoolean(), "Replay plan is incomplete");
      for (var entry : report.getAsJsonArray("sites")) {
        var site = entry.getAsJsonObject();
        if (!site.get("structure").getAsString().equals(id.toString())) continue;
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
        require(structure != null, "Replay structure is missing: " + id);
        var start =
            level
                .getChunk(site.get("chunk_x").getAsInt(), site.get("chunk_z").getAsInt())
                .getStartForStructure(structure);
        require(start != null && start.isValid(), "Saved natural start is missing: " + id);
        return start;
      }
      throw new IllegalStateException("No replay site: " + id);
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read saved-site replay plan", failure);
    }
  }

  private static void write(Path output, JsonObject report) {
    try {
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException exception) {
      throw new IllegalStateException("Cannot write group two evidence", exception);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
