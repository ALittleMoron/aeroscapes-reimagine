package dev.aeroscapes.construction;

import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/** Display-only text in LittleTiles widgets that bypass ordinary language keys. */
public final class ConstructionLanguage {
  private static final Map<String, String> LABELS =
      Map.of(
          "root", "aeroscapes.construction.root",
          "east", "aeroscapes.construction.direction.east",
          "west", "aeroscapes.construction.direction.west",
          "north", "aeroscapes.construction.direction.north",
          "south", "aeroscapes.construction.direction.south");
  private static final Pattern MATERIAL_VOLUME =
      Pattern.compile("(?:(\\d+) blocks )?(?:([\\d.,]+) pixel)?");

  private ConstructionLanguage() {}

  public static String literal(String text) {
    String key = LABELS.get(text);
    if (key != null) return Component.translatable(key).getString();
    if (text.endsWith(" ticks"))
      return Component.translatable(
              "aeroscapes.construction.ticks", text.substring(0, text.length() - 6))
          .getString();
    if (text.startsWith("space: "))
      return Component.translatable("aeroscapes.construction.space", text.substring(7)).getString();
    var volume = MATERIAL_VOLUME.matcher(text);
    if (!text.isEmpty() && volume.matches())
      return Component.translatable(
              "aeroscapes.construction.material_lore",
              volume.group(1) == null ? "0" : volume.group(1),
              volume.group(2) == null ? "0" : volume.group(2))
          .getString();
    return text;
  }
}
