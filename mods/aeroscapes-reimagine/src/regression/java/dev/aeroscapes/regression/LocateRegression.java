package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.InteractiveStructureLocator;
import dev.aeroscapes.worldgen.StructureLocateCursor;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Real seeded-world parity and controller cases; call only in the disposable server fixture. */
public final class LocateRegression {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final BlockPos ORIGIN = new BlockPos(419, 132, 1940);
  private static final String DIRECT_COMMAND = "locate structure minecraft:village_desert";

  private LocateRegression() {}

  public static void verify(ServerLevel level) {
    require(level.getServer().isDedicatedServer(), "Locate regression needs a disposable server");
    require(level.getSeed() == -4851711576543251912L, "Use the reported locate-stall seed");
    Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    Holder<Structure> desert =
        registry.getHolder(ResourceLocation.parse("minecraft:village_desert")).orElseThrow();
    Holder<Structure> stronghold =
        registry.getHolder(ResourceLocation.parse("minecraft:stronghold")).orElseThrow();
    HolderSet<Structure> villageTag =
        registry
            .getTag(
                TagKey.create(Registries.STRUCTURE, ResourceLocation.parse("minecraft:village")))
            .orElseThrow();
    Holder<Structure> camp =
        registry.getHolder(ResourceLocation.parse("nova_structures:deepslate_camp")).orElseThrow();
    pendingStarts(level, camp);
    parity(level, HolderSet.direct(desert), ORIGIN, "direct desert village", 2);
    parity(level, villageTag, ORIGIN, "village tag", 2);
    Pair<BlockPos, Holder<Structure>> strongholdResult =
        parity(level, HolderSet.direct(stronghold), ORIGIN, "concentric stronghold", 2);
    parity(level, HolderSet.direct(stronghold, desert), ORIGIN, "mixed placement types", 2);
    require(
        parity(
                level,
                HolderSet.direct(camp),
                new BlockPos(80, 132, 240),
                "positive random-spread camp",
                0)
            != null,
        "Known natural camp did not produce a locate result");
    pendingRingFuture(level, stronghold);
    try (ProbeSession session = session(level, 0)) {
      routesAndCancellation(level, session);
      synchronousWrappers(level, session.player(), strongholdResult);
    }
    try (ProbeSession session = session(level, 3)) {
      InteractiveStructureLocator locator = new InteractiveStructureLocator();
      CommandSourceStack source =
          session
              .player()
              .createCommandSourceStack()
              .withPermission(4)
              .withPosition(ORIGIN.getCenter());
      require(
          route(locator, source, "locate structure minecraft:stronghold"),
          "Positive player locate did not defer");
      for (int tick = 0; tick < 200 && locator.pending(level.getServer()) != 0; tick++) {
        locator.tick(new ServerTickEvent.Post(() -> true, level.getServer()));
        level.getChunkSource().pollTask();
      }
      require(locator.pending(level.getServer()) == 0, "Positive player locate did not finish");
      require(locatorTickets(level).isEmpty(), "Completed player locate retained a ticket");
      require(
          session.player().messages.stream()
              .anyMatch(
                  message ->
                      message.getContents() instanceof TranslatableContents contents
                          && contents.getKey().equals("commands.locate.structure.success")),
          "Completed player locate did not send the native success result");
    }
    TickProbe probe = new TickProbe(level);
    NeoForge.EVENT_BUS.addListener(probe.ticker);
    NeoForge.EVENT_BUS.addListener(probe.stopper);
    LOGGER.info("Locate regression awaiting post-start ticks for seed {}", level.getSeed());
  }

