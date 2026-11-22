package dev.aeroscapes.expedition;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;

final class ExpeditionStudyLoot {
  private static final String POOL = "aeroscapes_reimagine:expedition_study";
  private static final Set<ResourceLocation> TREASURE =
      Set.of(
          ResourceLocation.parse("irons_spellbooks:chests/catacombs/dead_king_vault"),
          ResourceLocation.parse("irons_spellbooks:chests/citadel/citadel_vault"),
          ResourceLocation.parse("nova_structures:chests/lone_citadel/c_vault_boss"));
  private static final Set<ResourceLocation> BOSSES =
      Set.of(
          ResourceLocation.parse("irons_spellbooks:entities/dead_king"),
          ResourceLocation.parse("irons_spellbooks:entities/fire_boss"));

  private ExpeditionStudyLoot() {}

  static void register() {
    NeoForge.EVENT_BUS.addListener(ExpeditionStudyLoot::addStudyPool);
  }

  private static void addStudyPool(LootTableLoadEvent event) {
    // Iron's ominous fights already roll the base shared table before the ominous bonus.
    boolean boss = BOSSES.contains(event.getName());
    if (!boss && !TREASURE.contains(event.getName())) return;
    var table = event.getTable();
    if (table.getPool(POOL) != null) return;

    var pool =
        LootPool.lootPool()
            .name(POOL)
            .setRolls(ConstantValue.exactly(1))
            .when(LootItemRandomChanceCondition.randomChance(boss ? 0.12F : 0.04F))
            .add(LootItem.lootTableItem(ExpeditionDiscoveries.BREATH_SCROLL.get()))
            .add(LootItem.lootTableItem(ExpeditionDiscoveries.DISTRACTION_SCROLL.get()));
    if (boss) pool.when(LootItemKilledByPlayerCondition.killedByPlayer());
    table.addPool(pool.build());
  }
}
