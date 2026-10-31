package dev.aeroscapes.regression;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Opt-in native structure-write observations, revalidated against an already FULL chunk. */
public final class StructureCommitObserver {
  private static final int POSITIONS_PER_START_CHUNK = 16;
  private static final int CHUNK_LIMIT = 4096;
  private static final int START_CHUNK_LIMIT = 4096;
  private static final int TREASURE_READ_LIMIT = 64;
  private static final int TREASURE_SUPPORT_STATE_LIMIT = 64;
  private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
  private static volatile Session active;

  private StructureCommitObserver() {}

  public record Frame(
      Session session,
      WorldGenLevel region,
      String dimension,
      String structure,
      ChunkPos origin,
      ChunkPos placementChunk) {}

  private record ChunkKey(String dimension, ChunkPos chunk) {}

  private record StartKey(String structure, ChunkPos origin) {}

  private record Write(
      BlockPos position, BlockState before, BlockState after, ChunkPos placementChunk) {}

  private record TreasureRead(
      String dimension,
      ChunkPos origin,
      ChunkPos placementChunk,
      BlockPos below,
      BlockState current,
      BlockState support) {}

  public static synchronized Session arm(MinecraftServer server, Set<Structure> targets) {
    if (active != null) throw new IllegalStateException("Structure commit observer already armed");
    if (!server.isDedicatedServer() || !server.isSameThread())
      throw new IllegalArgumentException("Structure observer needs the dedicated server thread");
    if (targets.isEmpty()) throw new IllegalArgumentException("Structure observer needs targets");
    Session session = new Session(server, targets);
    active = session;
    return session;
  }

  public static synchronized void disarm(Session session) {
    if (active == session) active = null;
  }

  public static boolean armed() {
    return active != null;
  }

  public static Frame enter(StructureStart start, WorldGenLevel region, ChunkPos placementChunk) {
    Frame previous = CURRENT.get();
    CURRENT.remove();
    Session session = active;
    ServerLevel level = region.getLevel();
    if (session != null
        && level.getServer() == session.server
        && start.isValid()
        && session.targets.contains(start.getStructure())) {
      var key =
          level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
      if (key == null) throw new IllegalStateException("Unregistered observed structure");
      session.called();
      CURRENT.set(
          new Frame(
              session,
              region,
              level.dimension().location().toString(),
              key.toString(),
              start.getChunkPos(),
              placementChunk));
    }
    return previous;
  }

  public static void restore(Frame previous) {
    if (previous == null) CURRENT.remove();
    else CURRENT.set(previous);
  }

  public static void committed(
      ServerLevel level,
      WorldGenLevel region,
      BlockPos position,
      BlockState before,
      BlockState after) {
    Session session = active;
    if (session == null
        || level.getServer() != session.server
        || region.getLevel() != level
        || before == null
        || before.equals(after)
        || level.isOutsideBuildHeight(position)) return;
    session.record(CURRENT.get(), region, position, before, after);
  }

  public static void treasureSupportRead(
      WorldGenLevel region, BlockPos below, BlockState current, BlockState support) {
    Session session = active;
    if (session == null) return;
    Frame frame = CURRENT.get();
    if (frame == null
        || frame.session != session
        || frame.region != region
        || !(region instanceof WorldGenRegion)
        || !frame.structure.equals("minecraft:buried_treasure")) return;
    session.treasureRead(frame, below, current, support);
  }

  public static final class Session implements AutoCloseable {
    private final MinecraftServer server;
    private final Set<Structure> targets;
    private final Map<ChunkKey, Map<StartKey, Map<Long, Write>>> chunks = new HashMap<>();
    private final List<TreasureRead> treasureReads = new ArrayList<>();
    private final Map<String, Long> treasureSupportStates = new TreeMap<>();
    private int startChunks;
    private long calls;
    private long changes;
    private long qualifyingChanges;
    private long invalidations;
    private long sampleLimitDrops;
    private long treasureSupportReads;
    private long treasureSupportStateDrops;
    private boolean overflow;

    private Session(MinecraftServer server, Set<Structure> targets) {
      this.server = server;
      this.targets = Set.copyOf(targets);
    }

    private synchronized void called() {
      calls++;
    }

    private synchronized void treasureRead(
        Frame frame, BlockPos below, BlockState current, BlockState support) {
      if (active != this) return;
      treasureSupportReads++;
      String state = support.toString();
      if (treasureSupportStates.containsKey(state))
        treasureSupportStates.merge(state, 1L, Long::sum);
      else if (treasureSupportStates.size() < TREASURE_SUPPORT_STATE_LIMIT)
        treasureSupportStates.put(state, 1L);
      else treasureSupportStateDrops++;
      if (treasureReads.size() < TREASURE_READ_LIMIT)
        treasureReads.add(
            new TreasureRead(
                frame.dimension,
                frame.origin,
                frame.placementChunk,
                below.immutable(),
                current,
                support));
    }

