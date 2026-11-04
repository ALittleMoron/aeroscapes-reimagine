package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Saved natural aerial starts, whole assemblies, crews and clean merchant offers. */
final class AerialGenerationRegression {
  private static final List<String> TARGETS =
      List.of(
          "cr_aero_structures:airballoon",
          "cr_aero_structures:airship",
          "cr_aero_structures:pillager_airballoon",
          "cr_aero_structures:pillager_airship",
          "skyvillages:skyvillage");

  private AerialGenerationRegression() {}

  static void run(ServerLevel level, Path output) {
    ArrayDeque<Runnable> checks = new ArrayDeque<>();
    JsonObject report = new JsonObject();
    report.addProperty("seed", level.getSeed());
    JsonArray sites = new JsonArray();
    report.add("sites", sites);
    Map<ResourceLocation, StructureStart> starts = new HashMap<>();
    checks.add(() -> RuntimeJsonPatchesRegression.verify(level));
    checks.add(() -> AerialTemplateRegression.verify(level));
    for (String name : TARGETS) {
      ResourceLocation id = ResourceLocation.parse(name);
      checks.add(
          () -> {
            StructureStart start = find(level, id);
            starts.put(id, start);
            force(level, start, true);
          });
      checks.add(
          () -> {
            StructureStart start = starts.remove(id);
            JsonObject site = inspect(level, id, start);
            sites.add(site);
            write(output, report);
            LogUtils.getLogger().info("Aerial natural FULL witness: {}", site);
            force(level, start, false);
          });
    }
    checks.add(
        () -> {
          level.getServer().saveAllChunks(true, true, true);
          report.addProperty("complete", true);
          write(output, report);
          LogUtils.getLogger().info("Aerial generation regression passed");
        });
    int[] ticks = {0};
    NeoForge.EVENT_BUS.addListener(
        (ServerTickEvent.Post event) -> {
          if (event.getServer() == level.getServer() && !checks.isEmpty() && ++ticks[0] >= 40) {
            ticks[0] = 0;
            checks.removeFirst().run();
          }
        });
  }

  private static void force(ServerLevel level, StructureStart start, boolean forced) {
    var bounds = start.getBoundingBox();
    for (int x = Math.floorDiv(bounds.minX(), 16); x <= Math.floorDiv(bounds.maxX(), 16); x++)
      for (int z = Math.floorDiv(bounds.minZ(), 16); z <= Math.floorDiv(bounds.maxZ(), 16); z++) {
        level.getChunk(x, z);
        level.setChunkForced(x, z, forced);
      }
  }

