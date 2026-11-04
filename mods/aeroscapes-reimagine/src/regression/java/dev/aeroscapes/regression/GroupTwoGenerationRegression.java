package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.ninni.spawn.server.data.DodoIslandSavedData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
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
                replay == null ? find(level, id) : saved(level, id, Path.of(replay));
            JsonObject site = inspect(level, id, start);
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

  private static StructureStart find(ServerLevel level, ResourceLocation id) {
    var holder =
        level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(id).orElseThrow();
    if (id.toString().equals("spawn:dodo_island")) {
      ChunkPos chunk = DodoIslandSavedData.findForLocate(level);
      require(chunk != null, "No native unique Dodo location");
      var start = level.getChunk(chunk.x, chunk.z).getStartForStructure(holder.value());
      require(
          start != null && start.isValid(), "Unique Dodo search did not produce a natural start");
      return start;
    }
    List<RandomSpreadStructurePlacement> placements =
        level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).stream()
            .filter(RandomSpreadStructurePlacement.class::isInstance)
            .map(RandomSpreadStructurePlacement.class::cast)
            .toList();
    require(!placements.isEmpty(), "No native random-spread placement for " + id);
    long deadline = System.nanoTime() + Duration.ofSeconds(240).toNanos();
    int candidates = 0;
    for (int radius = 0; radius <= 128; radius++)
      for (int regionX = -radius; regionX <= radius; regionX++)
        for (int regionZ = -radius; regionZ <= radius; regionZ++) {
          if (Math.max(Math.abs(regionX), Math.abs(regionZ)) != radius) continue;
          require(
              System.nanoTime() < deadline,
              "Natural search timed out: " + id + " candidates=" + candidates);
          for (var placement : placements) {
            ChunkPos origin =
                placement.getPotentialStructureChunk(
                    level.getSeed(), regionX * placement.spacing(), regionZ * placement.spacing());
            candidates++;
            if (!placement.isStructureChunk(
                level.getChunkSource().getGeneratorState(), origin.x, origin.z)) continue;
            Structure structure = holder.value();
            var generator = level.getChunkSource().getGenerator();
            var context =
                new Structure.GenerationContext(
                    level.registryAccess(),
                    generator,
                    generator.getBiomeSource(),
                    level.getChunkSource().randomState(),
                    level.getStructureManager(),
                    level.getSeed(),
                    origin,
                    level,
                    structure.biomes()::contains);
            if (structure.findValidGenerationPoint(context).isEmpty()) continue;
            var start = level.getChunk(origin.x, origin.z).getStartForStructure(structure);
            if (start != null && start.isValid()) return start;
          }
        }
    throw new IllegalStateException(
        "No natural FULL start found: " + id + " candidates=" + candidates);
  }

  private static JsonObject inspect(ServerLevel level, ResourceLocation id, StructureStart start) {
    var bounds = start.getBoundingBox();
    for (int x = Math.floorDiv(bounds.minX(), 16); x <= Math.floorDiv(bounds.maxX(), 16); x++)
      for (int z = Math.floorDiv(bounds.minZ(), 16); z <= Math.floorDiv(bounds.maxZ(), 16); z++)
        require(
            level.getChunk(x, z).getPersistedStatus() == ChunkStatus.FULL,
            "Assembly chunk is not FULL");
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    var saved = start.createTag(serialization, start.getChunkPos());
    var restored = StructureStart.loadStaticStart(serialization, saved, level.getSeed());
    require(restored != null && restored.isValid(), "Native structure did not reload");
    ListTag before = new ListTag();
    ListTag after = new ListTag();
    start.getPieces().forEach(piece -> before.add(piece.createTag(serialization)));
    restored.getPieces().forEach(piece -> after.add(piece.createTag(serialization)));
    require(before.equals(after), "Saved native pieces changed on reload: " + id);
    JsonObject report = new JsonObject();
    report.addProperty("structure", id.toString());
    report.addProperty("chunk_x", start.getChunkPos().x);
    report.addProperty("chunk_z", start.getChunkPos().z);
    report.addProperty("pieces", start.getPieces().size());
    report.addProperty("bounds", bounds.toString());
    var center = bounds.getCenter();
    report.addProperty("x", center.getX());
    report.addProperty(
        "y",
        level.getHeight(
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                center.getX(),
                center.getZ())
            + 3);
    report.addProperty("z", center.getZ());
    JsonObject blocks = new JsonObject();
    report.add("blocks", blocks);
    int containers = 0;
    JsonArray loot = new JsonArray();
    report.add("loot_tables", loot);
    JsonArray nativeContainers = new JsonArray();
    report.add("native_containers", nativeContainers);
    String paintedBiome = before.getCompound(0).getString("Biome");
    int paintedMaterialSamples = 0;
    report.addProperty("native_painted_biome", paintedBiome);
    for (int chunkX = Math.floorDiv(bounds.minX(), 16);
        chunkX <= Math.floorDiv(bounds.maxX(), 16);
        chunkX++)
      for (int chunkZ = Math.floorDiv(bounds.minZ(), 16);
          chunkZ <= Math.floorDiv(bounds.maxZ(), 16);
          chunkZ++) {
        var chunk = level.getChunk(chunkX, chunkZ);
        for (var entry : chunk.getBlockEntities().entrySet()) {
          var position = entry.getKey();
          if (position.getX() < bounds.minX()
              || position.getX() > bounds.maxX()
              || position.getZ() < bounds.minZ()
              || position.getZ() > bounds.maxZ()
              || position.getY() < bounds.minY()
              || position.getY() > bounds.maxY() + 48) continue;
          var tag = entry.getValue().saveWithFullMetadata(level.registryAccess());
          if (tag.contains("LootTable")) {
            loot.add(tag.getString("LootTable"));
            containers++;
          }
          if (tag.getString("id").startsWith("spawn:")
              || tag.toString().contains("spawn:dodo_translator")) {
            JsonObject container = new JsonObject();
            container.addProperty("id", tag.getString("id"));
            container.addProperty("x", position.getX());
            container.addProperty("y", position.getY());
            container.addProperty("z", position.getZ());
            JsonArray items = new JsonArray();
            for (var value : tag.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
              var item = (net.minecraft.nbt.CompoundTag) value;
              JsonObject stack = new JsonObject();
              stack.addProperty("id", item.getString("id"));
              stack.addProperty("count", item.getInt("count"));
              items.add(stack);
            }
            container.add("items", items);
            JsonObject lists = new JsonObject();
            for (String name : tag.getAllKeys())
              if (tag.contains(name, net.minecraft.nbt.Tag.TAG_LIST))
                lists.addProperty(
                    name, tag.getList(name, net.minecraft.nbt.Tag.TAG_COMPOUND).size());
            container.add("stored_lists", lists);
            nativeContainers.add(container);
          }
        }
        for (int x = Math.max(bounds.minX(), chunkX * 16);
            x <= Math.min(bounds.maxX(), chunkX * 16 + 15);
            x++)
          for (int z = Math.max(bounds.minZ(), chunkZ * 16);
              z <= Math.min(bounds.maxZ(), chunkZ * 16 + 15);
              z++)
            for (int y = Math.max(bounds.minY(), level.getMinBuildHeight());
                y <= Math.min(bounds.maxY() + 48, level.getMaxBuildHeight() - 1);
                y++) {
              var state = chunk.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
              if (!paintedBiome.isEmpty()
                  && (x & 3) == 2
                  && (y & 3) == 2
                  && (z & 3) == 2
                  && !state.isAir()
                  && chunk
                      .getNoiseBiome(x >> 2, y >> 2, z >> 2)
                      .unwrapKey()
                      .orElseThrow()
                      .location()
                      .toString()
                      .equals(paintedBiome)) paintedMaterialSamples++;
              var block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
              if (!block.getNamespace().equals("spawn") && !block.getNamespace().equals("rocks"))
                continue;
              String name = block.toString();
              blocks.addProperty(name, blocks.has(name) ? blocks.get(name).getAsInt() + 1 : 1);
            }
      }
    report.addProperty("unopened_loot_containers", containers);
    report.addProperty("native_painted_material_samples", paintedMaterialSamples);
    require(
        paintedBiome.isEmpty() || paintedMaterialSamples > 0,
        "Native painted island biome has no stored material witness: " + id);
    return report;
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
