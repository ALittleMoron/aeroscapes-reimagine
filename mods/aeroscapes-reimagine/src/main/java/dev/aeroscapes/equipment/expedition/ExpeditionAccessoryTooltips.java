package dev.aeroscapes.equipment.expedition;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class ExpeditionAccessoryTooltips {
  private ExpeditionAccessoryTooltips() {}

  @SubscribeEvent
  public static void add(ItemTooltipEvent event) {
    var stack = event.getItemStack();
    for (var accessory : ExceptionalAccessory.values()) {
      if (!stack.is(accessory.item())) continue;
      if (accessory.matches(stack))
        event
            .getToolTip()
            .add(
                Component.translatable("aeroscapes_reimagine.abilities." + accessory.path + "_hint")
                    .withStyle(ChatFormatting.AQUA));
      else if (accessory != ExceptionalAccessory.INVISIBILITY)
        event
            .getToolTip()
            .add(
                Component.translatable("aeroscapes_reimagine.abilities.exceptional_craft_hint")
                    .withStyle(ChatFormatting.GRAY));
    }
  }
}
