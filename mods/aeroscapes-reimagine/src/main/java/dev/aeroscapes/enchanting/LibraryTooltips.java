package dev.aeroscapes.enchanting;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class LibraryTooltips {
  private LibraryTooltips() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(LibraryTooltips::describe);
  }

  private static void describe(ItemTooltipEvent event) {
    var item = event.getItemStack();
    if (item.is(Items.ENCHANTING_TABLE)) {
      event
          .getToolTip()
          .add(
              Component.translatable("aeroscapes_reimagine.enchanting.table_hint")
                  .withStyle(ChatFormatting.GRAY));
    } else if (LibraryBooks.isKnowledgeBook(item)) {
      var stored = item.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
      var effect = stored.keySet().iterator().next();
      int level = stored.getLevel(effect);
      event
          .getToolTip()
          .add(
              Component.translatable(
                      "aeroscapes_reimagine.enchanting.book_level",
                      Enchantment.getFullname(effect, level))
                  .withStyle(ChatFormatting.AQUA));
      if (LibraryBooks.advanced(effect, level)) {
        event
            .getToolTip()
            .add(
                Component.translatable("aeroscapes_reimagine.enchanting.anvil_knowledge")
                    .withStyle(ChatFormatting.GRAY));
      }
    } else if (item.is(Items.ENCHANTED_BOOK)) {
      event
          .getToolTip()
          .add(
              Component.translatable("aeroscapes_reimagine.enchanting.ordinary_book")
                  .withStyle(ChatFormatting.GRAY));
    }
  }
}
