package dev.aeroscapes.enchanting;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import me.alfie.immersiveenchanting.config.ServerConfig;
import me.alfie.immersiveenchanting.datapack.enchantment_cost.CostRegistry;
import me.alfie.immersiveenchanting.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;

public final class LibraryBooks {
  private static final Set<String> FINAL_AT_ANY_LEVEL =
      Set.of("minecraft:mending", "allurement:reforming", "nova_structures:photosynthesis");
  private static final Set<String> ADVANCED_EFFECTS =
      Set.of(
          "minecraft:fortune",
          "minecraft:infinity",
          "minecraft:looting",
          "minecraft:silk_touch",
          "minecraft:soul_speed",
          "minecraft:swift_sneak",
          "minecraft:wind_burst",
          "allurement:alleviating",
          "betterarcheology:penetrating_strike",
          "betterarcheology:soaring_winds",
          "betterarcheology:tunneling");
  private static final Set<String> INTERNAL_EFFECTS =
      Set.of(
          "nova_structures:boss_behaviour",
          "nova_structures:shulker_boss",
          "nova_structures:shulker_miniboss");

  private LibraryBooks() {}

  public static boolean playerEffect(Holder<Enchantment> effect) {
    return !effect.is(EnchantmentTags.CURSE)
        && !INTERNAL_EFFECTS.contains(effect.getRegisteredName());
  }

  public static int requiredMastery(Holder<Enchantment> effect, int level) {
    String id = effect.getRegisteredName();
    if (FINAL_AT_ANY_LEVEL.contains(id)) return 4;
    if (ADVANCED_EFFECTS.contains(id)) return level == effect.value().getMaxLevel() ? 4 : 2;
    return level - 1;
  }

  public static boolean advanced(Holder<Enchantment> effect, int level) {
    return level > 2 || requiredMastery(effect, level) >= 2;
  }

  public static int ordinaryCeiling(Holder<Enchantment> effect, int level) {
    if (!playerEffect(effect)) return 0;
    int ceiling = Math.min(2, Math.min(level, effect.value().getMaxLevel()));
    return advanced(effect, ceiling) ? 0 : ceiling;
  }

  public static boolean isAncient(ItemStack stack) {
    return stack.is(ModItems.ANCIENT_BOOK.get());
  }

  public static boolean isKnowledgeBook(ItemStack stack) {
    var effects = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
    if (!isAncient(stack) || effects.size() != 1) return false;
    var effect = effects.keySet().iterator().next();
    return playerEffect(effect) && effects.getLevel(effect) <= effect.value().getMaxLevel();
  }

  public static ItemStack ancient(Holder<Enchantment> effect, int level) {
    ItemStack book = new ItemStack(ModItems.ANCIENT_BOOK.get());
    var effects = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
    effects.set(effect, Math.min(level, effect.value().getMaxLevel()));
    book.set(DataComponents.STORED_ENCHANTMENTS, effects.toImmutable());
    return book;
  }

  static boolean carriesKnowledge(Player player, Holder<Enchantment> effect, int level) {
    var inventory = player.getInventory();
    for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
      var book = inventory.getItem(slot);
      if (isKnowledgeBook(book)
          && book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                  .getLevel(effect)
              >= level) return true;
    }
    return false;
  }

  public static Map<ResourceLocation, Integer> ceilings(Level level, BlockPos table) {
    Map<ResourceLocation, Integer> result = new HashMap<>();
    var radius = ServerConfig.getBookshelfSearchRadius();
    for (BlockPos pos :
        BlockPos.betweenClosed(
            table.offset(-radius.x(), 0, -radius.z()),
            table.offset(radius.x(), radius.y(), radius.z()))) {
      if (Math.abs(pos.getX() - table.getX()) < 2 && Math.abs(pos.getZ() - table.getZ()) < 2)
        continue;
      if (level
              .getChunkSource()
              .hasChunk(
                  SectionPos.blockToSectionCoord(pos.getX()),
                  SectionPos.blockToSectionCoord(pos.getZ()))
          && level.getBlockEntity(pos) instanceof ChiseledBookShelfBlockEntity shelf) {
        for (int slot = 0; slot < shelf.getContainerSize(); slot++)
          addBook(result, shelf.getItem(slot));
      }
    }
    return Map.copyOf(result);
  }

  private static void addBook(Map<ResourceLocation, Integer> result, ItemStack stack) {
    if (!isAncient(stack) && !stack.is(Items.ENCHANTED_BOOK)) return;
    var effects = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
    for (var entry : effects.entrySet()) {
      Holder<Enchantment> effect = entry.getKey();
      if (!playerEffect(effect)) continue;
      int ceiling =
          isAncient(stack)
              ? Math.min(entry.getIntValue(), effect.value().getMaxLevel())
              : ordinaryCeiling(effect, entry.getIntValue());
      if (ceiling > 0)
        result.merge(effect.unwrapKey().orElseThrow().location(), ceiling, Math::max);
    }
  }

  public static boolean available(CostRegistry costs, Holder<Enchantment> effect) {
    return playerEffect(effect) && costs.isRegistered(effect) && costs.get(effect).enabled();
  }
}
