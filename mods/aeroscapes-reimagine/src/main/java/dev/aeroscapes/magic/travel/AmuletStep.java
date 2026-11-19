package dev.aeroscapes.magic.travel;

import io.redspace.ironsspellbooks.api.events.SpellTeleportEvent;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import top.theillusivec4.curios.api.CuriosApi;

public final class AmuletStep {
  public static final String ID = "aeroscapes_reimagine:amulet_step";
  public static final String GROUP = "aeroscapes_reimagine:equipment";
  private static final String DEADLINE = "amulet_step_ready_at";
  private static final int RANGE = 16;
  private static final int COOLDOWN = 20 * 20;

  private AmuletStep() {}

  public static boolean available(ServerPlayer player) {
    return player.getAttributeValue(AttributeRegistry.ENDER_SPELL_POWER) > 1.25
        && CuriosApi.getCuriosInventory(player)
            .flatMap(
                handler ->
                    handler.findFirstCurio(
                        stack -> stack.is(ItemRegistry.TELEPORTATION_AMULET.get())))
            .isPresent();
  }

  public static int cooldown(ServerPlayer player) {
    return TravelMagic.remaining(player, DEADLINE);
  }

  public static void cast(ServerPlayer player) {
    if (!available(player)) {
      feedback(player, "amulet_required");
      return;
    }
    if (cooldown(player) > 0) {
      feedback(player, "reason.cooldown");
      return;
    }
    if (!player.isAlive() || player.isSpectator() || player.isPassenger() || !player.onGround()) {
      feedback(player, "amulet_ground");
      return;
    }
    var start = player.position();
    var landing = destination(player);
    if (landing == null) {
      feedback(player, "amulet_ground");
      return;
    }
    var teleport =
        new SpellTeleportEvent(
            SpellRegistry.TELEPORT_SPELL.get(), player, landing.x, landing.y, landing.z);
    if (NeoForge.EVENT_BUS.post(teleport).isCanceled()) return;
    // Quark's myalite conduits redirect teleport events. Only this supported path was checked.
    player.teleportTo(landing.x, landing.y, landing.z);
    TravelMagic.startCooldown(player, DEADLINE, COOLDOWN);
    var level = player.serverLevel();
    for (var point : java.util.List.of(start, player.position()))
      level.playSound(
          null, point.x, point.y, point.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS);
  }

  private static void feedback(ServerPlayer player, String key) {
    player.displayClientMessage(
        Component.translatable("aeroscapes_reimagine.abilities." + key), true);
  }

  @Nullable
  private static Vec3 destination(ServerPlayer player) {
    ServerLevel level = player.serverLevel();
    Vec3 start = player.position();
    Vec3 eye = player.getEyePosition();
    Vec3 look = player.getLookAngle();
    AABB bounds = player.getBoundingBox();
    Vec3 last = start;
    AABB previous = bounds;
    // Trace the whole body and supporting surface, stopping at the first wall or gap.
    for (int step = 1; step <= RANGE * 4; step++) {
      var gaze = eye.add(look.scale(step * 0.25));
      var aim = new Vec3(gaze.x, last.y, gaze.z);
      if (level
              .getChunkSource()
              .getChunkNow(
                  SectionPos.blockToSectionCoord(aim.x), SectionPos.blockToSectionCoord(aim.z))
          == null) break;
      var hit =
          level.clip(
              new ClipContext(
                  aim.add(0, 0.55, 0),
                  aim.add(0, -0.55, 0),
                  ClipContext.Block.COLLIDER,
                  ClipContext.Fluid.NONE,
                  player));
      if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) break;
      var support = level.getBlockState(hit.getBlockPos());
      if (player.getType().isBlockDangerous(support) || !support.getFluidState().isEmpty()) break;
      Vec3 feet = hit.getLocation();
      if (gaze.y < feet.y || feet.distanceToSqr(start) > RANGE * RANGE) break;
      var next = bounds.move(feet.subtract(start));
      double rise = next.minY - previous.minY;
      var span = previous.move(0, Math.max(0, rise), 0).minmax(next.move(0, Math.max(0, -rise), 0));
      if (!clear(player, span) || (rise < 0 && !clear(player, next))) break;
      last = feet;
      previous = next;
    }
    return start.distanceToSqr(last) >= 1 ? last : null;
  }

  private static boolean clear(ServerPlayer player, AABB bounds) {
    var level = player.serverLevel();
    var border = level.getWorldBorder();
    return bounds.minY >= level.getMinBuildHeight()
        && bounds.maxY <= level.getMaxBuildHeight()
        && bounds.minX >= border.getMinX()
        && bounds.maxX <= border.getMaxX()
        && bounds.minZ >= border.getMinZ()
        && bounds.maxZ <= border.getMaxZ()
        && loaded(level, bounds)
        && level.noCollision(player, bounds)
        && level
            .getBlockStates(bounds.deflate(1.0e-7))
            .noneMatch(
                state ->
                    !state.getFluidState().isEmpty() || player.getType().isBlockDangerous(state));
  }

  private static boolean loaded(ServerLevel level, AABB bounds) {
    for (int x = SectionPos.blockToSectionCoord(bounds.minX);
        x <= SectionPos.blockToSectionCoord(bounds.maxX);
        x++)
      for (int z = SectionPos.blockToSectionCoord(bounds.minZ);
          z <= SectionPos.blockToSectionCoord(bounds.maxZ);
          z++) if (level.getChunkSource().getChunkNow(x, z) == null) return false;
    return true;
  }
}
