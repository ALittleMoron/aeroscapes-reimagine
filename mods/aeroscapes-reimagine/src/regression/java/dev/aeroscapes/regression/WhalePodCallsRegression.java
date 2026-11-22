package dev.aeroscapes.regression;

import dev.aeroscapes.sky.WhaleEncounters;
import dev.aeroscapes.sky.WhalePods;
import dev.dacite.skywhales.entity.SkyWhale;
import dev.dacite.skywhales.registry.SWEntities;
import dev.dacite.skywhales.registry.SWSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

final class WhalePodCallsRegression {
  private WhalePodCallsRegression() {}

  static void verify(ServerLevel level) {
    Vec3 position = Vec3.atCenterOf(level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 35));
    var leader = spawn(level, position);
    var nearer = spawn(level, position.add(29, 0, 0));
    var farther = spawn(level, position.add(-38, 0, 0));
    var unrelated = spawn(level, position.add(0, 0, -30));
    nearer.getPersistentData().putUUID(WhalePods.LEADER, leader.getUUID());
    farther.getPersistentData().putUUID(WhalePods.LEADER, leader.getUUID());

    long originalTime = level.getGameTime();
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "PodListener", position.add(0, -8, 55), null)) {
      session.player.setGameMode(GameType.SURVIVAL);
      var played = new ArrayList<PlayLevelSoundEvent.AtPosition>();
      Consumer<PlayLevelSoundEvent.AtPosition> capture =
          sound -> {
            if (sound.getSound() != null
                && sound.getSound().value() == SWSounds.WHALE_AMBIENT.get()) {
              played.add(sound);
            }
          };
      NeoForge.EVENT_BUS.addListener(PlayLevelSoundEvent.AtPosition.class, capture);
      try {
        verifySequence(level, leader, nearer, farther, played);
        leader.playAmbientSound();
        played.clear();
        tickAt(level, leader, level.getGameTime() + 200);
        require(played.isEmpty(), "Another call during the cooldown started a second exchange");
        setTime(level, originalTime + 2000);
        leader.playAmbientSound();
        played.clear();
        leader.setTarget(session.player);
        tickAt(level, leader, level.getGameTime() + 240);
        require(played.isEmpty(), "An active target did not interrupt the exchange");
      } finally {
        NeoForge.EVENT_BUS.unregister(capture);
      }
    } finally {
      setTime(level, originalTime);
      for (var whale : List.of(leader, nearer, farther, unrelated)) whale.discard();
    }
  }

  private static void verifySequence(
      ServerLevel level,
      SkyWhale leader,
      SkyWhale nearer,
      SkyWhale farther,
      List<PlayLevelSoundEvent.AtPosition> played) {
    long time = level.getGameTime();
    leader.playAmbientSound();
    require(played.size() == 1 && from(played.getFirst(), leader), "Missing native leader call");
    tickAt(level, leader, time + 99);
    require(played.size() == 1, "A companion answered too early");
    tickAt(level, leader, time + 100);
    require(played.size() == 2 && from(played.getLast(), nearer), "Missing first pod reply");
    tickAt(level, leader, time + 200);
    require(played.size() == 3 && from(played.getLast(), farther), "Missing second pod reply");
    tickAt(level, leader, time + 300);
    require(played.size() == 3, "An unrelated whale joined the exchange");
  }

  private static boolean from(PlayLevelSoundEvent.AtPosition event, SkyWhale whale) {
    return event.getPosition().distanceToSqr(whale.position()) < 25
        && event.getOriginalVolume() == WhaleEncounters.VOICE_VOLUME;
  }

  private static SkyWhale spawn(ServerLevel level, Vec3 position) {
    var whale = Objects.requireNonNull(SWEntities.SKY_WHALE.get().create(level));
    whale.moveTo(position.x, position.y, position.z);
    whale.ambientSoundTime = -1000;
    level.getChunkAt(whale.blockPosition());
    require(level.addFreshEntity(whale), "Could not add pod fixture whale");
    return whale;
  }

  private static void tickAt(ServerLevel level, SkyWhale whale, long time) {
    setTime(level, time);
    level.tickNonPassenger(whale);
  }

  private static void setTime(ServerLevel level, long time) {
    level.getServer().getWorldData().overworldData().setGameTime(time);
  }

  private static void require(boolean condition, String reason) {
    if (!condition) throw new IllegalStateException(reason);
  }
}