  @Nullable
  private static Pair<BlockPos, Holder<Structure>> parity(
      ServerLevel level,
      HolderSet<Structure> structures,
      BlockPos origin,
      String label,
      int radius) {
    try (StructureLocateCursor cursor =
        new StructureLocateCursor(level, structures, origin, radius)) {
      long started = System.nanoTime();
      int advances = drain(level, cursor);
      long cursorNanos = System.nanoTime() - started;
      Pair<BlockPos, Holder<Structure>> nativeResult =
          level
              .getChunkSource()
              .getGenerator()
              .findNearestMapStructure(level, structures, origin, radius, false);
      require(
          java.util.Objects.equals(cursor.result(), nativeResult),
          "Locate parity failed: " + label);
      if (structures.stream()
          .anyMatch(
              structure ->
                  level
                      .getChunkSource()
                      .getGeneratorState()
                      .getPlacementsForStructure(structure)
                      .stream()
                      .anyMatch(ConcentricRingsStructurePlacement.class::isInstance))) {
        require(advances > 1, "Fixture did not exercise resume: " + label);
      }
      StructureLocateCursor.Metrics metrics = cursor.metrics();
      require(metrics.candidates() > 0, "Fixture did not check native candidates: " + label);
      require(
          metrics.activeNanos() >= metrics.maximumCandidateNanos(),
          "Invalid cursor timing metrics");
      LOGGER.info(
          "Locate parity {}: {} advances, {} candidates, {} ms wall, result {}",
          label,
          advances,
          metrics.candidates(),
          cursorNanos / 1_000_000.0,
          nativeResult);
      require(locatorTickets(level).isEmpty(), "Parity completion retained a locator ticket");
      return nativeResult;
    }
  }

  private static int drain(ServerLevel level, StructureLocateCursor cursor) {
    long deadline = System.nanoTime() + 60_000_000_000L;
    int advances = 0;
    while (!cursor.isComplete()) {
      require(System.nanoTime() < deadline, "Timed out draining the disposable locate fixture");
      if (!cursor.advance()) {
        // Startup fixture only: production yields to real ticks. This exact pinned public method
        // pumps distance-manager updates, generation tasks and main-thread chunk callbacks.
        level.getChunkSource().pollTask();
      }
      advances++;
    }
    return advances;
  }

  private record LocatorTicket(long chunk, int level) {}

