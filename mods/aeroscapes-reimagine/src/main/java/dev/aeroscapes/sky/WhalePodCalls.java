package dev.aeroscapes.sky;

import dev.dacite.skywhales.entity.SkyWhale;
import dev.dacite.skywhales.registry.SWSounds;
import dev.ryanhcode.sable.Sable;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** Up to two replies to a pod leader's ordinary call, spaced so each voice can be heard. */
public final class WhalePodCalls {
  private static final int REPLY_DELAY = 100;
  private static final int COOLDOWN = 1800;
  private static final double POD_RANGE = 64;
  private static final double LISTENER_RANGE = 96;

  private final Deque<UUID> replies = new ArrayDeque<>(2);
  private long nextReply;
  private long nextExchange;

  public void afterAmbientCall(SkyWhale leader, ServerLevel level) {
    long time = level.getGameTime();
    if (time < nextExchange
        || !readyToCall(leader)
        || leader.getPersistentData().hasUUID(WhalePods.LEADER)
        || !hasListener(level, leader)) return;
    level
        .getEntitiesOfClass(
            SkyWhale.class,
            leader.getBoundingBox().inflate(POD_RANGE),
            whale -> canAnswer(level, leader, whale))
        .stream()
        .sorted(Comparator.comparingDouble(leader::distanceToSqr))
        .limit(2)
        .map(SkyWhale::getUUID)
        .forEach(replies::addLast);
    if (!replies.isEmpty()) {
      nextReply = time + REPLY_DELAY;
      nextExchange = time + COOLDOWN;
    }
  }

  public void tick(SkyWhale leader, ServerLevel level) {
    if (replies.isEmpty()) return;
    if (!readyToCall(leader)) {
      replies.clear();
      return;
    }
    long time = level.getGameTime();
    if (time < nextReply) return;
    nextReply = time + REPLY_DELAY;
    if (level.getEntity(replies.removeFirst()) instanceof SkyWhale whale
        && canAnswer(level, leader, whale)
        && hasListener(level, whale)) {
      whale.playSound(SWSounds.WHALE_AMBIENT.get(), WhaleEncounters.VOICE_VOLUME, 1.05F);
      whale.ambientSoundTime = -whale.getAmbientSoundInterval();
    }
  }

  private static boolean canAnswer(ServerLevel level, SkyWhale leader, SkyWhale whale) {
    return readyToCall(whale)
        && whale.distanceToSqr(leader) <= POD_RANGE * POD_RANGE
        && WhalePods.leader(level, whale) == leader;
  }

  private static boolean hasListener(ServerLevel level, SkyWhale whale) {
    for (var player : level.players()) {
      if (player.isAlive()
          && !player.isSpectator()
          && Sable.HELPER
                  .projectOutOfSubLevel(level, player.position())
                  .distanceToSqr(whale.position())
              <= LISTENER_RANGE * LISTENER_RANGE) return true;
    }
    return false;
  }

  private static boolean readyToCall(SkyWhale whale) {
    return whale.isAlive()
        && !whale.isSilent()
        && !whale.isNoAi()
        && whale.getTarget() == null
        && !whale.isAngry()
        && !whale.isChargingSonicBoom();
  }
}
