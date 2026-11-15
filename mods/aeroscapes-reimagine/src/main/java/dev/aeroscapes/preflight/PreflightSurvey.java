package dev.aeroscapes.preflight;

import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map.BalloonMap;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.content.blocks.portable_engine.PortableEngineBlockEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class PreflightSurvey {
  private static final Map<ServerPlayer, PreflightRequestWindow> REQUESTS = new WeakHashMap<>();

  private PreflightSurvey() {}

  public static void request(ServerPlayer player, int sequence) {
    ServerLevel level = player.serverLevel();
    if (!level.getServer().isSameThread())
      throw new IllegalStateException("Preflight requests require the server thread");
    var window = REQUESTS.computeIfAbsent(player, ignored -> new PreflightRequestWindow());
    window.offer(sequence);
    var ready = window.take(level.getServer().overworld().getGameTime());
    if (ready.isPresent()) answer(player, ready.getAsInt());
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    long now = event.getServer().overworld().getGameTime();
    for (var entry : REQUESTS.entrySet()) {
      ServerPlayer player = entry.getKey();
      if (player.isRemoved() || player.getServer() != event.getServer()) continue;
      var ready = entry.getValue().take(now);
      if (ready.isPresent()) answer(player, ready.getAsInt());
    }
  }

  @SubscribeEvent
  public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    REQUESTS.remove(event.getEntity());
  }

  private static void answer(ServerPlayer player, int sequence) {
    ServerSubLevel body = aboardBody(player);
    if (body == null) {
      PacketDistributor.sendToPlayer(player, unavailable(player, sequence));
      return;
    }
    PacketDistributor.sendToPlayer(player, survey(body, sequence));
  }

  static PreflightNetwork.Snapshot survey(ServerSubLevel body, int sequence) {
    return new Survey(body).snapshot(sequence);
  }

  static @Nullable ServerSubLevel aboardBody(ServerPlayer player) {
    ServerLevel level = player.serverLevel();
    if (player.isSpectator()
        || !(Sable.HELPER.getTrackingOrVehicleSubLevel(player) instanceof ServerSubLevel body)
        || body.isRemoved()
        || body.getLevel() != level
        || !body.getTrackingPlayers().contains(player.getUUID())
        || !body.boundingBox()
            .toMojang()
            .inflate(8)
            .contains(Sable.HELPER.projectOutOfSubLevel(level, player.position()))) return null;
    return body;
  }

  private static PreflightNetwork.Snapshot unavailable(ServerPlayer player, int sequence) {
    return new PreflightNetwork.Snapshot(
        sequence,
        player.level().dimension().location(),
        new UUID(0, 0),
        "",
        List.of(),
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        false,
        true,
        false,
        List.of());
  }

  private static final class Survey {
    private final ServerSubLevel root;
    private final ServerLevel level;
    private final Map<UUID, ServerSubLevel> members = new LinkedHashMap<>();
    private final List<PreflightNetwork.Fuel> fuels = new ArrayList<>();
    private boolean partial;
    private boolean consistentGravity = true;
    private boolean leaking;
    private int balloons;
    private double mass;
    private double support;
    private double filled;
    private double target;
    private double capacity;
    private double change;

    private Survey(ServerSubLevel root) {
      this.root = root;
      level = root.getLevel();
    }

    private PreflightNetwork.Snapshot snapshot(int sequence) {
      connectedBodies();
      collectBalloons();
      collectBodies();
      collectFuel();
      if (!Double.isFinite(mass)
          || !Double.isFinite(support)
          || !Double.isFinite(filled)
          || !Double.isFinite(target)
          || !Double.isFinite(capacity)
          || !Double.isFinite(change)
          || mass <= 0
          || support < 0
          || filled < 0
          || target < 0
          || capacity < 0) partial = true;
      String name = root.getName() == null ? "" : root.getName();
      if (name.length() > 96) name = name.substring(0, 96);
      return new PreflightNetwork.Snapshot(
          sequence,
          level.dimension().location(),
          root.getUniqueId(),
          name,
          List.copyOf(members.keySet()),
          balloons,
          mass,
          support,
          filled,
          target,
          capacity,
          change,
          leaking,
          partial,
          consistentGravity,
          List.copyOf(fuels));
    }

    private void connectedBodies() {
      Map<UUID, Set<ServerSubLevel>> connections = new HashMap<>();
      int bodies = 0, actors = 0;
      var container = SubLevelContainer.getContainer(level);
      for (ServerSubLevel body : container.getAllSubLevels()) {
        if (++bodies > 128) {
          partial = true;
          break;
        }
        if (body.isRemoved()) continue;
        for (var actor : body.getPlot().getBlockEntityActors()) {
          if (++actors > 4096) {
            partial = true;
            break;
          }
          var dependencies = actor.sable$getConnectionDependencies();
          if (dependencies == null) continue;
          int links = 0;
          for (var dependency : dependencies) {
            if (++links > 64) {
              partial = true;
              break;
            }
            if (dependency instanceof ServerSubLevel linked
                && !linked.isRemoved()
                && linked.getLevel() == level) {
              connections.computeIfAbsent(body.getUniqueId(), id -> new HashSet<>()).add(linked);
              connections.computeIfAbsent(linked.getUniqueId(), id -> new HashSet<>()).add(body);
            }
          }
        }
        if (actors > 4096) break;
      }
      var pending = new ArrayDeque<ServerSubLevel>();
      pending.add(root);
      while (!pending.isEmpty()) {
        ServerSubLevel body = pending.removeFirst();
        if (members.containsKey(body.getUniqueId())) continue;
        if (members.size() == 64) {
          partial = true;
          break;
        }
        members.put(body.getUniqueId(), body);
        for (ServerSubLevel linked : connections.getOrDefault(body.getUniqueId(), Set.of()))
          if (!members.containsKey(linked.getUniqueId())) pending.addLast(linked);
      }
    }

    private void collectBalloons() {
      var gravity = DimensionPhysicsData.getGravity(level, root.logicalPose().position());
      if (!gravity.isFinite() || gravity.lengthSquared() < 1.0E-8) consistentGravity = false;
      int visited = 0;
      for (var candidate : BalloonMap.MAP.get(level).getBalloons()) {
        if (++visited > 4096) {
          partial = true;
          break;
        }
        if (!(candidate instanceof ServerBalloon balloon)
            || !(Sable.HELPER.getContaining(level, balloon.getControllerPos())
                instanceof ServerSubLevel body)
            || !members.containsKey(body.getUniqueId())) continue;
        if (!balloon.isValid() || balloon.isAssembling()) {
          partial = true;
          continue;
        }
        balloons++;
        var center = balloon.getCenter();
        var world =
            body.logicalPose().transformPosition(new Vector3d(center.x, center.y, center.z));
        double pressure = DimensionPhysicsData.getAirPressure(level, world);
        var localGravity = DimensionPhysicsData.getGravity(level, world);
        if (!localGravity.isFinite() || localGravity.distanceSquared(gravity) > 1.0E-8)
          consistentGravity = false;
        support += PreflightReadings.supportMass(balloon.getTotalLift(), pressure);
        filled += balloon.getTotalFilledVolume();
        target += balloon.getTotalTargetVolume();
        capacity += balloon.getCapacity();
        change += balloon.getTotalVolumeChange();
        if (balloon instanceof BalloonLeakAccess access) leaking |= access.aeroscapes$isLeaking();
        else partial = true;
        if (!Double.isFinite(pressure) || pressure < 0) partial = true;
      }
    }

    private void collectBodies() {
      var gravity = DimensionPhysicsData.getGravity(level, root.logicalPose().position());
      for (ServerSubLevel body : members.values()) {
        mass += body.getMassTracker().getMass();
        var bodyGravity = DimensionPhysicsData.getGravity(level, body.logicalPose().position());
        if (!bodyGravity.isFinite() || bodyGravity.distanceSquared(gravity) > 1.0E-8)
          consistentGravity = false;
      }
    }

    private void collectFuel() {
      int entities = 0, chunks = 0;
      for (ServerSubLevel body : members.values()) {
        for (var holder : body.getPlot().getLoadedChunks()) {
          if (++chunks > 1024) {
            partial = true;
            break;
          }
          for (var blockEntity : holder.getChunk().getBlockEntities().values()) {
            if (++entities > 4096) {
              partial = true;
              break;
            }
            if (blockEntity instanceof PortableEngineBlockEntity engine) {
              if (fuels.size() == PreflightNetwork.MAX_FUELS) {
                partial = true;
                continue;
              }
              var stack = engine.inventory.slot.getStack();
              fuels.add(
                  new PreflightNetwork.Fuel(
                      BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(),
                      Math.max(0, engine.getCurrentBurnTime()), engine.isCurrentFuelInfinite()));
            }
          }
          if (entities > 4096) break;
        }
        if (entities > 4096 || chunks > 1024) break;
      }
    }
  }
}
