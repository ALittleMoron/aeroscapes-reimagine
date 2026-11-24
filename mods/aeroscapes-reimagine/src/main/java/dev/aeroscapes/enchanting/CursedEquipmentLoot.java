package dev.aeroscapes.enchanting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class CursedEquipmentLoot extends LootModifier {
  private static final MapCodec<CursedEquipmentLoot> CODEC =
      RecordCodecBuilder.mapCodec(
          instance -> codecStart(instance).apply(instance, CursedEquipmentLoot::new));
  private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> TYPES =
      DeferredRegister.create(
          NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "aeroscapes_reimagine");

  static {
    TYPES.register("cursed_equipment", () -> CODEC);
  }

  private CursedEquipmentLoot(LootItemCondition[] conditions) {
    super(conditions);
  }

  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  @Override
  public MapCodec<CursedEquipmentLoot> codec() {
    return CODEC;
  }

  @Override
  protected ObjectArrayList<ItemStack> doApply(
      ObjectArrayList<ItemStack> loot, LootContext context) {
    var candidates = new ArrayList<Find>();
    var registries = context.getLevel().registryAccess();
    for (int index = 0; index < loot.size(); index++) {
      ItemStack item = loot.get(index);
      if (!EquipmentCurses.hasUsefulEnchantment(item)) continue;
      var compatible = EquipmentCurses.compatible(item, registries);
      if (!compatible.isEmpty()) candidates.add(new Find(index, compatible));
    }
    if (!candidates.isEmpty()) {
      var random = context.getRandom();
      var find = candidates.get(random.nextInt(candidates.size()));
      var result = loot.get(find.index()).copy();
      EquipmentCurses.apply(result, find.curses(), random);
      loot.set(find.index(), result);
    }
    return loot;
  }

  private record Find(int index, List<Holder<Enchantment>> curses) {}
}