    private synchronized void record(
        Frame frame, WorldGenLevel region, BlockPos position, BlockState before, BlockState after) {
      if (active != this) return;
      changes++;
      if (overflow) return;
      ChunkKey chunk =
          new ChunkKey(region.getLevel().dimension().location().toString(), new ChunkPos(position));
      Map<StartKey, Map<Long, Write>> starts = chunks.get(chunk);
      if (starts != null) {
        for (Map<Long, Write> writes : starts.values())
          if (writes.remove(position.asLong()) != null) invalidations++;
      }
      if (frame == null || frame.session != this || frame.region != region || after.isAir()) return;
      qualifyingChanges++;
      StartKey start = new StartKey(frame.structure, frame.origin);
      if (starts == null) {
        if (chunks.size() >= CHUNK_LIMIT) {
          capacityExceeded();
          return;
        }
        starts = new HashMap<>();
        chunks.put(chunk, starts);
      }
      Map<Long, Write> writes = starts.get(start);
      if (writes == null) {
        if (startChunks >= START_CHUNK_LIMIT) {
          capacityExceeded();
          return;
        }
        writes = new LinkedHashMap<>();
        starts.put(start, writes);
        startChunks++;
      }
      if (writes.size() >= POSITIONS_PER_START_CHUNK) {
        sampleLimitDrops++;
        return;
      }
      writes.put(
          position.asLong(), new Write(position.immutable(), before, after, frame.placementChunk));
    }

    private void capacityExceeded() {
      overflow = true;
      chunks.clear();
    }

    public synchronized JsonArray evidence(
        ServerLevel level, Structure structure, ChunkPos origin, ChunkAccess fullRoot) {
      JsonArray result = new JsonArray();
      if (overflow || level.getServer() != server || !targets.contains(structure)) return result;
      if (fullRoot.getPersistedStatus() != ChunkStatus.FULL)
        throw new IllegalArgumentException("Structure evidence requires an already FULL chunk");
      var key = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);
      if (key == null) throw new IllegalArgumentException("Unregistered evidence structure");
      String dimension = level.dimension().location().toString();
      Map<StartKey, Map<Long, Write>> starts =
          chunks.get(new ChunkKey(dimension, fullRoot.getPos()));
      if (starts == null) return result;
      Map<Long, Write> writes = starts.get(new StartKey(key.toString(), origin));
      if (writes == null) return result;
      writes.values().stream()
          .sorted(Comparator.comparingLong(write -> write.position.asLong()))
          .forEach(
              write -> {
                BlockState actual = fullRoot.getBlockState(write.position);
                if (!actual.equals(write.after)) return;
                JsonObject proof = new JsonObject();
                proof.addProperty("proof_kind", "source_structure_commit");
                proof.addProperty("successful_block_state_change", true);
                proof.addProperty("FULL_state_matches_commit", true);
                proof.addProperty("dimension", dimension);
                proof.addProperty("structure", key.toString());
                proof.addProperty("origin_chunk_x", origin.x);
                proof.addProperty("origin_chunk_z", origin.z);
                proof.addProperty("write_chunk_x", fullRoot.getPos().x);
                proof.addProperty("write_chunk_z", fullRoot.getPos().z);
                proof.addProperty("placement_chunk_x", write.placementChunk.x);
                proof.addProperty("placement_chunk_z", write.placementChunk.z);
                proof.addProperty("x", write.position.getX());
                proof.addProperty("y", write.position.getY());
                proof.addProperty("z", write.position.getZ());
                proof.addProperty("old_state", write.before.toString());
                proof.addProperty("new_state", write.after.toString());
                proof.addProperty("FULL_state", actual.toString());
                result.add(proof);
              });
      return result;
    }

