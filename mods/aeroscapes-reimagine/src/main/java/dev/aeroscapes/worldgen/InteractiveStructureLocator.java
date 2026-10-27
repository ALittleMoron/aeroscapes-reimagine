package dev.aeroscapes.worldgen;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.logging.LogUtils;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Defers direct player locate commands; command functions, execute chains and map callers stay
 * native.
 *
 * <p>Internal server-thread implementation. Public access lets the separate regression module
 * exercise the real controller without sharing this package or shipping fixture classes.
 */
public final class InteractiveStructureLocator {
  private static final Logger LOGGER = LogUtils.getLogger();
  private static final int SEARCH_RADIUS = 100;
  private static final long TICK_BUDGET_NANOS = 2_000_000;
  private static final String MESSAGE_PREFIX = "commands.aeroscapes_reimagine.locate.";
  private static final DynamicCommandExceptionType INVALID_STRUCTURE =
      new DynamicCommandExceptionType(
          value -> Component.translatableEscape("commands.locate.structure.invalid", value));

  private record Search(
      ServerPlayer player,
      CommandSourceStack source,
      ResourceOrTagKeyArgument.Result<Structure> argument,
      BlockPos origin,
      StructureLocateCursor cursor,
      long started) {}

  private static final class ServerSearches {
    private final Map<UUID, Search> active = new HashMap<>();
    private final ArrayDeque<Search> queue = new ArrayDeque<>();
  }

  private final Map<MinecraftServer, ServerSearches> searches = new HashMap<>();

  public InteractiveStructureLocator() {}

  static void register() {
    InteractiveStructureLocator locator = new InteractiveStructureLocator();
    NeoForge.EVENT_BUS.addListener(locator::command);
    NeoForge.EVENT_BUS.addListener(locator::tick);
    NeoForge.EVENT_BUS.addListener(locator::unload);
    NeoForge.EVENT_BUS.addListener(locator::stop);
  }

  public void command(CommandEvent event) {
    var parsed = event.getParseResults();
    var context = parsed.getContext();
    CommandSourceStack source = context.getSource();
    ServerPlayer player = source.getPlayer();
    List<ParsedCommandNode<CommandSourceStack>> nodes = context.getNodes();
    // CommandEvent observes the initial dispatch. Compiled function bodies do not fire it;
    // /execute and /return have different root nodes even when their final source is a player.
    if (event.isCanceled()
        || player == null
        || source.source != player
        || source.callback() != CommandResultCallback.EMPTY
        || !source.hasPermission(2)
        || !parsed.getReader().getRemaining().isBlank()
        || context.getChild() != null
        || context.getCommand() == null
        || nodes.size() != 3
        || !nodes.get(0).getNode().getName().equals("locate")
        || !nodes.get(1).getNode().getName().equals("structure")
        || !nodes.get(2).getNode().getName().equals("structure")) {
      return;
    }
    CommandContext<CommandSourceStack> command = context.build(parsed.getReader().getString());
    ResourceOrTagKeyArgument.Result<Structure> argument;
    try {
      argument =
          ResourceOrTagKeyArgument.getResourceOrTagKey(
              command, "structure", Registries.STRUCTURE, INVALID_STRUCTURE);
    } catch (CommandSyntaxException exception) {
      return; // Let the native executor report invalid arguments with its original error.
    }
    Registry<Structure> registry =
        source.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
    Optional<? extends HolderSet<Structure>> holders =
        argument
            .unwrap()
            .map(key -> registry.getHolder(key).map(HolderSet::direct), registry::getTag);
    if (holders.isEmpty()) {
      return; // Missing IDs/tags retain vanilla's error rather than becoming empty searches.
    }
    event.setCanceled(true);
    BlockPos origin = BlockPos.containing(source.getPosition());
    Search search =
        new Search(
            player,
            source,
            argument,
            origin,
            new StructureLocateCursor(
                source.getLevel(), holders.orElseThrow(), origin, SEARCH_RADIUS),
            System.nanoTime());
    ServerSearches state =
        searches.computeIfAbsent(source.getServer(), ignored -> new ServerSearches());
    Search previous = state.active.put(player.getUUID(), search);
    if (previous != null) {
      state.queue.remove(previous);
      cancel(previous, "replaced");
    }
    state.queue.addLast(search);
    source.sendSuccess(
        () -> Component.translatable(MESSAGE_PREFIX + "searching", argument.asPrintable()), false);
  }

