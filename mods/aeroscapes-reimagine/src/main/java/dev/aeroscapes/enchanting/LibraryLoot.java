package dev.aeroscapes.enchanting;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.alfie.immersiveenchanting.datapack.enchantment_cost.CostRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class LibraryLoot extends LootModifier {
  private static final Map<String, Float> EXPEDITION_CHANCES =
      Map.of(
          "minecraft:chests/stronghold_library", 0.65F,
          "minecraft:chests/ancient_city", 0.15F,
          "minecraft:chests/trial_chambers/reward_ominous", 0.4F,
          "irons_spellbooks:chests/catacombs/dead_king_vault", 0.75F,
          "irons_spellbooks:chests/citadel/citadel_vault", 0.75F,
          "nova_structures:chests/lone_citadel/c_vault_boss", 0.75F,
          "nova_structures:chests/toxic_lair/toxic_ominous_vault", 0.50F);
  private static final MapCodec<LibraryLoot> CODEC =
      RecordCodecBuilder.mapCodec(
          instance -> codecStart(instance).apply(instance, LibraryLoot::new));

  private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> TYPES =
      DeferredRegister.create(
          NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "aeroscapes_reimagine");

  static {
    TYPES.register("library_books", () -> CODEC);
  }

  private LibraryLoot(LootItemCondition[] conditions) {
    super(conditions);
  }

  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  @Override
  public MapCodec<LibraryLoot> codec() {
    return CODEC;
  }

  @Override
  protected ObjectArrayList<ItemStack> doApply(
      ObjectArrayList<ItemStack> loot, LootContext context) {
    float chance = bookChance(context.getQueriedLootTableId());
    if (chance == 0) return loot;
    CostRegistry costs = CostRegistry.server();
    ObjectArrayList<ItemStack> result = new ObjectArrayList<>(loot.size() + 1);
    for (ItemStack stack : loot) {
      if (stack.is(Items.ENCHANTED_BOOK)) convert(stack, result, costs);
      else result.add(stack);
    }
    if (context.getRandom().nextFloat() < chance) {
      List<Discovery> pool = new ArrayList<>();
      costs.getAllEnabledEnchantmentHolders().stream()
          .filter(LibraryBooks::playerEffect)
          .sorted(java.util.Comparator.comparing(Holder::getRegisteredName))
          .forEach(
              effect -> {
                for (int level = 1; level <= effect.value().getMaxLevel(); level++) {
                  if (LibraryBooks.advanced(effect, level)) pool.add(new Discovery(effect, level));
                }
              });
      if (!pool.isEmpty()) {
        Discovery book = pool.get(context.getRandom().nextInt(pool.size()));
        result.add(LibraryBooks.ancient(book.effect(), book.level()));
      }
    }
    return result;
  }

  public static float bookChance(ResourceLocation table) {
    return EXPEDITION_CHANCES.getOrDefault(table.toString(), 0F);
  }

  private static void convert(
      ItemStack original, ObjectArrayList<ItemStack> result, CostRegistry costs) {
    var effects = original.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
    var convertible =
        effects.entrySet().stream()
            .filter(entry -> LibraryBooks.available(costs, entry.getKey()))
            .toList();
    if (convertible.isEmpty() || original.has(DataComponents.CUSTOM_NAME)) {
      result.add(original);
      return;
    }
    for (var entry : convertible) {
      ItemStack book = LibraryBooks.ancient(entry.getKey(), entry.getIntValue());
      book.setCount(original.getCount());
      result.add(book);
    }
    var remainder = new ItemEnchantments.Mutable(effects);
    remainder.removeIf(effect -> LibraryBooks.available(costs, effect));
    var stored = remainder.toImmutable();
    if (!stored.isEmpty()) {
      ItemStack book = original.copy();
      book.set(DataComponents.STORED_ENCHANTMENTS, stored);
      result.add(book);
    }
  }

  private record Discovery(Holder<Enchantment> effect, int level) {}
}
