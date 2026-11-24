package dev.aeroscapes.enchanting;

import java.util.Map;
import me.alfie.immersiveenchanting.datapack.enchantment_cost.CostRegistry;
import me.alfie.immersiveenchanting.datapack.enchantment_cost.codec.Cost;
import me.alfie.immersiveenchanting.datapack.enchantment_cost.codec.CostHolder;
import me.alfie.immersiveenchanting.gui.EnchantingTableMenu;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public final class LibraryEnchanting {
  private LibraryEnchanting() {}

  public static Component restriction(
      Map<ResourceLocation, Integer> ceilings, int rank, Holder<Enchantment> effect, int level) {
    if (!LibraryBooks.playerEffect(effect) || level < 1 || level > effect.value().getMaxLevel())
      return Component.translatable("aeroscapes_reimagine.enchanting.unavailable");
    int ceiling = ceilings.getOrDefault(effect.unwrapKey().orElseThrow().location(), 0);
    if (ceiling < level)
      return Component.translatable(
          "aeroscapes_reimagine.enchanting.library_needed", level, ceiling);
    int required = LibraryBooks.requiredMastery(effect, level);
    if (rank < required)
      return Component.translatable("aeroscapes_reimagine.enchanting.mastery_needed", required);
    return null;
  }

  public static boolean canApply(
      EnchantingTableMenu menu, Holder<Enchantment> effect, int level, Player player) {
    if (!usable(menu, player)) return false;
    ItemStack input = menu.getToolSlot().getItem();
    var effects = EnchantmentHelper.getEnchantmentsForCrafting(input);
    if (input.isEmpty()
        || input.is(Items.BOOK)
        || input.is(Items.ENCHANTED_BOOK)
        || LibraryBooks.isAncient(input)
        || !input.supportsEnchantment(effect)
        || level != effects.getLevel(effect) + 1
        || !EnchantmentHelper.isEnchantmentCompatible(effects.keySet(), effect)
            && effects.getLevel(effect) == 0) return false;
    CostRegistry costs = CostRegistry.server();
    if (!LibraryBooks.available(costs, effect)) return false;
    if (!player.hasInfiniteMaterials()) {
      Component blocked =
          restriction(
              LibraryBooks.ceilings(player.level(), menu.getBlockPos()),
              EnchantingMastery.rank(player),
              effect,
              level);
      if (blocked != null) {
        player.displayClientMessage(blocked, true);
        return false;
      }
    } else if (level > effect.value().getMaxLevel()) return false;

    return pay(
        menu,
        player,
        costs.get(effect).levelCosts().getLevel(level),
        costs.get(CostRegistry.ENCHANTING_FUELS).levelCosts().getLevel(level),
        true);
  }

  public static boolean canCopy(EnchantingTableMenu menu, Player player) {
    if (!usable(menu, player)) return false;
    ItemStack source = menu.getToolSlot().getItem();
    if (source.getCount() != 1 || !LibraryBooks.isKnowledgeBook(source)) {
      player.displayClientMessage(
          Component.translatable("aeroscapes_reimagine.enchanting.copy_single"), true);
      return false;
    }
    CostRegistry costs = CostRegistry.server();
    var prices = costs.get(CostRegistry.REPLICATE);
    return prices.enabled()
        && pay(
            menu,
            player,
            prices.levelCosts().getLevel(1),
            costs.get(CostRegistry.ENCHANTING_FUELS).levelCosts().getLevel(1),
            false);
  }

  private static boolean usable(EnchantingTableMenu menu, Player player) {
    return player instanceof ServerPlayer
        && player.containerMenu == menu
        && player.isAlive()
        && !player.isSpectator()
        && menu.stillValid(player);
  }

  private static boolean pay(
      EnchantingTableMenu menu,
      Player player,
      CostHolder prices,
      CostHolder fuels,
      boolean discounted) {
    if (prices.costs().isEmpty() || fuels.costs().isEmpty()) return false;
    if (player.hasInfiniteMaterials()) return true;
    ItemStack material = menu.getCostSlot().getItem();
    ItemStack fuel = menu.getFuelSlot().getItem();
    for (Cost price : prices.costs()) {
      if (!matches(price, material)) continue;
      int levels =
          discounted
              ? EnchantingMastery.discountedLevels(player, price.xpLevels())
              : price.xpLevels();
      for (Cost fuelPrice : fuels.costs()) {
        if (!matches(fuelPrice, fuel) || player.experienceLevel < levels + fuelPrice.xpLevels())
          continue;
        price.itemCost().tryConsume(material);
        fuelPrice.itemCost().tryConsume(fuel);
        player.giveExperienceLevels(-levels - fuelPrice.xpLevels());
        menu.getCostSlot().setChanged();
        menu.getFuelSlot().setChanged();
        return true;
      }
    }
    player.displayClientMessage(
        Component.translatable("aeroscapes_reimagine.enchanting.payment"), true);
    return false;
  }

  private static boolean matches(Cost cost, ItemStack stack) {
    return cost.itemCost().isValid(stack) || cost.itemCost().getItems().contains(Items.AIR);
  }
}
