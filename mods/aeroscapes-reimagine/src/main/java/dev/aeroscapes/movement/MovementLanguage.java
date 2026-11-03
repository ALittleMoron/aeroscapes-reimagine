package dev.aeroscapes.movement;

import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.locale.Language;

/** Display-only aliases for literals in the pinned travel equipment screens. */
public final class MovementLanguage {
  private MovementLanguage() {}

  public static String hookCategory(String text) {
    String key =
        switch (text) {
          case "Common" -> "aeroscapes.travel.hook.common";
          case "Server" -> "aeroscapes.travel.hook.server";
          case "Client" -> "aeroscapes.travel.hook.client";
          case "Parameters" -> "aeroscapes.travel.hook.parameters";
          default -> null;
        };
    return key == null ? text : Language.getInstance().getOrDefault(key, text);
  }

  public static String extraWheel(String text) {
    String key =
        switch (text) {
          case "LEFT" -> "aeroscapes.travel.extra_wheel.left";
          case "RIGHT" -> "aeroscapes.travel.extra_wheel.right";
          case "TOP" -> "aeroscapes.travel.extra_wheel.top";
          case "BOTTOM" -> "aeroscapes.travel.extra_wheel.bottom";
          default -> null;
        };
    return key == null ? text : Language.getInstance().getOrDefault(key, text);
  }

  public static String preview(String text) {
    return "No Preview".equals(text)
        ? Language.getInstance().getOrDefault("aeroscapes.travel.no_preview", text)
        : text;
  }

  public static String foreignState(String modId, String source) {
    if (!"paraglider".equals(modId)) return source;
    Language language = Language.getInstance();
    String generic = ConfigLiteralTranslations.translate("Ascending", language::getOrDefault);
    if (!"Ascending".equals(source) && !generic.equals(source)) return source;
    return language.getOrDefault("paraglider.configuration.ascending", source);
  }
}
