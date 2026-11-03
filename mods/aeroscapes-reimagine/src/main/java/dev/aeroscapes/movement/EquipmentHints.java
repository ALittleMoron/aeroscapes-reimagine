package dev.aeroscapes.movement;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class EquipmentHints {
  private EquipmentHints() {}

  @SubscribeEvent
  public static void tooltip(ItemTooltipEvent event) {
    var stack = event.getItemStack();
    var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
    String hint = null;
    if (stack.is(Items.FISHING_ROD)
        || (id.getNamespace().equals("tide") && id.getPath().endsWith("_fishing_rod")))
      hint = "rod";
    if (id.getNamespace().equals("paraglider")) {
      if (id.getPath().endsWith("goddess_statue") || id.getPath().equals("spirit_orb"))
        hint = "goddess";
      if (id.getPath().equals("horned_statue")) hint = "horned";
      if (id.getPath().equals("paraglider") || id.getPath().equals("deku_leaf")) hint = "glider";
    }
    if (hint == null) return;
    for (int line = 1; line <= 3; line++)
      event
          .getToolTip()
          .add(
              Component.translatable("aeroscapes_reimagine.equipment." + hint + "." + line)
                  .withStyle(ChatFormatting.GRAY));
  }
}
