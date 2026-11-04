package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.HexFormat;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Finite identical native candidates, one per tick, for before/after support comparisons. */
final class GroupTwoTemplateRegression {
  private record Candidate(ResourceLocation id, ChunkPos chunk, int pass, boolean knownPositive) {}

  private final ServerLevel level;
  private final Path output;
  private final ArrayDeque<Candidate> candidates = new ArrayDeque<>();
  private final JsonObject report = new JsonObject();
  private final JsonArray rows = new JsonArray();

  private GroupTwoTemplateRegression(ServerLevel level, Path plan, Path output) {
    this.level = level;
    this.output = output;
    try {
      var data = JsonParser.parseString(Files.readString(plan)).getAsJsonObject();
      if (data.get("seed").getAsLong() != level.getSeed())
        throw new IllegalStateException("Template comparison seed differs");
      for (int pass = 0; pass < 2; pass++)
        for (var element : data.getAsJsonArray("sites")) {
          var site = element.getAsJsonObject();
          ResourceLocation id = ResourceLocation.parse(site.get("structure").getAsString());
          if (!id.getNamespace().equals("explorify")
              && !id.getPath().equals("ant_mount")
              && !id.getPath().equals("octopolis")) continue;
          int x = site.get("chunk_x").getAsInt();
          int z = site.get("chunk_z").getAsInt();
          candidates.add(new Candidate(id, new ChunkPos(x, z), pass, true));
          candidates.add(new Candidate(id, new ChunkPos(x + 1, z - 1), pass, false));
        }
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read template comparison plan", failure);
    }
    report.addProperty("seed", level.getSeed());
    report.add("candidates", rows);
  }

  static void start(ServerLevel level, Path plan, Path output) {
    var comparison = new GroupTwoTemplateRegression(level, plan, output);
    NeoForge.EVENT_BUS.addListener(comparison::tick);
  }

  private void tick(ServerTickEvent.Post event) {
    if (event.getServer() != level.getServer() || candidates.isEmpty()) return;
    Candidate candidate = candidates.removeFirst();
    var structure =
        level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(candidate.id());
    if (structure == null) throw new IllegalStateException("Missing candidate " + candidate.id());
    var generator = level.getChunkSource().getGenerator();
    var context =
        new Structure.GenerationContext(
            level.registryAccess(),
            generator,
            generator.getBiomeSource(),
            level.getChunkSource().randomState(),
            level.getStructureManager(),
            level.getSeed(),
            candidate.chunk(),
            level,
            structure.biomes()::contains);
    LogUtils.getLogger()
        .info(
            "Template candidate begin: {} {} pass={}",
            candidate.id(),
            candidate.chunk(),
            candidate.pass());
    long started = System.nanoTime();
    var result = structure.findValidGenerationPoint(context);
    long elapsed = System.nanoTime() - started;
    ListTag pieces = new ListTag();
    result.ifPresent(
        stub ->
            stub.getPiecesBuilder()
                .build()
                .pieces()
                .forEach(
                    piece ->
                        pieces.add(
                            piece.createTag(StructurePieceSerializationContext.fromLevel(level)))));
    if (candidate.knownPositive() && result.isEmpty())
      throw new IllegalStateException("Recorded natural positive now rejected: " + candidate);
    JsonObject row = new JsonObject();
    row.addProperty("structure", candidate.id().toString());
    row.addProperty("chunk_x", candidate.chunk().x);
    row.addProperty("chunk_z", candidate.chunk().z);
    row.addProperty("pass", candidate.pass());
    row.addProperty("known_positive", candidate.knownPositive());
    row.addProperty("accepted", result.isPresent());
    row.addProperty("elapsed_ns", elapsed);
    row.addProperty(
        "heap_used", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
    row.addProperty("pieces_sha256", digest(pieces.toString()));
    row.addProperty("random_tail", context.random().nextLong());
    rows.add(row);
    report.addProperty("complete", candidates.isEmpty());
    try {
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write template comparison", failure);
    }
    LogUtils.getLogger()
        .info(
            "Template candidate end: {} accepted={} elapsed_ms={}",
            candidate.id(),
            result.isPresent(),
            elapsed / 1_000_000);
    if (candidates.isEmpty()) LogUtils.getLogger().info("Group two template comparison passed");
  }

  private static String digest(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("Missing SHA-256", failure);
    }
  }
}
