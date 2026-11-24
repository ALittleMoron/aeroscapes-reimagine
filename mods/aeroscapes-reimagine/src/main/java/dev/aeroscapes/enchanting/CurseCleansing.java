package dev.aeroscapes.enchanting;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class CurseCleansing {
  private static final ResourceLocation HOLY_RUNE =
      ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "holy_rune");

  private CurseCleansing() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(CurseCleansing::preview);
    NeoForge.EVENT_BUS.addListener(CurseCleansing::pay);
    NeoForge.EVENT_BUS.addListener(CurseCleansing::tooltip);
  }

  public static boolean isReagent(ItemStack stack) {
    return stack.getItemHolder().is(HOLY_RUNE);
  }

  private static void preview(GrindstoneEvent.OnPlaceItem event) {
    ItemStack input = event.getTopItem();
    if (!isReagent(event.getBottomItem()) || !isCursed(input)) return;

    ItemStack result = input.copyWithCount(1);
    EnchantmentHelper.updateEnchantments(
        result, enchantments -> enchantments.removeIf(holder -> holder.is(EnchantmentTags.CURSE)));
    event.setOutput(result);
    event.setXp(0);
  }

  private static void pay(GrindstoneEvent.OnTakeItem event) {
    ItemStack input = event.getTopItem();
    ItemStack rune = event.getBottomItem();
    if (!isReagent(rune) || !isCursed(input)) return;

    event.setNewTopItem(input.copyWithCount(input.getCount() - 1));
    event.setNewBottomItem(rune.copyWithCount(rune.getCount() - 1));
    event.setXp(0);
  }

  private static boolean isCursed(ItemStack input) {
    return EnchantmentHelper.getEnchantmentsForCrafting(input).keySet().stream()
        .anyMatch(holder -> holder.is(EnchantmentTags.CURSE));
  }

  private static void tooltip(ItemTooltipEvent event) {
    if (isReagent(event.getItemStack()))
      event
          .getToolTip()
          .add(Component.translatable("tooltip.aeroscapes_reimagine.curse_cleansing"));
  }
}
