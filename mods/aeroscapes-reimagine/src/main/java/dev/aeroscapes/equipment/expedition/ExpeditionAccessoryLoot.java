package dev.aeroscapes.equipment.expedition;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class ExpeditionAccessoryLoot {
  private ExpeditionAccessoryLoot() {}

  @SubscribeEvent
  public static void chest(LootTableLoadEvent event) {
    switch (event.getName().toString()) {
      case "irons_spellbooks:chests/evoker_fort" ->
          add(event, ExceptionalAccessory.INVISIBILITY, 0.05f);
      case "irons_spellbooks:chests/mountain_tower/ice_barrel" ->
          add(event, ExceptionalAccessory.FROSTWAVE, 0.04f);
      case "minecraft:chests/underwater_ruin_big" -> add(event, ExceptionalAccessory.BREATH, 0.04f);
      default -> {}
    }
  }

  private static void add(LootTableLoadEvent event, ExceptionalAccessory accessory, float chance) {
    event
        .getTable()
        .addPool(
            LootPool.lootPool()
                .name(accessory.id)
                .when(LootItemRandomChanceCondition.randomChance(chance))
                .add(
                    LootItem.lootTableItem(accessory.item())
                        .apply(
                            SetComponentsFunction.setComponent(
                                DataComponents.CUSTOM_DATA,
                                CustomData.of(ExceptionalAccessory.mark()))))
                .build());
  }
}
