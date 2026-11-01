package dev.aeroscapes.atmosphere;

import io.github.mortuusars.horseman.world.summoning.BoundData;
import io.github.mortuusars.horseman.world.summoning.StoredBoundHorse;
import io.github.mortuusars.horseman.world.summoning.SummonableHorse;
import io.github.mortuusars.horseman.world.summoning.Summoning;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

/** Keeps native instrument aliases on one current horse snapshot, including UUID replacement. */
public final class HorseRecallSnapshots {
  private HorseRecallSnapshots() {}

  public static void synchronize(Summoning summoning, AbstractHorse horse, UUID previousUuid) {
    var data = ((SummonableHorse) horse).getHorsemanBoundData();
    if (data == null) return;
    StoredBoundHorse current = summoning.getBoundHorse(data.owner(), data.instrument());
    if (current == null) return;
    summoning
        .getStoredHorsesOf(data.owner())
        .replaceAll(
            (instrument, stored) -> {
              if (!stored.getEntityUuid().equals(previousUuid)
                  && !stored.getEntityUuid().equals(current.getEntityUuid())) return stored;
              // Independent tags prevent one native load operation from mutating another alias's
              // snapshot.
              return new StoredBoundHorse(
                  new BoundData(data.owner(), instrument),
                  current.getTag().copy(),
                  current.getEntityUuid(),
                  horse.position(),
                  horse.level().dimension(),
                  current.isDead());
            });
    summoning.getStorage().setDirty();
  }

  public static void preserveRemainingBinding(
      Summoning summoning, ServerLevel level, StoredBoundHorse removed) {
    var data = removed.getBoundData();
    if (data == null) return;
    var remaining =
        summoning.getStoredHorsesOf(data.owner()).entrySet().stream()
            .filter(entry -> entry.getValue().getEntityUuid().equals(removed.getEntityUuid()))
            .findFirst();
    if (remaining.isEmpty()) return;
    var active = new BoundData(data.owner(), remaining.get().getKey());
    summoning.getStorage().getUnboundHorses().removeIf(removed.getEntityUuid()::equals);
    summoning.getStoredHorsesOf(data.owner()).values().stream()
        .filter(stored -> stored.getEntityUuid().equals(removed.getEntityUuid()))
        .forEach(stored -> active.save(stored.getTag()));
    summoning.getStorage().setDirty();
    for (ServerLevel dimension : level.getServer().getAllLevels()) {
      if (dimension.getEntity(removed.getEntityUuid()) instanceof AbstractHorse horse) {
        ((SummonableHorse) horse).setHorsemanBoundData(active);
        // This native method persists the current horse snapshot without unloading the entity.
        summoning.onHorseUnloaded(dimension, horse);
        return;
      }
    }
  }

  public static void restoreActiveAlias(Summoning summoning, AbstractHorse horse) {
    var current = ((SummonableHorse) horse).getHorsemanBoundData();
    if (current == null) return;
    var records = summoning.getStoredHorsesOf(current.owner());
    var active = records.get(current.instrument());
    if (active != null && active.getEntityUuid().equals(horse.getUUID())) return;
    records.entrySet().stream()
        .filter(entry -> entry.getValue().getEntityUuid().equals(horse.getUUID()))
        .findFirst()
        .ifPresent(
            entry ->
                ((SummonableHorse) horse)
                    .setHorsemanBoundData(new BoundData(current.owner(), entry.getKey())));
  }
}
