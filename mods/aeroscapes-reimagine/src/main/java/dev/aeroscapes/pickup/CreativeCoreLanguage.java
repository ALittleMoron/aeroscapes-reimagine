package dev.aeroscapes.pickup;

import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

/** Literal labels in the selected CreativeCore ingredient/configuration widgets. */
public final class CreativeCoreLanguage {
  private static final Map<String, String> KEYS =
      Map.ofEntries(
          Map.entry("Default", "aeroscapes.pickup.ingredient.default"),
          Map.entry("Blocktag", "aeroscapes.pickup.ingredient.blocktag"),
          Map.entry("Itemtag", "aeroscapes.pickup.ingredient.itemtag"),
          Map.entry("Fuel", "aeroscapes.pickup.ingredient.fuel"),
          Map.entry("Nothing to select", "aeroscapes.pickup.ingredient.nothing"),
          Map.entry("empty", "aeroscapes.pickup.ingredient.empty"),
          Map.entry("invalid", "aeroscapes.pickup.ingredient.invalid"),
          Map.entry("reset to default", "aeroscapes.pickup.reset"),
          Map.entry("and", "aeroscapes.pickup.selector.and"),
          Map.entry("or", "aeroscapes.pickup.selector.or"),
          Map.entry("not", "aeroscapes.pickup.selector.not"),
          Map.entry("level", "aeroscapes.pickup.selector.level"),
          Map.entry("mode", "aeroscapes.pickup.selector.mode"),
          Map.entry("selector", "aeroscapes.pickup.selector.selector"),
          Map.entry(" and ", "aeroscapes.pickup.selector.join_and"),
          Map.entry(" or ", "aeroscapes.pickup.selector.join_or"));

  private CreativeCoreLanguage() {}

  public static String translate(String text) {
    if (text.startsWith("level>="))
      return Component.translatable("aeroscapes.pickup.selector.permission", text.substring(7))
          .getString();
    if (text.startsWith("pattern: "))
      return Component.translatable("aeroscapes.pickup.selector.pattern", text.substring(9))
          .getString();
    String key = KEYS.get(text);
    return key == null ? text : Language.getInstance().getOrDefault(key, text);
  }
}
