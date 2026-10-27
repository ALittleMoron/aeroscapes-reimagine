package dev.aeroscapes.worldgen;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * Resumes Minecraft 1.21.1's locate traversal between native candidate checks on the server thread.
 *
 * <p>Internal implementation exposed to the separately packaged regression module. It must be used
 * on the logical server thread, as are the native candidate checks it delegates to.
 */
public final class StructureLocateCursor implements AutoCloseable {
  private static final TicketType<UUID> LOCATE_TICKET =
      TicketType.create("aeroscapes_reimagine_locate", UUID::compareTo);
  private static final int TICKET_LEVEL = ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS);

  private enum ConcentricStep {
    CANDIDATE,
    WAITING,
    FINISHED
  }

  private record Placement(StructurePlacement placement, Set<Holder<Structure>> structures) {}

  private record CandidateResult(@Nullable Pair<BlockPos, Holder<Structure>> result) {}

  private static final class PendingCandidate {
    private final Placement placement;
    private final ChunkPos position;
    private final UUID ticketKey;
    @Nullable private CompletableFuture<ChunkResult<ChunkAccess>> future;
    private boolean ownsTicket;
    private long activeNanos;

    private PendingCandidate(Placement placement, ChunkPos position, UUID ticketKey) {
      this.placement = placement;
      this.position = position;
      this.ticketKey = ticketKey;
    }
  }

  public record Metrics(long candidates, long activeNanos, long maximumCandidateNanos) {}

  private final ServerLevel level;
  private final List<Holder<Structure>> structures;
  private final BlockPos origin;
  private final int radius;
  private final UUID ticketKey = UUID.randomUUID();
  private final List<Placement> concentric = new ArrayList<>();
  private final List<Placement> randomSpread = new ArrayList<>();
  private boolean initialized;
  private boolean complete;
  private boolean closed;
  @Nullable private PendingCandidate pendingCandidate;
  @Nullable private Pair<BlockPos, Holder<Structure>> best;
  private double bestDistance = Double.MAX_VALUE;
  private int concentricIndex;
  private List<ChunkPos> ringPositions = List.of();
  private int ringPositionIndex;
  @Nullable private Pair<BlockPos, Holder<Structure>> concentricBest;
  private double concentricDistance = Double.MAX_VALUE;
  private boolean loadedRingPositions;
  private int ring;
  private int randomIndex;
  private int gridX;
  private int gridZ;
  private boolean foundInRing;
  private long candidates;
  private long activeNanos;
  private long maximumCandidateNanos;

  public StructureLocateCursor(
      ServerLevel level, HolderSet<Structure> structures, BlockPos origin, int radius) {
    this.level = level;
    this.structures = structures.stream().toList();
    this.origin = origin.immutable();
    this.radius = radius;
  }

  public boolean isComplete() {
    return complete;
  }

  @Nullable
  public Pair<BlockPos, Holder<Structure>> result() {
    if (!complete) {
      throw new IllegalStateException("Structure search has not completed");
    }
    return best;
  }

  public Metrics metrics() {
    return new Metrics(candidates, activeNanos, maximumCandidateNanos);
  }

  /**
   * Presence checks may overrun the budget; native structure-start generation yields between ticks.
   */
  public boolean advance() {
    if (closed) {
      throw new IllegalStateException("Structure search has been closed");
    }
    if (complete) {
      return false;
    }
    long started = System.nanoTime();
    try {
      if (!initialized) {
        initialize();
      }
      ConcentricStep step = advanceConcentric();
      if (step != ConcentricStep.FINISHED) {
        return step == ConcentricStep.CANDIDATE;
      }
      if (randomSpread.isEmpty() || ring > radius) {
        complete = true;
        return true;
      }
      return advanceRandomSpread();
    } finally {
      activeNanos += System.nanoTime() - started;
    }
  }

  private void initialize() {
    ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
    Map<StructurePlacement, Set<Holder<Structure>>> placements = new Object2ObjectArrayMap<>();
    for (Holder<Structure> structure : structures) {
      for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
        placements.computeIfAbsent(placement, ignored -> new ObjectArraySet<>()).add(structure);
      }
    }
    // Preserve vanilla's placement and holder insertion order, including first-hit ties.
    for (Map.Entry<StructurePlacement, Set<Holder<Structure>>> entry : placements.entrySet()) {
      Placement placement = new Placement(entry.getKey(), entry.getValue());
      if (entry.getKey() instanceof ConcentricRingsStructurePlacement) {
        concentric.add(placement);
      } else if (entry.getKey() instanceof RandomSpreadStructurePlacement) {
        randomSpread.add(placement);
      }
    }
    initialized = true;
  }

  private ConcentricStep advanceConcentric() {
    while (concentricIndex < concentric.size()) {
      Placement placement = concentric.get(concentricIndex);
      if (!loadedRingPositions) {
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        var future = state.ringPositions.get(placement.placement());
        if (future != null && !future.isDone()) {
          return ConcentricStep.WAITING;
        }
        List<ChunkPos> positions =
            state.getRingPositionsFor((ConcentricRingsStructurePlacement) placement.placement());
        if (positions == null) {
          throw new IllegalStateException("No ring positions for a registered structure placement");
        }
        ringPositions = List.copyOf(positions);
        loadedRingPositions = true;
      }
      while (ringPositionIndex < ringPositions.size()) {
        ChunkPos position = ringPositions.get(ringPositionIndex);
        double distance =
            new BlockPos(position.getMiddleBlockX(), 32, position.getMiddleBlockZ())
                .distSqr(origin);
        if (concentricBest == null || distance < concentricDistance) {
          CandidateResult checked = checkCandidate(placement, position);
          if (checked == null) {
            return ConcentricStep.WAITING;
          }
          ringPositionIndex++;
          if (checked.result() != null) {
            concentricBest = checked.result();
            // Vanilla prunes this placement by chunk-center Y=32, not by its locate position.
            concentricDistance = distance;
          }
          return ConcentricStep.CANDIDATE;
        }
        ringPositionIndex++;
      }
      consider(concentricBest);
      concentricIndex++;
      ringPositionIndex = 0;
      loadedRingPositions = false;
      ringPositions = List.of();
      concentricBest = null;
      concentricDistance = Double.MAX_VALUE;
    }
    return ConcentricStep.FINISHED;
  }

  private boolean advanceRandomSpread() {
    Placement group = randomSpread.get(randomIndex);
    RandomSpreadStructurePlacement placement = (RandomSpreadStructurePlacement) group.placement();
    ChunkPos position =
        placement.getPotentialStructureChunk(
            level.getChunkSource().getGeneratorState().getLevelSeed(),
            SectionPos.blockToSectionCoord(origin.getX()) + placement.spacing() * gridX,
            SectionPos.blockToSectionCoord(origin.getZ()) + placement.spacing() * gridZ);
    CandidateResult checked = checkCandidate(group, position);
    if (checked == null) {
      return false;
    }
    if (checked.result() != null) {
      foundInRing = true;
      consider(checked.result());
      nextRandomPlacement();
    } else if (!nextPerimeterPosition()) {
      nextRandomPlacement();
    }
    return true;
  }

  /** Skip only the interior cells that vanilla's nested loops never test. */
  private boolean nextPerimeterPosition() {
    if (gridX == -ring || gridX == ring) {
      gridZ++;
      if (gridZ > ring) {
        gridX++;
        gridZ = -ring;
      }
    } else if (gridZ == -ring) {
      gridZ = ring;
    } else {
      gridX++;
      gridZ = -ring;
    }
    return gridX <= ring;
  }

  private void nextRandomPlacement() {
    randomIndex++;
    if (randomIndex == randomSpread.size()) {
      // A hit ends the whole ring only after every placement has had its first hit or exhausted it.
      if (foundInRing) {
        complete = true;
        return;
      }
      ring++;
      randomIndex = 0;
      if (ring > radius) {
        complete = true;
      }
    }
    gridX = -ring;
    gridZ = -ring;
  }

  private void consider(@Nullable Pair<BlockPos, Holder<Structure>> candidate) {
    if (candidate != null) {
      double distance = origin.distSqr(candidate.getFirst());
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
  }

  @Nullable
  private CandidateResult checkCandidate(Placement placement, ChunkPos position) {
    long started = System.nanoTime();
    PendingCandidate candidate = pendingCandidate;
    boolean fresh = candidate == null;
    if (fresh) {
      candidate = new PendingCandidate(placement, position, ticketKey);
      pendingCandidate = candidate;
      candidates++;
    } else if (candidate.placement != placement || !candidate.position.equals(position)) {
      throw new IllegalStateException("Structure search advanced past a pending candidate");
    }
    boolean waiting = false;
    try {
      var chunkMap = level.getChunkSource().chunkMap;
      if (fresh) {
        StructureCheckResult presence = firstStructurePresence(placement, position);
        if (closed) {
          return new CandidateResult(null);
        }
        if (presence == StructureCheckResult.START_NOT_PRESENT) {
          // Exact 1.21.1 getStructureGeneratingAt: all ordered checks absent -> null, with no
          // further work. Repeating its loop would join uncached negative metadata reads again.
          return new CandidateResult(null);
        }
        if (presence == StructureCheckResult.CHUNK_LOAD_NEEDED) {
          candidate.ownsTicket = true;
          // NeoForge 21.1.230 exposes getDistanceManager(). Use the exact native status level;
          // vanilla's UNKNOWN ticket expires after one tick and cannot cover this suspended wait.
          chunkMap
              .getDistanceManager()
              .addTicket(LOCATE_TICKET, position, TICKET_LEVEL, candidate.ticketKey);
          waiting = true;
          return null;
        }
      } else {
        if (candidate.future == null) {
          var holder = chunkMap.getVisibleChunkIfPresent(position.toLong());
          if (holder != null && holder.getTicketLevel() <= TICKET_LEVEL) {
            // Unlike ServerChunkCache.getChunkFuture(), this public pinned method does not
            // managedBlock on the logical server thread. Normal level ticks propagate our ticket.
            candidate.future =
                holder.scheduleChunkGenerationTask(ChunkStatus.STRUCTURE_STARTS, chunkMap);
          }
          waiting = true;
          return null;
        }
        if (!candidate.future.isDone()) {
          waiting = true;
          return null;
        }
        ChunkResult<ChunkAccess> loaded = candidate.future.join();
        if (!loaded.isSuccess()) {
          throw new IllegalStateException(
              "Structure-start chunk request failed: " + loaded.getError());
        }
        var holder = chunkMap.getVisibleChunkIfPresent(position.toLong());
        if (holder == null
            || holder.getTicketLevel() > TICKET_LEVEL
            || holder.getChunkIfPresent(ChunkStatus.STRUCTURE_STARTS) == null) {
          throw new IllegalStateException("Completed structure-start chunk is no longer available");
        }
      }
      return new CandidateResult(
          ChunkGenerator.getStructureGeneratingAt(
              placement.structures(),
              level,
              level.structureManager(),
              false,
              placement.placement(),
              position));
    } finally {
      candidate.activeNanos += System.nanoTime() - started;
      maximumCandidateNanos = Math.max(maximumCandidateNanos, candidate.activeNanos);
      if (!waiting || closed) {
        release(candidate);
      }
    }
  }

  private StructureCheckResult firstStructurePresence(Placement placement, ChunkPos position) {
    for (Holder<Structure> structure : placement.structures()) {
      StructureCheckResult presence =
          level
              .structureManager()
              .checkStructurePresence(position, structure.value(), placement.placement(), false);
      // Native skipKnown=false returns immediately for the first START_PRESENT. A later holder
      // requiring a chunk must not cause generation that the original native call would skip.
      if (presence != StructureCheckResult.START_NOT_PRESENT) {
        return presence;
      }
    }
    return StructureCheckResult.START_NOT_PRESENT;
  }

  private void release(PendingCandidate candidate) {
    if (pendingCandidate == candidate) {
      pendingCandidate = null;
    }
    if (candidate.ownsTicket) {
      candidate.ownsTicket = false;
      level
          .getChunkSource()
          .chunkMap
          .getDistanceManager()
          .removeTicket(LOCATE_TICKET, candidate.position, TICKET_LEVEL, candidate.ticketKey);
    }
  }

  @Override
  public void close() {
    closed = true;
    if (pendingCandidate != null) {
      release(pendingCandidate);
    }
  }
}
