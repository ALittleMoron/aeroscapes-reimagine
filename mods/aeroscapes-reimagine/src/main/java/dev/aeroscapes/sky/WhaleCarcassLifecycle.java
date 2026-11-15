package dev.aeroscapes.sky;

import dev.dacite.skywhales.corpse.WhaleCorpseSpawner;
import dev.dacite.skywhales.corpse.WhaleCorpseTracker;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class WhaleCarcassLifecycle {
  private WhaleCarcassLifecycle() {}

  @SubscribeEvent
  public static void onContainerReady(ForgeSableSubLevelContainerReadyEvent event) {
    if (event.getLevel() instanceof ServerLevel level) {
      event.getContainer().addObserver(new TrackerCleanup(level));
    }
  }

  private record TrackerCleanup(ServerLevel level) implements SubLevelObserver {
    @Override
    public void onSubLevelRemoved(SubLevel sublevel, SubLevelRemovalReason reason) {
      // Chunk unloading keeps the saved decay age; only permanent removal forgets it.
      if (reason == SubLevelRemovalReason.REMOVED
          && WhaleCorpseSpawner.CORPSE_NAME.equals(sublevel.getName())) {
        WhaleCorpseTracker.get(level).untrack(sublevel.getUniqueId());
      }
    }
  }
}
