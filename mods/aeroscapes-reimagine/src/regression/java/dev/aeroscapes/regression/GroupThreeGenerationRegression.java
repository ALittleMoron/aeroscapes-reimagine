package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Natural selected archaeological and wetland assemblies in saved FULL terrain. */
final class GroupThreeGenerationRegression {
  private GroupThreeGenerationRegression() {}

  static void run(ServerLevel level, Path output) {
    ArrayDeque<Runnable> checks = new ArrayDeque<>();
    checks.add(() -> RuntimeJsonPatchesRegression.verify(level));
    checks.add(() -> BiolithBiomeRegression.verify(level, output.resolveSibling("biolith.json")));
    String selected = System.getProperty("aeroscapes.regression.groupThreeTargets");
    List<ResourceLocation> targets =
        selected == null
            ? level.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet().stream()
                .filter(
                    id -> Set.of("wetland_whimsy", "betterarcheology").contains(id.getNamespace()))
                .sorted()
                .toList()
            : Arrays.stream(selected.split(","))
                .map(String::strip)
                .map(ResourceLocation::parse)
                .toList();
    int minimumPieces = Integer.getInteger("aeroscapes.regression.groupThreeMinimumPieces", 1);
    require(minimumPieces >= 1 && minimumPieces <= 512, "Invalid natural assembly minimum");
    Audit audit = new Audit(level, output, checks, targets, minimumPieces);
    NeoForge.EVENT_BUS.addListener(audit::tick);
  }

  private static final class Audit {
    private final ServerLevel level;
    private final Path output;
    private final ArrayDeque<Runnable> checks;
    private final ArrayDeque<ResourceLocation> targets;
    private final int minimumPieces;
    private final JsonObject report = new JsonObject();
    private final JsonArray sites = new JsonArray();
    private NaturalStructureWitness.Search search;
    private ResourceLocation current;
    private StructureStart ready;
    private boolean finished;

    private Audit(
        ServerLevel level,
        Path output,
        ArrayDeque<Runnable> checks,
        List<ResourceLocation> targets,
        int minimumPieces) {
      this.level = level;
      this.output = output;
      this.checks = checks;
      this.targets = new ArrayDeque<>(targets);
      this.minimumPieces = minimumPieces;
      report.addProperty("seed", level.getSeed());
      report.addProperty("minimum_pieces", minimumPieces);
      report.add("sites", sites);
      write(output, report);
    }

    private void tick(ServerTickEvent.Post event) {
      if (event.getServer() != level.getServer() || finished) return;
      try {
        if (!checks.isEmpty()) {
          checks.removeFirst().run();
          return;
        }
        if (ready != null) {
          var site = NaturalStructureWitness.inspect(level, current, ready);
          if (current.toString().equals("nova_structures:illager_manor"))
            site.add(
                "manor_support_regression", ManorFoundationRegression.verifyStart(level, ready));
          var fossils = GroupThreeArcheologyRegression.naturalFinds(level, current, ready);
          if (!fossils.isEmpty()) site.add("native_fossil_deposits", fossils);
          if (minimumPieces > 2 && current.toString().equals("wetland_whimsy:swamp_dungeon"))
            site.add(
                "dungeon_support_regression", WetlandDungeonSupportRegression.verify(level, ready));
          site.add("search", search.statistics());
          sites.add(site);
          write(output, report);
          LogUtils.getLogger().info("Group three natural FULL witness: {}", site);
          search.close();
          search = null;
          ready = null;
          return;
        }
        if (search != null) {
          ready = search.poll().orElse(null);
          return;
        }
        if (!targets.isEmpty()) {
          current = targets.removeFirst();
          search = new NaturalStructureWitness.Search(level, current, minimumPieces);
          return;
        }
        level.getServer().saveAllChunks(true, true, true);
        finished = true;
        report.addProperty("complete", true);
        write(output, report);
        LogUtils.getLogger().info("Group three generation regression passed");
      } catch (RuntimeException failure) {
        finished = true;
        report.addProperty("failure", failure.toString());
        if (search != null) {
          report.add("failed_search", search.statistics());
          search.close();
        }
        write(output, report);
        throw failure;
      }
    }
  }

  private static void write(Path output, JsonObject report) {
    try {
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write natural group three witnesses", failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