  private static List<LocatorTicket> locatorTickets(ServerLevel level) {
    try {
      // Exact pinned fields, fixture only. Compare the actual private TicketType identity;
      // production needs no test accessor or widened distance-manager field.
      Field typeField = StructureLocateCursor.class.getDeclaredField("LOCATE_TICKET");
      typeField.setAccessible(true);
      Object locatorType = typeField.get(null);
      require(locatorType instanceof TicketType<?>, "Unexpected locator ticket type");
      Field ticketsField = DistanceManager.class.getDeclaredField("tickets");
      ticketsField.setAccessible(true);
      Object contents = ticketsField.get(level.getChunkSource().chunkMap.getDistanceManager());
      if (!(contents instanceof Long2ObjectMap<?> tickets)) {
        throw new IllegalStateException("Unexpected pinned distance-manager ticket map");
      }
      List<LocatorTicket> found = new ArrayList<>();
      for (Long2ObjectMap.Entry<?> entry : tickets.long2ObjectEntrySet()) {
        if (!(entry.getValue() instanceof Iterable<?> chunkTickets)) {
          throw new IllegalStateException("Unexpected pinned chunk ticket set");
        }
        for (Object value : chunkTickets) {
          if (!(value instanceof Ticket<?> ticket)) {
            throw new IllegalStateException("Unexpected pinned ticket record");
          }
          if (ticket.getType() == locatorType) {
            found.add(new LocatorTicket(entry.getLongKey(), ticket.getTicketLevel()));
          }
        }
      }
      return found;
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "Could not inspect pinned locator ticket lifecycle", exception);
    }
  }

  @Nullable
  private static CompletableFuture<?> pendingStartsFuture(StructureLocateCursor cursor) {
    try {
      Field candidateField = StructureLocateCursor.class.getDeclaredField("pendingCandidate");
      candidateField.setAccessible(true);
      Object candidate = candidateField.get(cursor);
      require(candidate != null, "Expected a suspended structure-start candidate");
      Field futureField = candidate.getClass().getDeclaredField("future");
      futureField.setAccessible(true);
      Object value = futureField.get(candidate);
      if (value == null) {
        return null;
      }
      if (!(value instanceof CompletableFuture<?> future)) {
        throw new IllegalStateException("Unexpected structure-start future type");
      }
      return future;
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Could not inspect the suspended native future", exception);
    }
  }

  private static void failedStarts(ServerLevel level, Holder<Structure> camp, boolean exceptional) {
    try (StructureLocateCursor cursor =
        new StructureLocateCursor(level, HolderSet.direct(camp), new BlockPos(80, 132, 240), 0)) {
      require(!cursor.advance(), "Cold positive camp did not suspend its native start request");
      require(locatorTickets(level).size() == 1, "Suspended failure case did not own one ticket");
      try {
        Field candidateField = StructureLocateCursor.class.getDeclaredField("pendingCandidate");
        candidateField.setAccessible(true);
        Object candidate = candidateField.get(cursor);
        require(candidate != null, "Missing failure-injection candidate");
        Field futureField = candidate.getClass().getDeclaredField("future");
        futureField.setAccessible(true);
        CompletableFuture<?> failed =
            exceptional
                ? CompletableFuture.failedFuture(
                    new IllegalStateException("fixture worldgen failure"))
                : CompletableFuture.completedFuture(ChunkResult.error("fixture unloaded chunk"));
        futureField.set(candidate, failed);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException(
            "Could not inject the fixture's completed failure", exception);
      }
      boolean rejected = false;
      try {
        cursor.advance();
      } catch (IllegalStateException | CompletionException expected) {
        rejected = true;
        LOGGER.info(
            "Expected suspended locate failure (exceptional={}): {}",
            exceptional,
            expected.toString());
      }
      require(rejected, "Failed native future became absence or synchronous fallback");
      require(!cursor.isComplete(), "Failed candidate advanced the traversal");
      require(cursor.metrics().candidates() == 1, "Polling failure counted extra candidates");
      require(locatorTickets(level).isEmpty(), "Thrown native future retained a locator ticket");
    }
  }

  private static void pendingStarts(ServerLevel level, Holder<Structure> camp) {
    require(locatorTickets(level).isEmpty(), "Fixture began with a locator ticket");
    reentrantPreflightClose(level, camp);
    failedStarts(level, camp, false);
    failedStarts(level, camp, true);
    pendingControllerCleanup(level);
    StructureLocateCursor first =
        new StructureLocateCursor(level, HolderSet.direct(camp), new BlockPos(80, 132, 240), 0);
    StructureLocateCursor second =
        new StructureLocateCursor(level, HolderSet.direct(camp), new BlockPos(80, 132, 240), 0);
    try {
      require(!first.advance() && !second.advance(), "Overlapping cold candidates did not yield");
      List<LocatorTicket> tickets = locatorTickets(level);
      require(tickets.size() == 2, "Overlapping cursors collapsed into one ticket");
      LocatorTicket target = tickets.getFirst();
      require(
          tickets.stream().allMatch(ticket -> ticket.chunk() == target.chunk()),
          "Overlap fixture did not target the same native chunk");
      require(
          tickets.stream()
              .allMatch(
                  ticket -> ticket.level() == ChunkLevel.byStatus(ChunkStatus.STRUCTURE_STARTS)),
          "Locate ticket requested another generation status");
      long deadline = System.nanoTime() + 60_000_000_000L;
      while (pendingStartsFuture(first) == null || pendingStartsFuture(second) == null) {
        require(System.nanoTime() < deadline, "Timed out scheduling overlapping fixture starts");
        level.getChunkSource().pollTask();
        if (pendingStartsFuture(first) == null) {
          require(!first.advance(), "Scheduling first native future advanced the candidate");
        }
        if (pendingStartsFuture(second) == null) {
          require(!second.advance(), "Scheduling second native future advanced the candidate");
        }
      }
      CompletableFuture<?> shared = pendingStartsFuture(first);
      require(
          shared != null && shared == pendingStartsFuture(second),
          "Native start future was not shared");
      first.close();
      first.close();
      require(locatorTickets(level).size() == 1, "Cancelling one cursor removed another ticket");
      require(!shared.isCancelled(), "Cancelling a cursor cancelled shared native generation");
      while (!shared.isDone()) {
        require(System.nanoTime() < deadline, "Timed out finishing the shared fixture starts");
        level.getChunkSource().pollTask();
      }
      require(
          shared.join() instanceof ChunkResult<?> loaded && loaded.isSuccess(),
          "Shared start future failed");
      var holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(target.chunk());
      require(
          holder != null && holder.getChunkIfPresent(ChunkStatus.STRUCTURE_STARTS) != null,
          "Native delegate would run before the requested chunk was available");
      long started = System.nanoTime();
      drain(level, second);
      require(second.result() != null, "Surviving overlap request lost its positive native start");
      require(second.metrics().candidates() == 1, "Pending polls advanced the candidate order");
      require(locatorTickets(level).isEmpty(), "Successful native resume retained a ticket");
      LOGGER.info(
          "Locate suspended-start overlap passed; ready native resume {} ms",
          (System.nanoTime() - started) / 1_000_000.0);
    } finally {
      first.close();
      second.close();
    }
    require(locatorTickets(level).isEmpty(), "Closed overlap fixture leaked a locator ticket");
  }

  private static void pendingControllerCleanup(ServerLevel level) {
    String command = "locate structure nova_structures:deepslate_camp";
    InteractiveStructureLocator locator = new InteractiveStructureLocator();
    ServerTickEvent.Post tick = new ServerTickEvent.Post(() -> true, level.getServer());
    try (ProbeSession session = session(level, 4)) {
      CommandSourceStack source =
          session
              .player()
              .createCommandSourceStack()
              .withPermission(4)
              .withPosition(new BlockPos(80, 132, 240).getCenter());
      for (String reason :
          List.of("replaced", "world_changed", "stopped", "dimension", "disconnected")) {
        require(route(locator, source, command), "Could not queue pending cleanup case: " + reason);
        locator.tick(tick);
        require(
            locatorTickets(level).size() == 1,
            "Controller did not own a pending ticket: " + reason);
        switch (reason) {
          case "replaced" -> {
            require(route(locator, source, command), "Could not replace a suspended request");
            locator.unload(new LevelEvent.Unload(level));
          }
          case "world_changed" -> locator.unload(new LevelEvent.Unload(level));
          case "stopped" -> locator.stop(new ServerStoppingEvent(level.getServer()));
          case "dimension" -> {
            session.player().setServerLevel(level.getServer().getLevel(ServerLevel.NETHER));
            locator.tick(tick);
            session.player().setServerLevel(level);
          }
          case "disconnected" -> {
            session.channel().close().syncUninterruptibly();
            locator.tick(tick);
          }
          default -> throw new IllegalStateException("Unknown cleanup fixture case");
        }
        require(
            locator.pending(level.getServer()) == 0,
            "Cancellation retained a queued search: " + reason);
        require(
            locatorTickets(level).isEmpty(), "Cancellation retained a native ticket: " + reason);
      }
    } finally {
      locator.stop(new ServerStoppingEvent(level.getServer()));
    }
  }

  private static void reentrantPreflightClose(ServerLevel level, Holder<Structure> camp) {
    var state = level.getChunkSource().getGeneratorState();
    List<StructurePlacement> actualPlacements = state.getPlacementsForStructure(camp);
    AtomicReference<StructureLocateCursor> active = new AtomicReference<>();
    AtomicBoolean invoked = new AtomicBoolean();
    Structure original = camp.value();
    Structure closing =
        new Structure(
            new Structure.StructureSettings(
                original.biomes(),
                original.spawnOverrides(),
                original.step(),
                original.terrainAdaptation())) {
          @Override
          protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
            return Optional.empty();
          }

          @Override
          public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context) {
            invoked.set(true);
            active.get().close();
            return Optional.of(new GenerationStub(BlockPos.ZERO, builder -> {}));
          }

          @Override
          public StructureType<?> type() {
            return original.type();
          }
        };
    Map<Structure, List<StructurePlacement>> placements = fixturePlacements(state);
    require(!placements.containsKey(closing), "Synthetic callback structure already registered");
    placements.put(closing, actualPlacements);
    try (StructureLocateCursor cursor =
        new StructureLocateCursor(
            level, HolderSet.direct(Holder.direct(closing)), new BlockPos(80, 132, 240), 0)) {
      active.set(cursor);
      cursor.advance();
      require(invoked.get(), "Synthetic reentrant preflight callback never ran");
      require(
          cursor.metrics().candidates() == 1, "Reentrant fixture did not enter native preflight");
      require(
          locatorTickets(level).isEmpty(),
          "Reentrant preflight cancellation created an orphan ticket");
    } finally {
      placements.remove(closing);
    }
  }

  private static Map<Structure, List<StructurePlacement>> fixturePlacements(
      ChunkGeneratorStructureState state) {
    try {
      Field field = ChunkGeneratorStructureState.class.getDeclaredField("placementsForStructure");
      field.setAccessible(true);
      Object contents = field.get(state);
      if (!(contents instanceof Map<?, ?> placements)) {
        throw new IllegalStateException("Unexpected pinned placement map type");
      }
      for (Map.Entry<?, ?> entry : placements.entrySet()) {
        require(entry.getKey() instanceof Structure, "Unexpected placement map key");
        if (!(entry.getValue() instanceof List<?> values)) {
          throw new IllegalStateException("Unexpected placement map value");
        }
        require(
            values.stream().allMatch(StructurePlacement.class::isInstance),
            "Unexpected placement record");
      }
      // The map and every existing key/list/member were validated above; this narrowly scoped
      // unchecked conversion lets the fixture add/remove its own callback structure in finally.
      @SuppressWarnings("unchecked")
      Map<Structure, List<StructurePlacement>> validated =
          (Map<Structure, List<StructurePlacement>>) placements;
      return validated;
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "Could not install the fixture's reentrant callback", exception);
    }
  }

  private static void pendingRingFuture(ServerLevel level, Holder<Structure> stronghold) {
    var state = level.getChunkSource().getGeneratorState();
    ConcentricRingsStructurePlacement placement =
        state.getPlacementsForStructure(stronghold).stream()
            .filter(ConcentricRingsStructurePlacement.class::isInstance)
            .map(ConcentricRingsStructurePlacement.class::cast)
            .findFirst()
            .orElseThrow();
    CompletableFuture<List<ChunkPos>> original = state.ringPositions.get(placement);
    require(original != null, "Missing stronghold ring future");
    try (StructureLocateCursor cursor =
        new StructureLocateCursor(level, HolderSet.direct(stronghold), ORIGIN, 2)) {
      // Hold just this existing asynchronous boundary pending; restore it before any native check.
      state.ringPositions.put(placement, new CompletableFuture<>());
      try {
        require(!cursor.advance(), "Pending ring computation did not yield");
        require(
            !cursor.isComplete() && cursor.metrics().candidates() == 0,
            "Pending rings searched or completed early");
      } finally {
        state.ringPositions.put(placement, original);
      }
      cursor.advance();
      require(
          cursor.metrics().candidates() == 1, "Resumed cursor did not check exactly one candidate");
    }
  }

  private static void routesAndCancellation(ServerLevel level, ProbeSession session) {
    InteractiveStructureLocator locator = new InteractiveStructureLocator();
    CommandSourceStack source =
        session
            .player()
            .createCommandSourceStack()
            .withPermission(4)
            .withPosition(ORIGIN.getCenter());
    require(route(locator, source, DIRECT_COMMAND), "Direct player command was not deferred");
    require(locator.pending(level.getServer()) == 1, "Direct command did not queue one search");
    require(route(locator, source, DIRECT_COMMAND + "  "), "Trailing whitespace did not defer");
    require(locator.pending(level.getServer()) == 1, "Replacement retained more than one search");
    require(
        session.player().messages.stream().anyMatch(LocateRegression::cancelMessage),
        "Replacing a request did not report cancellation");
    require(
        route(locator, source, "locate structure #minecraft:village"), "Direct tag did not defer");
    for (String command :
        List.of(
            DIRECT_COMMAND + " extra",
            "locate structure aeroscapes_reimagine:missing_regression_structure",
            "locate structure #aeroscapes_reimagine:missing_regression_tag",
            "locate biome minecraft:desert",
            "execute as @s run " + DIRECT_COMMAND,
            "execute store result storage aeroscapes_reimagine:locate_regression value int 1 run "
                + DIRECT_COMMAND,
            "function aeroscapes_reimagine:locate_regression")) {
      require(
          !route(locator, source, command),
          "Deferred a command with synchronous/native semantics: " + command);
    }
    require(
        !route(locator, source.withCallback((success, result) -> {}), DIRECT_COMMAND),
        "Deferred a caller's result callback");
    require(
        !route(locator, level.getServer().createCommandSourceStack(), DIRECT_COMMAND),
        "Deferred a console command");
    locator.unload(new LevelEvent.Unload(level));
    require(locator.pending(level.getServer()) == 0, "World unload retained searches");
    require(route(locator, source, DIRECT_COMMAND), "Could not queue after world cancellation");
    locator.stop(new ServerStoppingEvent(level.getServer()));
    require(locator.pending(level.getServer()) == 0, "Server stop retained searches");
    require(route(locator, source, DIRECT_COMMAND), "Could not queue transport cancellation case");
    session.channel().close().syncUninterruptibly();
    locator.tick(new ServerTickEvent.Post(() -> true, level.getServer()));
    require(locator.pending(level.getServer()) == 0, "Closed transport retained a search");
  }

  private static boolean route(
      InteractiveStructureLocator locator, CommandSourceStack source, String input) {
    CommandEvent event =
        new CommandEvent(source.getServer().getCommands().getDispatcher().parse(input, source));
    locator.command(event);
    return event.isCanceled();
  }

  private static boolean cancelMessage(Component message) {
    return (message.getContents() instanceof TranslatableContents contents
            && contents.getKey().equals("commands.aeroscapes_reimagine.locate.cancelled"))
        || message.getSiblings().stream().anyMatch(LocateRegression::cancelMessage);
  }

  private static void synchronousWrappers(
      ServerLevel level,
      ProbePlayer player,
      @Nullable Pair<BlockPos, Holder<Structure>> strongholdResult) {
    require(
        strongholdResult != null, "Need an actual stronghold result to verify synchronous storage");
    BlockPos result = strongholdResult.getFirst();
    int expected =
        Mth.floor(
            Mth.sqrt(
                (float) ORIGIN.distToLowCornerSqr(result.getX(), ORIGIN.getY(), result.getZ())));
    ResourceLocation storage = ResourceLocation.parse("aeroscapes_reimagine:locate_regression");
    CompoundTag values = new CompoundTag();
    values.putInt("wrapped", -1);
    values.putInt("function", -1);
    level.getServer().getCommandStorage().set(storage, values);
    CommandSourceStack source =
        player.createCommandSourceStack().withPermission(4).withPosition(ORIGIN.getCenter());
    String command =
        "execute store result storage "
            + storage
            + " wrapped int 1 run locate structure minecraft:stronghold";
    level.getServer().getCommands().performPrefixedCommand(source, command);
    require(
        level.getServer().getCommandStorage().get(storage).getInt("wrapped") == expected,
        "Execute store lost its immediate locate result");
    CommandFunction<CommandSourceStack> function =
        CommandFunction.fromLines(
            storage,
            level.getServer().getCommands().getDispatcher(),
            source,
            List.of(command.replace(" wrapped int ", " function int ")));
    level.getServer().getFunctions().execute(function, source);
    require(
        level.getServer().getCommandStorage().get(storage).getInt("function") == expected,
        "Function lost its immediate locate result");
  }

  private static ProbeSession session(ServerLevel level, int number) {
    ProbePlayer player = new ProbePlayer(level, number);
    Connection connection = new Connection(PacketFlow.SERVERBOUND);
    EmbeddedChannel channel = new EmbeddedChannel(connection);
    new ServerGamePacketListenerImpl(
        level.getServer(),
        connection,
        player,
        CommonListenerCookie.createInitial(player.getGameProfile(), false));
    return new ProbeSession(player, channel);
  }

  private record ProbeSession(ProbePlayer player, EmbeddedChannel channel)
      implements AutoCloseable {
    @Override
    public void close() {
      channel.finishAndReleaseAll();
      player.getTextFilter().leave();
    }
  }

  private static final class ProbePlayer extends ServerPlayer {
    private final List<Component> messages = new ArrayList<>();

    private ProbePlayer(ServerLevel level, int number) {
      super(
          level.getServer(),
          level,
          new GameProfile(
              new UUID(0xb13a004125d449d4L, 0xb8e61f497e481f90L + number), "LocateProbe" + number),
          ClientInformation.createDefault());
    }

    @Override
    public void sendSystemMessage(Component message) {
      messages.add(message);
    }
  }

  private static final class TickProbe {
    private final ServerLevel level;
    private final InteractiveStructureLocator locator = new InteractiveStructureLocator();
    private final ProbeSession first;
    private final ProbeSession second;
    private final CommandSourceStack firstSource;
    private final CommandSourceStack secondSource;
    private final int startedTick;
    private final Consumer<ServerTickEvent.Post> ticker = this::tick;
    private final Consumer<ServerStoppingEvent> stopper = this::stop;
    private int ticks;
    private long maximumSliceNanos;

    private TickProbe(ServerLevel level) {
      this.level = level;
      first = session(level, 1);
      second = session(level, 2);
      firstSource =
          first
              .player()
              .createCommandSourceStack()
              .withPermission(4)
              .withPosition(ORIGIN.getCenter());
      secondSource =
          second
              .player()
              .createCommandSourceStack()
              .withPermission(4)
              .withPosition(ORIGIN.getCenter());
      startedTick = level.getServer().getTickCount();
      require(route(locator, firstSource, DIRECT_COMMAND), "Could not queue first tick search");
      require(route(locator, secondSource, DIRECT_COMMAND), "Could not queue second tick search");
    }

    private void tick(ServerTickEvent.Post event) {
      if (event.getServer() != level.getServer()) {
        return;
      }
      try {
        ticks++;
        if (ticks == 9) {
          second.channel().close().syncUninterruptibly();
        } else if (ticks == 11) {
          first.player().setServerLevel(level.getServer().getLevel(ServerLevel.NETHER));
        }
        long started = System.nanoTime();
        locator.tick(event);
        maximumSliceNanos = Math.max(maximumSliceNanos, System.nanoTime() - started);
        if (ticks == 8) {
          require(
              locator.pending(level.getServer()) == 2,
              "Long searches completed before the ticking case could exercise cancellation");
          logProgress(first.player());
          logProgress(second.player());
          require(
              route(locator, firstSource, DIRECT_COMMAND), "Could not replace an active search");
          require(
              locator.pending(level.getServer()) == 2,
              "Replacement changed the number of player searches");
        } else if (ticks == 10) {
          require(
              locator.pending(level.getServer()) == 1,
              "Disconnected transport survived two scheduler ticks");
        } else if (ticks == 11) {
          require(
              locator.pending(level.getServer()) == 0,
              "Changing dimensions retained the original search");
          first.player().setServerLevel(level);
        } else if (ticks == 12) {
          require(route(locator, firstSource, DIRECT_COMMAND), "Could not queue world unload case");
          locator.unload(new LevelEvent.Unload(level));
          require(locator.pending(level.getServer()) == 0, "World unload retained a ticked search");
        } else if (ticks == 13) {
          require(route(locator, firstSource, DIRECT_COMMAND), "Could not queue stop case");
          locator.stop(new ServerStoppingEvent(level.getServer()));
          require(locator.pending(level.getServer()) == 0, "Server stop retained a ticked search");
          require(
              level.getServer().getTickCount() > startedTick,
              "Server ticks did not advance during locate");
          cleanup();
          LOGGER.info(
              "Locate regression passed: {} live server ticks; {} ms maximum shared slice (one"
                  + " native candidate can overrun 2 ms)",
              ticks,
              maximumSliceNanos / 1_000_000.0);
        }
      } catch (RuntimeException exception) {
        cleanup();
        throw exception;
      }
    }

    private void logProgress(ServerPlayer player) {
      StructureLocateCursor.Metrics metrics = locator.metrics(player).orElseThrow();
      require(metrics.candidates() > 0, "Round robin did not advance both player searches");
      LOGGER.info(
          "Ticked locate {}: {} candidates, {} ms active, {} ms maximum native candidate",
          player.getGameProfile().getName(),
          metrics.candidates(),
          metrics.activeNanos() / 1_000_000.0,
          metrics.maximumCandidateNanos() / 1_000_000.0);
    }

    private void stop(ServerStoppingEvent event) {
      if (event.getServer() == level.getServer()) {
        cleanup();
      }
    }

    private void cleanup() {
      NeoForge.EVENT_BUS.unregister(ticker);
      NeoForge.EVENT_BUS.unregister(stopper);
      locator.stop(new ServerStoppingEvent(level.getServer()));
      first.close();
      second.close();
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