  public void tick(ServerTickEvent.Post event) {
    MinecraftServer server = event.getServer();
    ServerSearches state = searches.get(server);
    if (state == null) {
      return;
    }
    long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
    boolean progressed = false;
    int waiting = 0;
    while (!state.queue.isEmpty() && (!progressed || System.nanoTime() < deadline)) {
      Search search = state.queue.removeFirst();
      if (state.active.get(search.player().getUUID()) != search) {
        search.cursor().close();
        continue;
      }
      if (!valid(search, server)) {
        state.active.remove(search.player().getUUID());
        cancel(search, server.isRunning() ? "world_changed" : "stopped");
        continue;
      }
      try {
        boolean advanced = search.cursor().advance();
        progressed = true;
        // A native chunk wait may run another command or unload callback. Its replacement or
        // cancellation must win even when this candidate subsequently returns successfully.
        if (state.active.get(search.player().getUUID()) != search) {
          search.cursor().close();
          continue;
        }
        if (!valid(search, server)) {
          state.active.remove(search.player().getUUID());
          cancel(search, server.isRunning() ? "world_changed" : "stopped");
          continue;
        }
        if (search.cursor().isComplete()) {
          state.active.remove(search.player().getUUID());
          finish(search);
        } else {
          state.queue.addLast(search);
          waiting = advanced ? 0 : waiting + 1;
          if (waiting >= state.queue.size()) {
            break; // All remaining cursors await native futures; do not busy-poll this tick.
          }
        }
      } catch (RuntimeException exception) {
        // Native generation and mod callbacks have no checked failure contract. Isolate this
        // request at the tick boundary, retain the cause, and do not turn it into a server crash.
        search.cursor().close();
        LOGGER.error(
            "Structure search failed for {}: {}",
            search.player().getUUID(),
            search.argument().asPrintable(),
            exception);
        if (state.active.remove(search.player().getUUID(), search)) {
          search.source().sendFailure(Component.translatable(MESSAGE_PREFIX + "failed"));
        }
        logMetrics(search, "failed");
        progressed = true;
      }
    }
    if (state.active.isEmpty()) {
      searches.remove(server, state);
    }
  }

  public void unload(LevelEvent.Unload event) {
    if (event.getLevel() instanceof ServerLevel level) {
      ServerSearches state = searches.get(level.getServer());
      if (state != null) {
        state
            .active
            .values()
            .removeIf(
                search -> {
                  if (search.source().getLevel() == level) {
                    state.queue.remove(search);
                    cancel(search, "world_changed");
                    return true;
                  }
                  return false;
                });
        if (state.active.isEmpty()) {
          searches.remove(level.getServer());
        }
      }
    }
  }

  public void stop(ServerStoppingEvent event) {
    ServerSearches state = searches.remove(event.getServer());
    if (state != null) {
      for (Search search : state.active.values()) {
        cancel(search, "stopped");
      }
      state.active.clear();
      state.queue.clear();
    }
  }

  public int pending(MinecraftServer server) {
    ServerSearches state = searches.get(server);
    return state == null ? 0 : state.active.size();
  }

  public Optional<StructureLocateCursor.Metrics> metrics(ServerPlayer player) {
    ServerSearches state = searches.get(player.serverLevel().getServer());
    if (state == null) {
      return Optional.empty();
    }
    Search search = state.active.get(player.getUUID());
    return search == null ? Optional.empty() : Optional.of(search.cursor().metrics());
  }

  private static boolean connected(ServerPlayer player) {
    return player.connection != null && player.connection.getConnection().isConnected();
  }

  private static boolean valid(Search search, MinecraftServer server) {
    return server.isRunning()
        && connected(search.player())
        && !search.player().isRemoved()
        && search.player().serverLevel() == search.source().getLevel()
        && server.getLevel(search.source().getLevel().dimension()) == search.source().getLevel();
  }

  private static void cancel(Search search, String reason) {
    search.cursor().close();
    if (connected(search.player())) {
      search
          .source()
          .sendFailure(
              Component.translatable(
                  MESSAGE_PREFIX + "cancelled", Component.translatable(MESSAGE_PREFIX + reason)));
    }
    logMetrics(search, "cancelled: " + reason);
  }

  private static void finish(Search search) {
    try {
      var result = search.cursor().result();
      if (result == null) {
        search
            .source()
            .sendFailure(
                Component.translatableEscape(
                    "commands.locate.structure.not_found", search.argument().asPrintable()));
      } else {
        LocateCommand.showLocateResult(
            search.source(),
            search.argument(),
            search.origin(),
            result,
            "commands.locate.structure.success",
            false,
            Duration.ofNanos(System.nanoTime() - search.started()));
      }
      logMetrics(search, "complete");
    } finally {
      search.cursor().close();
    }
  }

  private static void logMetrics(Search search, String outcome) {
    StructureLocateCursor.Metrics metrics = search.cursor().metrics();
    LOGGER.info(
        "Interactive locate {} for {} by {}: {} candidates, {} ms active, {} ms wall, {} ms maximum"
            + " candidate",
        outcome,
        search.argument().asPrintable(),
        search.player().getUUID(),
        metrics.candidates(),
        metrics.activeNanos() / 1_000_000.0,
        (System.nanoTime() - search.started()) / 1_000_000.0,
        metrics.maximumCandidateNanos() / 1_000_000.0);
  }
}
