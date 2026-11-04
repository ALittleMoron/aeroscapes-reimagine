package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.ninni.spawn.server.data.DodoIslandSavedData;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Natural selection, saved FULL assembly and native piece roundtrip witnesses. */
final class NaturalStructureWitness {
  private NaturalStructureWitness() {}

  static StructureStart find(ServerLevel level, ResourceLocation id) {
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
    List<RandomSpreadStructurePlacement> placements = placements(level, id);
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
            if (prefit(level, structure, origin).isEmpty()) continue;
            var start = level.getChunk(origin.x, origin.z).getStartForStructure(structure);
            if (start != null && start.isValid()) return start;
          }
        }
    throw new IllegalStateException(
        "No natural FULL start found: " + id + " candidates=" + candidates);
  }

  private static List<RandomSpreadStructurePlacement> placements(
      ServerLevel level, ResourceLocation id) {
    var holder =
        level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(id).orElseThrow();
    var placements =
        level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder).stream()
            .filter(RandomSpreadStructurePlacement.class::isInstance)
            .map(RandomSpreadStructurePlacement.class::cast)
            .toList();
    require(!placements.isEmpty(), "No native random-spread placement for " + id);
    return placements;
  }

  private static Optional<Structure.GenerationStub> prefit(
      ServerLevel level, Structure structure, ChunkPos origin) {
    var generator = level.getChunkSource().getGenerator();
    return structure.findValidGenerationPoint(
        new Structure.GenerationContext(
            level.registryAccess(),
            generator,
            generator.getBiomeSource(),
            level.getChunkSource().randomState(),
            level.getStructureManager(),
            level.getSeed(),
            origin,
            level,
            structure.biomes()::contains));
  }

  /**
   * Native search and FULL loads yield between server-tick slices; one attempt can exceed a slice.
   */
  static final class Search implements AutoCloseable {
    private static final TicketType<ChunkPos> TICKET =
        TicketType.create(
            "aeroscapes_structure_witness", Comparator.comparingLong(ChunkPos::toLong));
    private final ServerLevel level;
    private final ResourceLocation id;
    private final Structure structure;
    private final List<RandomSpreadStructurePlacement> placements;
    private final int minimumPieces;
    private final long started = System.nanoTime();
    private final long deadline;
    private final Set<ChunkPos> tickets = new HashSet<>();
    private final ArrayDeque<ChunkPos> assemblyChunks = new ArrayDeque<>();
    private CompletableFuture<ChunkResult<ChunkAccess>> pending;
    private ChunkPos pendingChunk;
    private StructureStart selected;
    private int radius;
    private int regionX;
    private int regionZ;
    private int placementIndex;
    private int candidates;
    private int nativeValid;
    private int shortAssemblies;
    private int savedRejected;
    private int fullRequests;
    private long nextProgress;
    private long maximumCandidateNanos;
    private boolean closed;

    Search(ServerLevel level, ResourceLocation id, int minimumPieces) {
      require(minimumPieces >= 1 && minimumPieces <= 512, "Invalid natural assembly minimum");
      this.level = level;
      this.id = id;
      this.minimumPieces = minimumPieces;
      structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);
      require(structure != null, "Missing natural structure: " + id);
      placements = placements(level, id);
      // Large-assembly discovery filters native short starts; yield between attempts, with a finite
      // wall deadline.
      deadline = started + Duration.ofSeconds(minimumPieces > 1 ? 600 : 240).toNanos();
    }

    Optional<StructureStart> poll() {
      require(!closed, "Natural search is closed");
      long now = System.nanoTime();
      require(now < deadline, "Natural search timed out: " + id + " " + statistics());
      if (now >= nextProgress) {
        nextProgress = now + Duration.ofSeconds(15).toNanos();
        LogUtils.getLogger().info("Natural incremental search {}: {}", id, statistics());
      }
      long sliceEnd = now + 15_000_000L;
      do {
        if (pending != null) {
          if (!pending.isDone()) return Optional.empty();
          ChunkAccess chunk =
              pending
                  .join()
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Natural FULL chunk unavailable: " + pendingChunk));
          pending = null;
          if (selected == null) {
            var start = chunk.getStartForStructure(structure);
            if (start == null || !start.isValid() || start.getPieces().size() < minimumPieces) {
              savedRejected++;
              release(pendingChunk);
            } else {
              selected = start;
              prepareAssembly();
            }
          }
          pendingChunk = null;
        }
        if (selected != null) {
          if (assemblyChunks.isEmpty()) return Optional.of(selected);
          request(assemblyChunks.removeFirst());
          return Optional.empty();
        }
        Candidate candidate = nextCandidate();
        require(candidate != null, "No natural FULL start found: " + id + " " + statistics());
        candidates++;
        long candidateStarted = System.nanoTime();
        try {
          if (!candidate
              .placement()
              .isStructureChunk(
                  level.getChunkSource().getGeneratorState(),
                  candidate.origin().x,
                  candidate.origin().z)) continue;
          var stub = prefit(level, structure, candidate.origin());
          if (stub.isEmpty()) continue;
          nativeValid++;
          if (stub.orElseThrow().getPiecesBuilder().build().pieces().size() < minimumPieces) {
            shortAssemblies++;
            continue;
          }
          request(candidate.origin());
          return Optional.empty();
        } finally {
          maximumCandidateNanos =
              Math.max(maximumCandidateNanos, System.nanoTime() - candidateStarted);
        }
      } while (System.nanoTime() < sliceEnd);
      return Optional.empty();
    }

    private record Candidate(ChunkPos origin, RandomSpreadStructurePlacement placement) {}

    private Candidate nextCandidate() {
      while (radius <= 128) {
        if (Math.max(Math.abs(regionX), Math.abs(regionZ)) != radius) {
          regionZ = radius;
          continue;
        }
        if (placementIndex < placements.size()) {
          var placement = placements.get(placementIndex++);
          return new Candidate(
              placement.getPotentialStructureChunk(
                  level.getSeed(), regionX * placement.spacing(), regionZ * placement.spacing()),
              placement);
        }
        placementIndex = 0;
        if (++regionZ > radius) {
          regionZ = -radius;
          if (++regionX > radius) {
            radius++;
            regionX = -radius;
            regionZ = -radius;
          }
        }
      }
      return null;
    }

    private void prepareAssembly() {
      var bounds = selected.getBoundingBox();
      for (int x = Math.floorDiv(bounds.minX(), 16); x <= Math.floorDiv(bounds.maxX(), 16); x++)
        for (int z = Math.floorDiv(bounds.minZ(), 16); z <= Math.floorDiv(bounds.maxZ(), 16); z++) {
          ChunkPos chunk = new ChunkPos(x, z);
          if (!tickets.contains(chunk)) assemblyChunks.addLast(chunk);
        }
    }

    private void request(ChunkPos chunk) {
      require(pending == null, "Natural FULL request overlaps another request");
      pendingChunk = chunk;
      tickets.add(chunk);
      level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
      fullRequests++;
      pending =
          CompletableFuture.supplyAsync(
                  () ->
                      level
                          .getChunkSource()
                          .getChunkFuture(chunk.x, chunk.z, ChunkStatus.FULL, true),
                  Util.backgroundExecutor())
              .thenCompose(future -> future);
    }

    JsonObject statistics() {
      JsonObject statistics = new JsonObject();
      statistics.addProperty("candidates", candidates);
      statistics.addProperty("native_valid", nativeValid);
      statistics.addProperty("native_short_assemblies", shortAssemblies);
      statistics.addProperty("saved_start_rejections", savedRejected);
      statistics.addProperty("FULL_requests", fullRequests);
      statistics.addProperty("radius", radius);
      statistics.addProperty("elapsed_seconds", (System.nanoTime() - started) / 1_000_000_000.0);
      statistics.addProperty("maximum_candidate_seconds", maximumCandidateNanos / 1_000_000_000.0);
      return statistics;
    }

    private void release(ChunkPos chunk) {
      if (tickets.remove(chunk)) level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
    }

    @Override
    public void close() {
      for (ChunkPos chunk : Set.copyOf(tickets)) release(chunk);
      if (pending != null) pending.cancel(false);
      pending = null;
      pendingChunk = null;
      assemblyChunks.clear();
      closed = true;
    }
  }

  static JsonObject inspect(ServerLevel level, ResourceLocation id, StructureStart start) {
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
        for (var position : chunk.getBlockEntitiesPos()) {
          if (position.getX() < bounds.minX()
              || position.getX() > bounds.maxX()
              || position.getZ() < bounds.minZ()
              || position.getZ() > bounds.maxZ()
              || position.getY() < bounds.minY()
              || position.getY() > bounds.maxY() + 48) continue;
          var entity = chunk.getBlockEntity(position);
          require(entity != null, "Missing saved structure block entity: " + position);
          var tag = entity.saveWithFullMetadata(level.registryAccess());
          if (tag.contains("LootTable")) {
            loot.add(tag.getString("LootTable"));
            containers++;
          }
          if (tag.getString("id").startsWith("spawn:")
              || tag.getString("id").startsWith("wetland_whimsy:")
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
              if (!block.getNamespace().equals("spawn")
                  && !block.getNamespace().equals("rocks")
                  && !block.getNamespace().equals(id.getNamespace())) continue;
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

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
