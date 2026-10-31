package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Finite native selected-start collection, promoting only new IDs to origin/root FULL chunks. */
public final class StructureCoverageRegression {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final TicketType<UUID> TICKET =
      TicketType.create("aeroscapes_structure_coverage", UUID::compareTo);

  private StructureCoverageRegression() {}

  public static void start(MinecraftServer server, Path planFile, Path output) {
    if (!server.isDedicatedServer() || !server.isSameThread())
      throw new IllegalStateException("Structure coverage needs the dedicated server thread");
    if (!server.getWorldData().worldGenOptions().generateStructures())
      throw new IllegalStateException("Structure coverage requires generateStructures=true");
    Audit audit = new Audit(server, Plan.read(planFile), output.toAbsolutePath().normalize());
    NeoForge.EVENT_BUS.addListener(audit.tickListener);
    NeoForge.EVENT_BUS.addListener(audit.stopListener);
    LOGGER.info(
        "Structure coverage started: seed={} targets={} groups={} STARTS-level={} FULL-level={} output={}",
        audit.plan.seed,
        audit.targets.size(),
        audit.groups.size(),
        ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS),
        ChunkLevel.byStatus(ChunkStatus.FULL),
        audit.output);
  }

  private record Center(String dimension, int x, int z, String biome, String set) {}

  private record BlockWitness(
      String structure, String rootPieceType, BlockPos local, String block) {}

  private record Plan(
      long seed,
      Set<String> targets,
      int attemptsPerSet,
      Map<String, Integer> setBudgets,
      Map<String, Integer> samplePieceIndices,
      int regionRadius,
      int fullOriginBudget,
      int rootFullBudget,
      int deadlineSeconds,
      int requestTimeoutSeconds,
      boolean requireAllTargets,
      List<Center> centers,
      List<BlockWitness> blockWitnesses,
      JsonObject original) {
    private static Plan read(Path path) {
      try {
        JsonElement parsed = JsonParser.parseString(Files.readString(path));
        if (!parsed.isJsonObject())
          throw new IllegalArgumentException("Coverage plan must be an object");
        JsonObject json = parsed.getAsJsonObject();
        if (integer(json, "format", 1) != 1)
          throw new IllegalArgumentException("Unsupported structure coverage plan format");
        long seed = number(json, "seed").longValueExact();
        Set<String> targets = new TreeSet<>();
        if (json.has("targets")) {
          for (JsonElement value : array(json, "targets")) targets.add(identifier(value, "target"));
        }
        int attempts = positive(json, "attempts_per_set", 32);
        Map<String, Integer> budgets = new TreeMap<>();
        if (json.has("set_attempt_budgets")) {
          JsonElement value = json.get("set_attempt_budgets");
          if (!value.isJsonObject())
            throw new IllegalArgumentException("set_attempt_budgets must be an object");
          JsonObject overrides = value.getAsJsonObject();
          for (String key : overrides.keySet()) {
            ResourceLocation.parse(key);
            budgets.put(key, positive(overrides, key, attempts));
          }
        }
        Map<String, Integer> samplePieceIndices = new TreeMap<>();
        if (json.has("sample_piece_indices")) {
          JsonElement value = json.get("sample_piece_indices");
          if (!value.isJsonObject())
            throw new IllegalArgumentException("sample_piece_indices must be an object");
          JsonObject indices = value.getAsJsonObject();
          for (String key : indices.keySet()) {
            String normalized = ResourceLocation.parse(key).toString();
            int index = number(indices, key).intValueExact();
            if (index < 0)
              throw new IllegalArgumentException("Sample piece index must be nonnegative " + key);
            if (samplePieceIndices.putIfAbsent(normalized, index) != null)
              throw new IllegalArgumentException(
                  "Duplicate normalized sample target " + normalized);
          }
        }
        int radius = integer(json, "radius_regions", 8);
        if (radius < 0) throw new IllegalArgumentException("radius_regions must be nonnegative");
        List<Center> centers = new ArrayList<>();
        if (json.has("centers")) {
          for (JsonElement value : array(json, "centers")) {
            if (!value.isJsonObject())
              throw new IllegalArgumentException("Search center must be an object");
            JsonObject center = value.getAsJsonObject();
            centers.add(
                new Center(
                    identifier(center.get("dimension"), "center dimension"),
                    number(center, "x").intValueExact(),
                    number(center, "z").intValueExact(),
                    center.has("biome") ? identifier(center.get("biome"), "center biome") : null,
                    center.has("set") ? identifier(center.get("set"), "center set") : null));
          }
        }
        boolean require = false;
        if (json.has("require_all_target_positives")) {
          JsonElement value = json.get("require_all_target_positives");
          if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
            throw new IllegalArgumentException("require_all_target_positives must be boolean");
          require = value.getAsBoolean();
        }
        List<BlockWitness> blockWitnesses = new ArrayList<>();
        if (json.has("block_witnesses")) {
          for (JsonElement value : array(json, "block_witnesses")) {
            if (!value.isJsonObject())
              throw new IllegalArgumentException("Block witness must be an object");
            JsonObject witness = value.getAsJsonObject();
            blockWitnesses.add(
                new BlockWitness(
                    identifier(witness.get("structure"), "witness structure"),
                    identifier(witness.get("root_piece_type"), "witness root piece"),
                    new BlockPos(
                        number(witness, "local_x").intValueExact(),
                        number(witness, "local_y").intValueExact(),
                        number(witness, "local_z").intValueExact()),
                    identifier(witness.get("block"), "witness block")));
          }
        }
        return new Plan(
            seed,
            Set.copyOf(targets),
            attempts,
            Map.copyOf(budgets),
            Map.copyOf(samplePieceIndices),
            radius,
            positive(json, "full_origin_budget", 32),
            positive(json, "root_full_budget", 64),
            positive(json, "deadline_seconds", 1200),
            positive(json, "request_timeout_seconds", 120),
            require,
            List.copyOf(centers),
            List.copyOf(blockWitnesses),
            json.deepCopy());
      } catch (IOException | ArithmeticException exception) {
        throw new IllegalArgumentException(
            "Cannot read exact structure coverage plan " + path, exception);
      }
    }
  }

  private static final class Coverage {
    private final String id;
    private final Structure structure;
    private final Set<String> eligibleDimensions = new TreeSet<>();
    private long selectedStarts;
    private JsonObject positive;

    private Coverage(String id, Structure structure) {
      this.id = id;
      this.structure = structure;
    }
  }

  private static final class Group {
    private final String id;
    private final ServerLevel level;
    private final StructureSet structureSet;
    private final StructurePlacement placement;
    private final Set<String> targets;
    private final List<Center> centers;
    private final int budget;
    private final Set<Long> seen = new HashSet<>();
    private final Map<String, Long> selected = new TreeMap<>();
    private final Map<String, Long> returnedStatuses = new TreeMap<>();
    private final CompletableFuture<List<ChunkPos>> rings;
    private long enumerated;
    private long duplicatePositions;
    private long placementRejected;
    private long sharedPositions;
    private long startsRequests;
    private long fullPromotions;
    private int radius;
    private int center;
    private int perimeter;
    private int ringIndex;
    private boolean exhausted;

    private Group(
        String id,
        ServerLevel level,
        StructureSet structureSet,
        Set<String> targets,
        List<Center> centers,
        int budget) {
      this.id = id;
      this.level = level;
      this.structureSet = structureSet;
      this.placement = structureSet.placement();
      this.targets = Set.copyOf(targets);
      this.centers = List.copyOf(centers);
      this.budget = budget;
      if (placement instanceof ConcentricRingsStructurePlacement concentric) {
        rings = level.getChunkSource().getGeneratorState().ringPositions.get(concentric);
        if (rings == null) throw new IllegalStateException("Missing native ring future " + id);
      } else if (placement instanceof RandomSpreadStructurePlacement) rings = null;
      else
        throw new IllegalArgumentException(
            "Unsupported actual placement " + placement.getClass().getName());
    }

    private ChunkPos next(int maximumRadius) {
      if (enumerated >= budget) exhausted = true;
      if (exhausted) return null;
      ChunkPos next;
      if (rings != null) {
        if (!rings.isDone()) return null;
        List<ChunkPos> positions = rings.join();
        if (ringIndex >= positions.size()) {
          exhausted = true;
          return null;
        }
        next = positions.get(ringIndex++);
      } else {
        if (radius > maximumRadius) {
          exhausted = true;
          return null;
        }
        Center origin = centers.get(center);
        int dx = 0;
        int dz = 0;
        if (radius > 0) {
          int width = radius * 2;
          int side = perimeter / width;
          int offset = perimeter % width;
          dx =
              switch (side) {
                case 0 -> -radius + offset;
                case 1 -> radius;
                case 2 -> radius - offset;
                default -> -radius;
              };
          dz =
              switch (side) {
                case 0 -> -radius;
                case 1 -> -radius + offset;
                case 2 -> radius;
                default -> radius - offset;
              };
        }
        RandomSpreadStructurePlacement spread = (RandomSpreadStructurePlacement) placement;
        int regionX = Math.floorDiv(origin.x >> 4, spread.spacing()) + dx;
        int regionZ = Math.floorDiv(origin.z >> 4, spread.spacing()) + dz;
        next =
            spread.getPotentialStructureChunk(
                level.getChunkSource().getGeneratorState().getLevelSeed(),
                regionX * spread.spacing(),
                regionZ * spread.spacing());
        if (++center >= centers.size()) {
          center = 0;
          if (++perimeter >= (radius == 0 ? 1 : radius * 8)) {
            perimeter = 0;
            radius++;
          }
        }
      }
      if (!seen.add(next.toLong())) {
        duplicatePositions++;
        return null;
      }
      enumerated++;
      return next;
    }
  }

  private static final class Request {
    private final ServerLevel level;
    private final ChunkPos position;
    private final ChunkStatus status;
    private final int ticketLevel;
    private final UUID key = UUID.randomUUID();
    private final long started = System.nanoTime();
    private CompletableFuture<ChunkResult<ChunkAccess>> future;
    private boolean released;

    private Request(ServerLevel level, ChunkPos position, ChunkStatus status) {
      this.level = level;
      this.position = position;
      this.status = status;
      ticketLevel = ChunkLevel.byStatus(status);
      level
          .getChunkSource()
          .chunkMap
          .getDistanceManager()
          .addTicket(TICKET, position, ticketLevel, key);
    }

    private ChunkAccess poll(int timeoutSeconds) {
      if ((future == null || !future.isDone())
          && System.nanoTime() - started > timeoutSeconds * 1_000_000_000L)
        throw new IllegalStateException(
            "Timed out waiting for "
                + status
                + " "
                + level.dimension().location()
                + " "
                + position);
      var chunkMap = level.getChunkSource().chunkMap;
      var holder = chunkMap.getVisibleChunkIfPresent(position.toLong());
      if (future == null) {
        if (holder != null && holder.getTicketLevel() <= ticketLevel)
          future = holder.scheduleChunkGenerationTask(status, chunkMap);
        return null;
      }
      if (!future.isDone()) return null;
      ChunkResult<ChunkAccess> result = future.join();
      if (!result.isSuccess())
        throw new IllegalStateException(
            "Native " + status + " request failed: " + result.getError());
      if (holder == null || holder.getTicketLevel() > ticketLevel)
        throw new IllegalStateException("Completed request lost its owned status ticket");
      ChunkAccess chunk = result.orElse(null);
      if (chunk == null || !chunk.getPos().equals(position))
        throw new IllegalStateException("Native request returned the wrong/missing chunk");
      return chunk;
    }

    private void close() {
      if (!released) {
        released = true;
        level
            .getChunkSource()
            .chunkMap
            .getDistanceManager()
            .removeTicket(TICKET, position, ticketLevel, key);
      }
      // The holder's task future is shared with native generation; releasing our ticket is enough.
    }
  }

  private static final class Audit {
    private final MinecraftServer server;
    private final Plan plan;
    private final Path output;
    private final Registry<Structure> registry;
    private final StructureCommitObserver.Session commitTrace;
    private final Map<String, Coverage> coverage = new TreeMap<>();
    private final Set<String> targets = new TreeSet<>();
    private final List<Group> groups = new ArrayList<>();
    private final Set<String> visited = new HashSet<>();
    private final Set<String> requestedFullChunks = new HashSet<>();
    private final JsonArray exceptions = new JsonArray();
    private final Consumer<ServerTickEvent.Post> tickListener = this::tick;
    private final Consumer<ServerStoppingEvent> stopListener = this::stopping;
    private final long started = System.nanoTime();
    private long nextProgress;
    private long maximumStepNanos;
    private int groupIndex;
    private int fullOrigins;
    private int rootFullRequests;
    private Group current;
    private Request request;
    private Request heldOrigin;
    private ChunkAccess originChunk;
    private List<Coverage> witnesses = List.of();
    private int witnessIndex;
    private int phase;
    private boolean done;
    private String termination = "running";

    private Audit(MinecraftServer server, Plan plan, Path output) {
      this.server = server;
      this.plan = plan;
      this.output = output;
      if (server.getWorldData().worldGenOptions().seed() != plan.seed)
        throw new IllegalArgumentException("Coverage plan seed differs from the actual world seed");
      registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
      for (ResourceLocation id : registry.keySet())
        coverage.put(id.toString(), new Coverage(id.toString(), registry.get(id)));
      targets.addAll(plan.targets.isEmpty() ? coverage.keySet() : plan.targets);
      for (String target : targets)
        if (!coverage.containsKey(target))
          throw new IllegalArgumentException("Unknown target structure " + target);
      for (String target : plan.samplePieceIndices.keySet())
        if (!targets.contains(target))
          throw new IllegalArgumentException("Sample piece structure is not targeted " + target);
      for (BlockWitness witness : plan.blockWitnesses) {
        if (!targets.contains(witness.structure))
          throw new IllegalArgumentException(
              "Block witness structure is not targeted " + witness.structure);
        if (!BuiltInRegistries.STRUCTURE_PIECE.containsKey(
            ResourceLocation.parse(witness.rootPieceType)))
          throw new IllegalArgumentException("Unknown witness root piece " + witness.rootPieceType);
        if (!BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(witness.block)))
          throw new IllegalArgumentException("Unknown witness block " + witness.block);
      }
      var sets = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
      for (String set : plan.setBudgets.keySet())
        if (!sets.containsKey(ResourceLocation.parse(set)))
          throw new IllegalArgumentException("Unknown set budget " + set);
      Set<String> dimensions = new HashSet<>();
      for (ServerLevel level : server.getAllLevels()) {
        String dimension = level.dimension().location().toString();
        dimensions.add(dimension);
        var state = level.getChunkSource().getGeneratorState();
        for (ResourceLocation id : registry.keySet()) {
          if (!state.getPlacementsForStructure(registry.getHolder(id).orElseThrow()).isEmpty())
            coverage.get(id.toString()).eligibleDimensions.add(dimension);
        }
        for (Holder<StructureSet> holder : state.possibleStructureSets()) {
          String id = holder.unwrapKey().orElseThrow().location().toString();
          StructureSet set = holder.value();
          Set<String> relevant = new TreeSet<>();
          Set<String> biomes = new HashSet<>();
          for (var member : set.structures()) {
            ResourceLocation key = registry.getKey(member.structure().value());
            if (key == null)
              throw new IllegalStateException("Unregistered native set member " + id);
            Coverage entry = coverage.get(key.toString());
            if (targets.contains(entry.id) && entry.eligibleDimensions.contains(dimension)) {
              relevant.add(entry.id);
              entry.structure.biomes().stream()
                  .map(biome -> biome.unwrapKey().orElseThrow().location().toString())
                  .forEach(biomes::add);
            }
          }
          if (!relevant.isEmpty())
            groups.add(
                new Group(
                    id,
                    level,
                    set,
                    relevant,
                    centers(dimension, id, biomes),
                    plan.setBudgets.getOrDefault(id, plan.attemptsPerSet)));
        }
      }
      for (Center center : plan.centers) {
        if (!dimensions.contains(center.dimension))
          throw new IllegalArgumentException(
              "Unknown/unloaded search dimension " + center.dimension);
        if (center.set != null && !sets.containsKey(ResourceLocation.parse(center.set)))
          throw new IllegalArgumentException("Unknown search-center set " + center.set);
        if (center.biome != null
            && !server
                .registryAccess()
                .registryOrThrow(Registries.BIOME)
                .containsKey(ResourceLocation.parse(center.biome)))
          throw new IllegalArgumentException("Unknown search-center biome " + center.biome);
      }
      groups.sort(
          Comparator.comparing(group -> group.level.dimension().location() + "/" + group.id));
      boolean trace = false;
      if (plan.original.has("trace_structure_commits")) {
        JsonElement value = plan.original.get("trace_structure_commits");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
          throw new IllegalArgumentException("trace_structure_commits must be boolean");
        trace = value.getAsBoolean();
      }
      Set<Structure> traced = new HashSet<>();
      targets.forEach(id -> traced.add(coverage.get(id).structure));
      commitTrace = trace ? StructureCommitObserver.arm(server, traced) : null;
    }

    private List<Center> centers(String dimension, String set, Set<String> biomes) {
      LinkedHashSet<Center> matched = new LinkedHashSet<>();
      for (Center center : plan.centers)
        if (center.dimension.equals(dimension)
            && (center.set == null || center.set.equals(set))
            && (center.biome == null || biomes.contains(center.biome))) matched.add(center);
      if (matched.isEmpty()) matched.add(new Center(dimension, 0, 0, null, set));
      return List.copyOf(matched);
    }

    private void tick(ServerTickEvent.Post event) {
      if (done || event.getServer() != server) return;
      try {
        if (System.nanoTime() - started >= plan.deadlineSeconds * 1_000_000_000L) {
          finish("deadline_exhausted", null);
          return;
        }
        long until = System.nanoTime() + 25_000_000L;
        while (!done && System.nanoTime() < until) {
          long stepStarted = System.nanoTime();
          boolean advanced = advance();
          maximumStepNanos = Math.max(maximumStepNanos, System.nanoTime() - stepStarted);
          if (!advanced) break;
        }
        if (!done && System.nanoTime() >= nextProgress) {
          nextProgress = System.nanoTime() + 10_000_000_000L;
          write("running");
          LOGGER.info(
              "Structure coverage progress: positives={} eligibleMissing={} FULL-origins={} root-FULL={} phase={}",
              positiveCount(),
              missingCount(),
              fullOrigins,
              rootFullRequests,
              phase);
        }
      } catch (RuntimeException exception) {
        try {
          if (!done) finish("exception", exception);
        } catch (RuntimeException finishFailure) {
          exception.addSuppressed(finishFailure);
        }
        throw exception;
      }
    }

    private boolean advance() {
      if (request != null) {
        ChunkAccess chunk = request.poll(plan.requestTimeoutSeconds);
        if (chunk == null) return false;
        if (phase == 0) inspectStarts(chunk);
        else if (phase == 1) inspectOrigin(chunk);
        else inspectRoot(chunk);
        return true;
      }
      if (heldOrigin != null) return nextWitness();
      if (missingCount() == 0) {
        finish("all_eligible_targets_positive", null);
        return false;
      }
      if (fullOrigins >= plan.fullOriginBudget) {
        finish("full_origin_budget_exhausted", null);
        return false;
      }
      boolean available = false;
      for (int checked = 0; checked < groups.size(); checked++) {
        Group group = groups.get(groupIndex);
        groupIndex = (groupIndex + 1) % groups.size();
        if (group.exhausted
            || group.targets.stream().allMatch(id -> coverage.get(id).positive != null)) continue;
        available = true;
        if (group.rings != null && !group.rings.isDone()) continue;
        ChunkPos next = group.next(plan.regionRadius);
        if (next == null) return true;
        if (!group.placement.isStructureChunk(
            group.level.getChunkSource().getGeneratorState(), next.x, next.z)) {
          group.placementRejected++;
          return true;
        }
        String key = group.level.dimension().location() + ":" + next.toLong();
        if (!visited.add(key)) {
          group.sharedPositions++;
          return true;
        }
        current = group;
        group.startsRequests++;
        phase = 0;
        request = new Request(group.level, next, ChunkStatus.STRUCTURE_STARTS);
        return true;
      }
      if (!available) finish("native_candidate_budgets_exhausted", null);
      return false;
    }

    private void inspectStarts(ChunkAccess chunk) {
      current.returnedStatuses.merge(chunk.getPersistedStatus().getName(), 1L, Long::sum);
      List<Coverage> selected = new ArrayList<>();
      for (var entry : chunk.getAllStarts().entrySet()) {
        StructureStart start = entry.getValue();
        if (start == null || !start.isValid() || start.getPieces().isEmpty()) continue;
        ResourceLocation id = registry.getKey(entry.getKey());
        if (id == null)
          throw new IllegalStateException("Selected start has an unregistered structure");
        if (!start.getChunkPos().equals(request.position))
          throw new IllegalStateException("Selected native start has a different origin chunk");
        Coverage row = coverage.get(id.toString());
        row.selectedStarts++;
        current.selected.merge(row.id, 1L, Long::sum);
        if (targets.contains(row.id) && row.positive == null) selected.add(row);
      }
      selected.sort(Comparator.comparing(row -> row.id));
      if (selected.isEmpty()) {
        request.close();
        request = null;
        return;
      }
      witnesses = List.copyOf(selected);
      witnessIndex = 0;
      Request starts = request;
      request = new Request(starts.level, starts.position, ChunkStatus.FULL);
      starts.close();
      phase = 1;
      fullOrigins++;
      current.fullPromotions++;
      requestedFullChunks.add(
          current.level.dimension().location() + ":" + request.position.toLong());
    }

    private void inspectOrigin(ChunkAccess chunk) {
      requireFull(chunk);
      originChunk = chunk;
      heldOrigin = request;
      request = null;
    }

    private boolean nextWitness() {
      if (witnessIndex >= witnesses.size()) {
        heldOrigin.close();
        heldOrigin = null;
        originChunk = null;
        witnesses = List.of();
        return true;
      }
      Coverage entry = witnesses.get(witnessIndex);
      StructureStart start = originChunk.getStartForStructure(entry.structure);
      requireStart(start, heldOrigin.position, entry.id);
      StructurePiece root = start.getPieces().getFirst();
      int sampleIndex = samplePieceIndex(entry, start);
      if (sampleIndex != 0
          && !new ChunkPos(root.getBoundingBox().getCenter()).equals(heldOrigin.position))
        throw new IllegalStateException(
            "Nonzero piece sampling requires the first root in its FULL origin chunk " + entry.id);
      ChunkPos sampleChunk =
          new ChunkPos(start.getPieces().get(sampleIndex).getBoundingBox().getCenter());
      if (sampleChunk.equals(heldOrigin.position)) {
        record(entry, start, root, originChunk);
        witnessIndex++;
        return true;
      }
      if (rootFullRequests >= plan.rootFullBudget) {
        finish("root_full_budget_exhausted", null);
        return false;
      }
      rootFullRequests++;
      phase = 2;
      request = new Request(heldOrigin.level, sampleChunk, ChunkStatus.FULL);
      requestedFullChunks.add(heldOrigin.level.dimension().location() + ":" + sampleChunk.toLong());
      return true;
    }

    private void inspectRoot(ChunkAccess chunk) {
      requireFull(chunk);
      Coverage entry = witnesses.get(witnessIndex);
      StructureStart start = originChunk.getStartForStructure(entry.structure);
      requireStart(start, heldOrigin.position, entry.id);
      record(entry, start, start.getPieces().getFirst(), chunk);
      request.close();
      request = null;
      witnessIndex++;
    }

    private void record(
        Coverage entry, StructureStart start, StructurePiece root, ChunkAccess sampledChunk) {
      if (entry.positive != null) return;
      int sampleIndex = samplePieceIndex(entry, start);
      ChunkAccess rootChunk = sampleIndex == 0 ? sampledChunk : originChunk;
      requireFull(rootChunk);
      if (!new ChunkPos(root.getBoundingBox().getCenter()).equals(rootChunk.getPos()))
        throw new IllegalStateException(
            "First-root proof chunk differs from its center " + entry.id);
      if (!rootChunk
          .getReferencesForStructure(entry.structure)
          .contains(heldOrigin.position.toLong()))
        throw new IllegalStateException(
            "FULL root-piece chunk lacks its natural origin reference for " + entry.id);
      if (root.getBoundingBox().minY() >= heldOrigin.level.getMaxBuildHeight()
          || root.getBoundingBox().maxY() < heldOrigin.level.getMinBuildHeight())
        throw new IllegalStateException(
            "Root piece is entirely outside build height for " + entry.id);
      JsonObject result = new JsonObject();
      result.addProperty("dimension", heldOrigin.level.dimension().location().toString());
      result.addProperty("seed", heldOrigin.level.getSeed());
      result.addProperty(
          "structure_generation_seed",
          heldOrigin.level.getChunkSource().getGeneratorState().getLevelSeed());
      result.add("origin_chunk", chunk(heldOrigin.position));
      result.addProperty("candidate_probe_set", current.id);
      JsonArray nativeMemberships = new JsonArray();
      for (Group group : groups)
        if (group.level == heldOrigin.level
            && group.targets.contains(entry.id)
            && group.placement.isStructureChunk(
                group.level.getChunkSource().getGeneratorState(),
                heldOrigin.position.x,
                heldOrigin.position.z)) nativeMemberships.add(group.id);
      if (nativeMemberships.isEmpty())
        throw new IllegalStateException(
            "Selected FULL start has no native origin placement membership " + entry.id);
      result.add("native_origin_placement_memberships", nativeMemberships);
      result.addProperty(
          "selection",
          "Actual native weighted set generation; no forced structure or biome predicate");
      result.addProperty("piece_count", start.getPieces().size());
      result.add("bounds", bounds(start.getBoundingBox()));
      result.add("root_piece_chunk", chunk(rootChunk.getPos()));
      result.add("root_piece_bounds", bounds(root.getBoundingBox()));
      result.addProperty("root_piece_class", root.getClass().getName());
      result.addProperty("root_has_native_origin_reference", true);
      result.addProperty("root_bounds_intersect_build_height", true);
      String proofLevel = "native_start_FULL_root_reference";
      if (sampleIndex != 0) {
        requireFull(sampledChunk);
        StructurePiece sampled = start.getPieces().get(sampleIndex);
        if (!new ChunkPos(sampled.getBoundingBox().getCenter()).equals(sampledChunk.getPos())
            || !sampledChunk
                .getReferencesForStructure(entry.structure)
                .contains(heldOrigin.position.toLong()))
          throw new IllegalStateException(
              "FULL sampled-piece chunk lacks its center/native origin reference " + entry.id);
        if (sampled.getBoundingBox().minY() >= heldOrigin.level.getMaxBuildHeight()
            || sampled.getBoundingBox().maxY() < heldOrigin.level.getMinBuildHeight())
          throw new IllegalStateException(
              "Sampled piece is entirely outside build height for " + entry.id);
        result.addProperty("sampled_piece_index", sampleIndex);
        result.add("sampled_piece_chunk", chunk(sampledChunk.getPos()));
        result.add("sampled_piece_bounds", bounds(sampled.getBoundingBox()));
        result.addProperty("sampled_piece_class", sampled.getClass().getName());
        result.addProperty("sampled_piece_has_native_origin_reference", true);
        result.addProperty("sampled_piece_bounds_intersect_build_height", true);
        result.addProperty("sampled_piece_status", sampledChunk.getPersistedStatus().getName());
        result.addProperty(
            "source_structure_commit_scope",
            "Start-level writes in the sampled FULL chunk; not attribution to an individual piece");
        proofLevel = "native_start_FULL_root_and_sampled_piece_references";
      }
      result.addProperty("proof_level", proofLevel);
      if (commitTrace != null) {
        JsonArray commits =
            commitTrace.evidence(
                heldOrigin.level, entry.structure, heldOrigin.position, sampledChunk);
        result.add("source_structure_commits_at_FULL_observation", commits);
        if (!commits.isEmpty())
          result.addProperty(
              "proof_level",
              proofLevel
                  + (sampleIndex == 0
                      ? "_and_block_commits"
                      : "_and_start_block_commits_in_sampled_chunk"));
      }
      var rootType = BuiltInRegistries.STRUCTURE_PIECE.getKey(root.getType());
      if (rootType != null) result.addProperty("root_piece_type", rootType.toString());
      JsonArray blockWitnesses = new JsonArray();
      for (BlockWitness witness : plan.blockWitnesses) {
        if (!witness.structure.equals(entry.id)) continue;
        if (rootType == null || !rootType.toString().equals(witness.rootPieceType))
          throw new IllegalStateException("Unexpected first piece for block witness " + entry.id);
        BlockPos position =
            root.getWorldPos(witness.local.getX(), witness.local.getY(), witness.local.getZ());
        if (!root.getBoundingBox().isInside(position)
            || !new ChunkPos(position).equals(rootChunk.getPos())
            || position.getY() < heldOrigin.level.getMinBuildHeight()
            || position.getY() >= heldOrigin.level.getMaxBuildHeight())
          throw new IllegalStateException(
              "Block witness is outside its FULL root chunk/build height " + position);
        var actual = rootChunk.getBlockState(position);
        if (!actual.is(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(witness.block))))
          throw new IllegalStateException(
              "Natural block witness differs for "
                  + entry.id
                  + " at "
                  + position
                  + ": expected "
                  + witness.block
                  + ", got "
                  + actual);
        JsonObject proof = new JsonObject();
        proof.add("position", block(position));
        proof.addProperty("block", witness.block);
        proof.addProperty("actual_state", actual.toString());
        blockWitnesses.add(proof);
      }
      result.add("checked_block_witnesses", blockWitnesses);
      result.addProperty("origin_status", originChunk.getPersistedStatus().getName());
      result.addProperty("root_status", rootChunk.getPersistedStatus().getName());
      result.addProperty(
          "proven",
          "Natural valid start at its actual origin FULL chunk; first-piece center chunk also FULL with a native reference to that origin and root bounds intersecting build height. "
              + (sampleIndex == 0
                  ? ""
                  : "The sampled-piece center chunk is additionally FULL with its native origin reference and bounds intersecting build height; block commits are sampled from that chunk. ")
              + "This does not prove every room/block of the full assembly or client appearance.");
      entry.positive = result;
      LOGGER.info(
          "Structure natural FULL positive {} dimension={} origin={} pieces={} root={}",
          entry.id,
          heldOrigin.level.dimension().location(),
          heldOrigin.position,
          start.getPieces().size(),
          rootChunk.getPos());
      write("running");
    }

    private int samplePieceIndex(Coverage entry, StructureStart start) {
      int index = plan.samplePieceIndices.getOrDefault(entry.id, 0);
      if (index >= start.getPieces().size())
        throw new IllegalArgumentException(
            "Sample piece index exceeds the native piece count for " + entry.id + ": " + index);
      return index;
    }

    private long positiveCount() {
      return targets.stream().filter(id -> coverage.get(id).positive != null).count();
    }

    private long missingCount() {
      return targets.stream()
          .map(coverage::get)
          .filter(row -> !row.eligibleDimensions.isEmpty() && row.positive == null)
          .count();
    }

    private void stopping(ServerStoppingEvent event) {
      if (!done && event.getServer() == server)
        finish(
            "server_stopping",
            new IllegalStateException("Server stopped before finite coverage collection finished"));
    }

    private void finish(String reason, RuntimeException failure) {
      if (done) return;
      termination = reason;
      if (failure != null) {
        JsonObject error = new JsonObject();
        error.addProperty("class", failure.getClass().getName());
        error.addProperty("message", failure.getMessage());
        error.addProperty("phase", phase);
        if (current != null) {
          error.addProperty("set", current.id);
          error.addProperty("dimension", current.level.dimension().location().toString());
        }
        if (request != null) error.add("chunk", chunk(request.position));
        exceptions.add(error);
      }
      done = true;
      if (request != null) request.close();
      if (heldOrigin != null) heldOrigin.close();
      request = null;
      heldOrigin = null;
      NeoForge.EVENT_BUS.unregister(tickListener);
      NeoForge.EVENT_BUS.unregister(stopListener);
      if (commitTrace != null) {
        commitTrace.close();
        for (Coverage entry : coverage.values()) {
          if (entry.positive == null) continue;
          JsonArray original =
              entry.positive.getAsJsonArray("source_structure_commits_at_FULL_observation");
          JsonArray retained = new JsonArray();
          for (JsonElement proof : original) {
            if (commitTrace.retained(proof.getAsJsonObject())) retained.add(proof);
          }
          entry.positive.add("source_structure_commits_at_FULL_observation", retained);
          if (retained.size() != original.size())
            entry.positive.addProperty("later_observed_writes_invalidated_block_samples", true);
          if (retained.isEmpty())
            entry.positive.addProperty(
                "proof_level",
                entry.positive.has("sampled_piece_index")
                    ? "native_start_FULL_root_and_sampled_piece_references"
                    : "native_start_FULL_root_reference");
        }
      }
      boolean strictMissing = plan.requireAllTargets && positiveCount() < targets.size();
      write(
          failure != null
              ? "error"
              : strictMissing ? "required_targets_finite_missing" : "complete");
      if (failure != null) return;
      if (strictMissing)
        throw new IllegalStateException(
            "Required target positives remain finite-missing; see " + output);
      LOGGER.info(
          "Structure coverage collection completed: positives={} finiteMissing={} FULL-origins={} root-FULL={} termination={} output={}",
          positiveCount(),
          missingCount(),
          fullOrigins,
          rootFullRequests,
          reason,
          output);
    }

    private void write(String status) {
      JsonObject report = new JsonObject();
      report.addProperty("format", 1);
      report.addProperty("status", status);
      report.addProperty("termination", termination);
      report.addProperty("seed", plan.seed);
      report.addProperty(
          "generate_structures_enabled",
          server.getWorldData().worldGenOptions().generateStructures());
      report.add("plan", plan.original.deepCopy());
      report.addProperty(
          "scope",
          "Finite native structure-set candidates. Only actually selected new target IDs promote their origin and root-piece chunk to FULL. Missing/excluded/not-targeted are explicit; one seed never proves global absence. No /place, config/weights/biome overrides or shared future cancellation.");
      report.addProperty("candidate_ticket_status", "STRUCTURE_STARTS");
      report.addProperty(
          "candidate_ticket_level", ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS));
      report.addProperty("full_ticket_level", ChunkLevel.byStatus(ChunkStatus.FULL));
      report.addProperty("full_dependency_radius", ChunkLevel.RADIUS_AROUND_FULL_CHUNK);
      report.addProperty(
          "planned_native_candidate_request_upper_bound",
          groups.stream().mapToLong(group -> group.budget).sum());
      long diameter = 2L * ChunkLevel.RADIUS_AROUND_FULL_CHUNK + 1;
      report.addProperty(
          "disjoint_FULL_dependency_footprint_chunk_upper_bound",
          (plan.fullOriginBudget + (long) plan.rootFullBudget) * diameter * diameter);
      report.addProperty("elapsed_ms", (System.nanoTime() - started) / 1_000_000L);
      report.addProperty("cooperative_tick_slice_ms", 25);
      report.addProperty("maximum_single_step_ms", maximumStepNanos / 1_000_000.0);
      report.addProperty("full_origin_requests", fullOrigins);
      report.addProperty("root_full_requests", rootFullRequests);
      if (!plan.samplePieceIndices.isEmpty())
        report.addProperty(
            "root_full_request_scope",
            "The shared root_full_budget also covers opt-in sampled-piece center FULL requests");
      report.addProperty("distinct_requested_FULL_chunks", requestedFullChunks.size());
      report.addProperty("native_positive_targets", positiveCount());
      if (commitTrace != null) {
        report.add("source_structure_commit_observer", commitTrace.report());
        report.addProperty(
            "targets_with_source_structure_commits_at_FULL_observation",
            coverage.values().stream()
                .filter(entry -> entry.positive != null)
                .filter(
                    entry ->
                        entry.positive.has("source_structure_commits_at_FULL_observation")
                            && !entry
                                .positive
                                .getAsJsonArray("source_structure_commits_at_FULL_observation")
                                .isEmpty())
                .count());
      }
      report.addProperty(
          "targets_with_observed_expected_block_witness",
          coverage.values().stream()
              .filter(entry -> entry.positive != null)
              .filter(entry -> !entry.positive.getAsJsonArray("checked_block_witnesses").isEmpty())
              .count());
      report.addProperty("eligible_without_positive_targets", missingCount());
      JsonArray setRows = new JsonArray();
      for (Group group : groups) {
        JsonObject row = new JsonObject();
        row.addProperty("id", group.id);
        row.addProperty("dimension", group.level.dimension().location().toString());
        row.addProperty("placement_class", group.placement.getClass().getName());
        row.addProperty("candidate_budget", group.budget);
        JsonArray targetIds = new JsonArray();
        group.targets.stream().sorted().forEach(targetIds::add);
        row.add("target_ids", targetIds);
        JsonArray weightedMembers = new JsonArray();
        for (var member : group.structureSet.structures()) {
          JsonObject value = new JsonObject();
          value.addProperty("id", registry.getKey(member.structure().value()).toString());
          value.addProperty("weight", member.weight());
          weightedMembers.add(value);
        }
        row.add("actual_weighted_members", weightedMembers);
        row.addProperty("enumerated_unique_positions", group.enumerated);
        row.addProperty("duplicate_positions", group.duplicatePositions);
        row.addProperty("native_placement_rejected", group.placementRejected);
        row.addProperty("already_examined_global_positions", group.sharedPositions);
        row.addProperty("structure_starts_requests", group.startsRequests);
        row.addProperty("full_origin_promotions", group.fullPromotions);
        row.addProperty("exhausted", group.exhausted);
        row.addProperty(
            "candidate_order",
            group.rings == null
                ? "Square region perimeters; each perimeter coordinate visits all relevant centers before advancing; native potential chunk and isStructureChunk"
                : "Native ordered ring positions, without changing ring futures or preferred-biome search");
        JsonArray centers = new JsonArray();
        for (Center center : group.centers) {
          JsonObject point = new JsonObject();
          point.addProperty("x", center.x);
          point.addProperty("z", center.z);
          if (center.biome != null) point.addProperty("biome_annotation", center.biome);
          centers.add(point);
        }
        row.add("centers_xz", centers);
        row.addProperty("center_coordinate_units", "blocks");
        JsonObject selected = new JsonObject();
        group.selected.forEach(selected::addProperty);
        row.add("native_start_counts_in_candidate_chunks", selected);
        row.addProperty(
            "start_count_scope",
            "All native starts in chunks requested for this probe set; not attributed weighted outcomes of this set");
        JsonObject returnedStatuses = new JsonObject();
        group.returnedStatuses.forEach(returnedStatuses::addProperty);
        row.add("candidate_returned_status_counts", returnedStatuses);
        setRows.add(row);
      }
      report.add("sets", setRows);
      JsonArray rows = new JsonArray();
      for (Coverage entry : coverage.values()) {
        JsonObject row = new JsonObject();
        row.addProperty("id", entry.id);
        row.addProperty("targeted", targets.contains(entry.id));
        JsonArray dimensions = new JsonArray();
        entry.eligibleDimensions.forEach(dimensions::add);
        row.add("eligible_dimensions", dimensions);
        row.addProperty("actual_selected_start_count", entry.selectedStarts);
        row.addProperty(
            "status",
            entry.positive != null
                ? "native_positive"
                : entry.eligibleDimensions.isEmpty()
                    ? "excluded_no_native_placement_in_loaded_dimensions"
                    : !targets.contains(entry.id)
                        ? "not_targeted"
                        : status.equals("running")
                            ? "pending_native_collection"
                            : exceptions.isEmpty()
                                ? "finite_missing"
                                : "not_completed_after_exception");
        if (entry.positive != null) row.add("natural_FULL", entry.positive);
        rows.add(row);
      }
      report.add("structures", rows);
      report.add("exceptions", exceptions);
      try {
        Files.createDirectories(output.getParent());
        Files.writeString(
            output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
      } catch (IOException exception) {
        throw new IllegalStateException("Cannot write structure coverage " + output, exception);
      }
    }
  }

  private static void requireFull(ChunkAccess chunk) {
    if (chunk.getPersistedStatus() != ChunkStatus.FULL)
      throw new IllegalStateException("Natural structure evidence chunk is not FULL");
  }

  private static void requireStart(StructureStart start, ChunkPos origin, String id) {
    if (start == null
        || !start.isValid()
        || start.getPieces().isEmpty()
        || !start.getChunkPos().equals(origin))
      throw new IllegalStateException(
          "Lost actual selected FULL start/origin for " + id + " " + origin);
  }

  private static JsonArray chunk(ChunkPos position) {
    JsonArray result = new JsonArray();
    result.add(position.x);
    result.add(position.z);
    return result;
  }

  private static JsonArray block(BlockPos position) {
    JsonArray result = new JsonArray();
    result.add(position.getX());
    result.add(position.getY());
    result.add(position.getZ());
    return result;
  }

  private static JsonArray bounds(BoundingBox box) {
    JsonArray result = new JsonArray();
    for (int value :
        new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()})
      result.add(value);
    return result;
  }

  private static JsonArray array(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonArray())
      throw new IllegalArgumentException(key + " must be an array");
    return value.getAsJsonArray();
  }

  private static java.math.BigDecimal number(JsonObject object, String key) {
    JsonElement value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException(key + " must be a JSON number");
    return value.getAsBigDecimal();
  }

  private static int integer(JsonObject object, String key, int fallback) {
    return object.has(key) ? number(object, key).intValueExact() : fallback;
  }

  private static int positive(JsonObject object, String key, int fallback) {
    int value = integer(object, key, fallback);
    if (value <= 0) throw new IllegalArgumentException(key + " must be positive");
    return value;
  }

  private static String identifier(JsonElement value, String label) {
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException(label + " must be a resource identifier string");
    return ResourceLocation.parse(value.getAsString()).toString();
  }
}
