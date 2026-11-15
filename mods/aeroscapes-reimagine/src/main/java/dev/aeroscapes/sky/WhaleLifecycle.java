package dev.aeroscapes.sky;

import dev.dacite.skywhales.config.SWConfig;
import dev.dacite.skywhales.entity.SkyWhale;

/** Distant encounters end after two ticking minutes; ordinary chunk unloading still saves them. */
public final class WhaleLifecycle {
  public static final int DISTANT_GRACE_TICKS = 2400;
  private static final String DISTANT_TICKS = "aeroscapesWhaleDistantTicks";

  private WhaleLifecycle() {}

  public static void checkDespawn(SkyWhale whale) {
    var data = whale.getPersistentData();
    if (whale.isPersistenceRequired()
        || whale.requiresCustomPersistence()
        || !SWConfig.despawnEnabled()) {
      data.remove(DISTANT_TICKS);
      return;
    }
    var nearest = whale.level().getNearestPlayer(whale, -1);
    if (nearest == null) return;
    double distance = SWConfig.immediateDespawnDistance();
    if (nearest.distanceToSqr(whale) <= distance * distance) {
      data.remove(DISTANT_TICKS);
      return;
    }
    int ticks = Math.clamp(data.getInt(DISTANT_TICKS), 0, DISTANT_GRACE_TICKS) + 1;
    if (ticks >= DISTANT_GRACE_TICKS) whale.discard();
    else data.putInt(DISTANT_TICKS, ticks);
  }
}