  private static StructureStart find(ServerLevel level, ResourceLocation id) {
    var holder =
        level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(id).orElseThrow();
    var placements =
        level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).stream()
            .filter(RandomSpreadStructurePlacement.class::isInstance)
            .map(RandomSpreadStructurePlacement.class::cast)
            .toList();
    require(!placements.isEmpty(), "Missing natural aerial placement: " + id);
    long deadline = System.nanoTime() + Duration.ofSeconds(180).toNanos();
    int attempts = 0;
    for (int radius = 0; radius < 48; radius++)
      for (int regionX = -radius; regionX <= radius; regionX++)
        for (int regionZ = -radius; regionZ <= radius; regionZ++) {
          if (Math.max(Math.abs(regionX), Math.abs(regionZ)) != radius) continue;
          require(System.nanoTime() < deadline, "Natural aerial search timed out: " + id);
          for (var placement : placements) {
            ChunkPos origin =
                placement.getPotentialStructureChunk(
                    level.getSeed(), regionX * placement.spacing(), regionZ * placement.spacing());
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
            attempts++;
            if (structure.findValidGenerationPoint(context).isEmpty()) continue;
            var start = level.getChunk(origin.x, origin.z).getStartForStructure(structure);
            if (start != null && start.isValid()) {
              LogUtils.getLogger()
                  .info("Aerial search {} accepted after {} attempts", id, attempts);
              return start;
            }
          }
        }
    throw new IllegalStateException("No saved natural aerial start: " + id);
  }

  private static JsonObject inspect(ServerLevel level, ResourceLocation id, StructureStart start) {
    var bounds = start.getBoundingBox();
    require(bounds.maxY() < 384, "Aerial assembly exceeds Y383: " + id);
    require(bounds.minY() >= 144, "Aerial assembly lies below its band: " + id);
    for (int x = Math.floorDiv(bounds.minX(), 16); x <= Math.floorDiv(bounds.maxX(), 16); x++)
      for (int z = Math.floorDiv(bounds.minZ(), 16); z <= Math.floorDiv(bounds.maxZ(), 16); z++)
        require(
            level.getChunk(x, z).getPersistedStatus() == ChunkStatus.FULL,
            "Aerial assembly chunk is not FULL");
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    var saved = start.createTag(serialization, start.getChunkPos());
    var restored = StructureStart.loadStaticStart(serialization, saved, level.getSeed());
    require(restored != null && restored.isValid(), "Aerial start did not reload: " + id);
    ListTag before = new ListTag();
    ListTag after = new ListTag();
    start.getPieces().forEach(piece -> before.add(piece.createTag(serialization)));
    restored.getPieces().forEach(piece -> after.add(piece.createTag(serialization)));
    require(before.equals(after), "Saved aerial pieces changed: " + id);
    JsonObject site = new JsonObject();
    site.addProperty("structure", id.toString());
    site.addProperty("chunk_x", start.getChunkPos().x);
    site.addProperty("chunk_z", start.getChunkPos().z);
    site.addProperty("pieces", start.getPieces().size());
    site.addProperty("bounds", bounds.toString());
    site.addProperty("x", bounds.getCenter().getX());
    site.addProperty("y", bounds.maxY() + 3);
    site.addProperty("z", bounds.getCenter().getZ());
    JsonArray containers = new JsonArray();
    site.add("containers", containers);
    JsonArray stocked = new JsonArray();
    site.add("stocked_containers", stocked);
    for (int x = Math.floorDiv(bounds.minX(), 16); x <= Math.floorDiv(bounds.maxX(), 16); x++)
      for (int z = Math.floorDiv(bounds.minZ(), 16); z <= Math.floorDiv(bounds.maxZ(), 16); z++)
        for (BlockPos position : level.getChunk(x, z).getBlockEntitiesPos()) {
          if (!bounds.isInside(position)) continue;
          var blockEntity = level.getChunk(x, z).getBlockEntity(position);
          require(blockEntity != null, "Missing saved aerial block entity: " + position);
          CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
          if (!tag.getList("Items", Tag.TAG_COMPOUND).isEmpty()) {
            JsonObject container = new JsonObject();
            container.addProperty("x", position.getX());
            container.addProperty("y", position.getY());
            container.addProperty("z", position.getZ());
            JsonArray items = new JsonArray();
            for (var value : tag.getList("Items", Tag.TAG_COMPOUND)) {
              CompoundTag stack = (CompoundTag) value;
              require(stack.getInt("count") > 0, "Empty saved cargo stack");
              items.add(stack.getString("id"));
            }
            container.add("items", items);
            stocked.add(container);
          }
          if (!tag.contains("LootTable")) continue;
          JsonObject container = new JsonObject();
          container.addProperty("loot", tag.getString("LootTable"));
          // Native template placement assigns a fresh seed to each container.
          container.addProperty("loot_seed", tag.getLong("LootTableSeed"));
          container.addProperty("x", position.getX());
          container.addProperty("y", position.getY());
          container.addProperty("z", position.getZ());
          containers.add(container);
        }
    int expectedContainers =
        switch (id.getPath()) {
          case "airballoon" -> 1;
          case "airship" -> 6;
          case "pillager_airballoon" -> 4;
          case "pillager_airship" -> 14;
          default -> 1;
        };
    require(
        containers.size() >= expectedContainers,
        "Missing aerial containers: " + id + " loot=" + containers + " stocked=" + stocked);
    if (id.getPath().equals("pillager_airship"))
      require(stocked.size() == 2, "Missing prefilled airship cargo");
    JsonObject inhabitants = new JsonObject();
    site.add("inhabitants", inhabitants);
    for (Entity entity : level.getEntitiesOfClass(Entity.class, AABB.of(bounds).inflate(2))) {
      String name = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
      inhabitants.addProperty(
          name, inhabitants.has(name) ? inhabitants.get(name).getAsInt() + 1 : 1);
      require(
          !name.equals("naturalist:butterfly") && !name.equals("simulated:contraption_diagram"),
          "Incidental saved entity survived: " + name);
      if (entity instanceof WanderingTrader trader)
        for (var offer : trader.getOffers())
          require(
              !offer.getResult().isEmpty() && !offer.getResult().is(Items.FILLED_MAP),
              "Invalid/stale merchant offer survived");
    }
    int expectedCrew =
        switch (id.getPath()) {
          case "airballoon" -> 1;
          case "airship" -> 6;
          case "pillager_airballoon" -> 6;
          case "pillager_airship" -> 19;
          default -> 1;
        };
    require(
        inhabitants.asMap().values().stream().mapToInt(value -> value.getAsInt()).sum()
            >= expectedCrew,
        "Missing aerial crew: " + id + " " + inhabitants);
    if (id.getNamespace().equals("skyvillages")) {
      require(bounds.minY() >= 288, "Village is not in the upper band");
      require(start.getPieces().size() > 3, "Village has no connected assembly");
      require(inhabitants.has("minecraft:villager"), "Village has no inhabitants");
    }
    BlockPos center = bounds.getCenter();
    require(
        level.getChunkAt(center).getPersistedStatus() == ChunkStatus.FULL,
        "Aerial center was not saved");
    return site;
  }

  private static void write(Path output, JsonObject report) {
    try {
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write aerial generation report", failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
