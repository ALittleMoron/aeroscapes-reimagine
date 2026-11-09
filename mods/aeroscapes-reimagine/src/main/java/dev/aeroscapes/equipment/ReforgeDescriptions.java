package dev.aeroscapes.equipment;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class ReforgeDescriptions {
  private ReforgeDescriptions() {}

  @SubscribeEvent
  public static void tooltip(ItemTooltipEvent event) {
    String profile = ReforgeProfiles.path(event.getItemStack());
    if (profile.isEmpty()) return;
    if (profile.startsWith("magic_armor_") && profile.endsWith("_legendary_ward"))
      profile = "magic_armor_legendary_ward";
    String key =
        "aeroscapes_reimagine.equipment."
            + (profile.equals("melee_legendary_frost") ? "frost" : profile)
            + ".description";
    if (Language.getInstance().has(key)) {
      event.getToolTip().add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }
  }
}
