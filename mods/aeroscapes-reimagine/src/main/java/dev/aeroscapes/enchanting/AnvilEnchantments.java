package dev.aeroscapes.enchanting;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.fml.ModList;

public final class AnvilEnchantments {
  private static final ResourceLocation ANCIENT_TOME =
      ResourceLocation.fromNamespaceAndPath("quark", "ancient_tome");
  private static final ResourceLocation ANCIENT_BOOK =
      ResourceLocation.fromNamespaceAndPath("immersiveenchanting", "ancient_book");

  private AnvilEnchantments() {}

  public static boolean rejectBookMerge(AnvilMenu menu) {
    if (!isBook(menu.getSlot(0).getItem()) || !isBook(menu.getSlot(1).getItem())) return false;
    clearResult(menu);
    return true;
  }

  public static void enforceProgression(AnvilMenu menu, Player player) {
    if (!allowsOutput(menu, player)) clearResult(menu);
  }

  public static boolean allowsOutput(AnvilMenu menu, Player player) {
    return menu.getSlot(1).getItem().isEmpty()
        || !violatesProgression(menu.getSlot(0).getItem(), menu.getSlot(2).getItem(), player);
  }

  private static void clearResult(AnvilMenu menu) {
    menu.getSlot(2).set(ItemStack.EMPTY);
    menu.setMaximumCost(0);
    menu.repairItemCountCost = 0;
    menu.broadcastChanges();
  }

  private static boolean isBook(ItemStack stack) {
    return !stack.isEmpty()
        && (stack.has(DataComponents.STORED_ENCHANTMENTS)
            || stack.getItemHolder().is(ANCIENT_TOME)
            || stack.getItemHolder().is(ANCIENT_BOOK));
  }

  private static boolean violatesProgression(ItemStack left, ItemStack output, Player player) {
    var existing = EnchantmentHelper.getEnchantmentsForCrafting(left);
    boolean usesLibrary = ModList.get().isLoaded("immersiveenchanting");
    for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(output).entrySet()) {
      var enchantment = entry.getKey();
      int outputLevel = entry.getIntValue();
      int existingLevel = existing.getLevel(enchantment);
      int maximum = enchantment.value().getMaxLevel();
      if (existingLevel > maximum && outputLevel < existingLevel) return true;
      if (outputLevel <= existingLevel) continue;
      if (outputLevel > maximum) return true;
      if (!usesLibrary || player.hasInfiniteMaterials() || !LibraryBooks.playerEffect(enchantment))
        continue;
      if (EnchantingMastery.rank(player) < LibraryBooks.requiredMastery(enchantment, outputLevel))
        return true;
      if (LibraryBooks.advanced(enchantment, outputLevel)
          && !LibraryBooks.carriesKnowledge(player, enchantment, outputLevel)) return true;
    }
    return false;
  }
}
