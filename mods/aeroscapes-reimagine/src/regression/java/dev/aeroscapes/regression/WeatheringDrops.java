package dev.aeroscapes.regression;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

final class WeatheringDrops {
  private WeatheringDrops() {}

  static int spawnedBy(ServerLevel level, BlockPos pos, Item item, Runnable interact) {
    List<ItemEntity> drops = new ArrayList<>();
    // At ServerStartedEvent a chunk can accept an entity before its section is visible to queries.
    Consumer<EntityJoinLevelEvent> observer =
        event -> {
          if (event.getLevel() == level
              && event.getEntity() instanceof ItemEntity drop
              && drop.blockPosition().distManhattan(pos) <= 2
              && drop.getItem().is(item)) {
            drops.add(drop);
          }
        };
    NeoForge.EVENT_BUS.addListener(observer);
    try {
      interact.run();
      return drops.stream().mapToInt(drop -> drop.getItem().getCount()).sum();
    } finally {
      NeoForge.EVENT_BUS.unregister(observer);
      drops.forEach(ItemEntity::discard);
    }
  }
}
