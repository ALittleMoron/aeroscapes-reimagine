package dev.aeroscapes.interfaceart;

import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.resources.language.I18n;

public final class OvergearedScreenText {
  private static final String KEY = "gui.aeroscapes_reimagine.overgeared.config.";
  private static final Pattern PUNCTUATION = Pattern.compile("[^a-z0-9]+");
  private static final Pattern ROW_COUNT = Pattern.compile(" \\(([0-9]+) items\\)");
  private static final String EDIT_PREFIX = "Edit ";
  private static final String COUNT_PREFIX = "Current items: ";

  private OvergearedScreenText() {}

  public static String display(String literal) {
    String key = KEY + PUNCTUATION.matcher(literal.toLowerCase(Locale.ROOT)).replaceAll("_");
    if (I18n.exists(key)) return I18n.get(key);
    if (literal.startsWith(EDIT_PREFIX)) {
      return I18n.get(KEY + "edit_blacklist", display(literal.substring(EDIT_PREFIX.length())));
    }
    if (literal.startsWith(COUNT_PREFIX)) {
      return I18n.get(KEY + "blacklist_count", literal.substring(COUNT_PREFIX.length()));
    }
    var rowCount = ROW_COUNT.matcher(literal);
    return rowCount.matches() ? I18n.get(KEY + "blacklist_row_count", rowCount.group(1)) : literal;
  }
}
