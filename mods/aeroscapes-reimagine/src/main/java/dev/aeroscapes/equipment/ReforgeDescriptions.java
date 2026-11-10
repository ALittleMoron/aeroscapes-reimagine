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
    if (profile.isEmpty() || appendDescription(event, profile)) return;
    String base = ReforgeProfiles.basePath(profile);
    if (base.startsWith("magic_armor_") && base.endsWith("_legendary_ward"))
      base = "magic_armor_legendary_ward";
    if (base.equals("melee_legendary_frost")) base = "frost";
    if (!base.equals(profile)) appendDescription(event, base);
  }

  private static boolean appendDescription(ItemTooltipEvent event, String profile) {
    String key = "aeroscapes_reimagine.equipment." + profile + ".description";
    if (!Language.getInstance().has(key)) return false;
    event.getToolTip().add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
    return true;
  }
}
