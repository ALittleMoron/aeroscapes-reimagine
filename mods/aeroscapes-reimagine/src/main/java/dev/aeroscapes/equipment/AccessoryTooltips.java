package dev.aeroscapes.equipment;

import java.util.Arrays;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class AccessoryTooltips {
  private static final String PREFIX = "aeroscapes_reimagine.accessory.";

  private AccessoryTooltips() {}

  public static Component line(String key) {
    return switch (key) {
      case PREFIX + "spyglass" -> withControl(key, "key.spyglass-improvements.use");
      case PREFIX + "backpack" -> withControl(key, "key.sophisticatedbackpacks.open_backpack");
      case PREFIX + "quiver" -> withControl(key, "supplementaries.keybind.quiver");
      case PREFIX + "terminal" -> withControl(key, "key.toms_storage.open_terminal");
      case PREFIX + "cursed_doll" ->
          Component.translatable(
              key,
              Component.translatable("spell.irons_spellbooks.blood_slash"),
              Component.translatable("spell.irons_spellbooks.blood_step"));
      case PREFIX + "necronomicon" ->
          Component.translatable(key, Component.translatable("spell.irons_spellbooks.raise_dead"));
      case "item.irons_spellbooks.lurker_ring.desc" -> Component.translatable(key, 50);
      default -> Component.translatable(key);
    };
  }

  private static Component withControl(String key, String control) {
    return Component.translatable(key, Component.keybind(control));
  }

  public static Component slot(String name) {
    return Component.translatable("curios.tooltip.slot")
        .append(" ")
        .append(Component.translatable("curios.identifier." + name));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void tooltip(ItemTooltipEvent event) {
    var id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
    var guide = AccessoryGuides.ITEMS.get(id);
    if (guide == null
        || guide.hints().isEmpty()
        || guide.nativeShiftDetails() && Screen.hasShiftDown()) return;
    var rows = event.getToolTip();
    guide.superseded().ifPresent(key -> rows.removeIf(row -> containsKey(row, key)));
    if (!hasKey(rows, "curios.tooltip.slot") && !hasKey(rows, "curios.modifiers." + guide.slot()))
      rows.add(slot(guide.slot()).copy().withStyle(ChatFormatting.GOLD));
    for (String key : guide.hints()) {
      if (!hasKey(rows, key)) rows.add(line(key).copy().withStyle(ChatFormatting.GRAY));
    }
  }

  private static boolean hasKey(List<Component> lines, String key) {
    return lines.stream().anyMatch(line -> containsKey(line, key));
  }

  private static boolean containsKey(Component line, String key) {
    if (line.getContents() instanceof TranslatableContents translation
        && (translation.getKey().equals(key)
            || Arrays.stream(translation.getArgs())
                .anyMatch(arg -> arg instanceof Component child && containsKey(child, key))))
      return true;
    return line.getSiblings().stream().anyMatch(child -> containsKey(child, key));
  }
}