    public synchronized JsonObject report() {
      JsonObject result = new JsonObject();
      result.addProperty("version", 1);
      result.addProperty("enabled", true);
      result.addProperty("armed", active == this);
      result.addProperty("counter_period", "since_observer_arm");
      result.addProperty("target_structure_place_calls", calls);
      result.addProperty("observed_block_state_changes", changes);
      result.addProperty("target_nonair_block_state_changes", qualifyingChanges);
      result.addProperty("later_changed_write_invalidations", invalidations);
      result.addProperty("sample_limit_dropped_positions", sampleLimitDrops);
      result.addProperty("retained_write_chunks", chunks.size());
      result.addProperty("observed_start_chunk_buckets", startChunks);
      result.addProperty("positions_per_start_chunk_limit", POSITIONS_PER_START_CHUNK);
      result.addProperty("write_chunk_limit", CHUNK_LIMIT);
      result.addProperty("start_chunk_bucket_limit", START_CHUNK_LIMIT);
      result.addProperty("overflow_disables_all_trace_evidence", overflow);
      JsonObject support = new JsonObject();
      support.addProperty("observed_read_count", treasureSupportReads);
      support.addProperty(
          "buried_treasure_is_targeted",
          targets.stream()
              .anyMatch(
                  structure ->
                      ResourceLocation.withDefaultNamespace("buried_treasure")
                          .equals(
                              server
                                  .registryAccess()
                                  .registryOrThrow(Registries.STRUCTURE)
                                  .getKey(structure))));
      support.addProperty("retained_pair_limit", TREASURE_READ_LIMIT);
      support.addProperty("support_state_kind_limit", TREASURE_SUPPORT_STATE_LIMIT);
      support.addProperty("unretained_support_state_reads", treasureSupportStateDrops);
      JsonObject states = new JsonObject();
      treasureSupportStates.forEach(states::addProperty);
      support.add("actual_below_state_counts", states);
      var supportTag =
          server
              .registryAccess()
              .registryOrThrow(Registries.BLOCK)
              .getTag(
                  TagKey.create(
                      Registries.BLOCK,
                      ResourceLocation.fromNamespaceAndPath(
                          "aeroscapes_reimagine", "buried_treasure_support")));
      support.addProperty("fallback_support_tag_present", supportTag.isPresent());
      JsonArray supportBlocks = new JsonArray();
      supportTag.ifPresent(
          holders ->
              holders.forEach(
                  holder ->
                      supportBlocks.add(holder.unwrapKey().orElseThrow().location().toString())));
      support.add("resolved_fallback_support_tag_blocks", supportBlocks);
      JsonArray pairs = new JsonArray();
      for (TreasureRead read : treasureReads) {
        JsonObject pair = new JsonObject();
        pair.addProperty("dimension", read.dimension);
        pair.addProperty("structure", "minecraft:buried_treasure");
        pair.addProperty("origin_chunk_x", read.origin.x);
        pair.addProperty("origin_chunk_z", read.origin.z);
        pair.addProperty("placement_chunk_x", read.placementChunk.x);
        pair.addProperty("placement_chunk_z", read.placementChunk.z);
        pair.addProperty("below_x", read.below.getX());
        pair.addProperty("below_y", read.below.getY());
        pair.addProperty("below_z", read.below.getZ());
        pair.addProperty("current_x", read.below.getX());
        pair.addProperty("current_y", read.below.getY() + 1);
        pair.addProperty("current_z", read.below.getZ());
        pair.addProperty("current_state", read.current.toString());
        pair.addProperty("actual_below_state", read.support.toString());
        pairs.add(pair);
      }
      support.add("first_actual_read_pairs", pairs);
      support.addProperty(
          "scope",
          "Actual native buried-treasure scan-loop iterations during armed target placement frames."
              + " Counts are iterations, not treasures; pairs retain the first 64 iterations across frames."
              + " Each intercepted below read is called once and returned unchanged; no extra world read or RNG."
              + " Zero reads means no captured iteration, not proof that no treasure was placed."
              + " These bounded during-generation hints are not placement or FULL block proof.");
      result.add("treasure_support_reads", support);
      result.addProperty(
          "scope",
          "Real WorldGenRegion and native OreFeature section state changes during target StructureStart.placeInChunk only."
              + " Changed writes through those APIs invalidate prior positions regardless of scope."
              + " Evidence reads only the supplied FULL chunk and requires exact final state."
              + " Bounded samples do not prove every room, entity, direct write through another"
              + " API, or absence of later identical-state overwrites.");
      return result;
    }

    public synchronized boolean retained(JsonObject proof) {
      if (overflow) return false;
      ChunkKey chunk =
          new ChunkKey(
              proof.get("dimension").getAsString(),
              new ChunkPos(
                  proof.get("write_chunk_x").getAsInt(), proof.get("write_chunk_z").getAsInt()));
      var starts = chunks.get(chunk);
      if (starts == null) return false;
      StartKey start =
          new StartKey(
              proof.get("structure").getAsString(),
              new ChunkPos(
                  proof.get("origin_chunk_x").getAsInt(), proof.get("origin_chunk_z").getAsInt()));
      var writes = starts.get(start);
      if (writes == null) return false;
      BlockPos position =
          new BlockPos(
              proof.get("x").getAsInt(), proof.get("y").getAsInt(), proof.get("z").getAsInt());
      Write write = writes.get(position.asLong());
      return write != null
          && write.before.toString().equals(proof.get("old_state").getAsString())
          && write.after.toString().equals(proof.get("new_state").getAsString());
    }

    @Override
    public void close() {
      disarm(this);
    }
  }
}
