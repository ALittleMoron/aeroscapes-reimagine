package dev.aeroscapes.husbandry;

import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class HusbandryLanguage {
  private static final List<Template> TEMPLATES =
      List.of(
          template("§6Transfer Deed: %s"),
          template("§7Seller: §f%s"),
          template("§aAdded %s to the Ledger's co-owners."),
          template("§e%s is already in this Ledger."),
          template("§cThis %s is not domesticated."),
          template("§fGrowth: §e~%sm\n"),
          template("§fBreeding: §e~%sm\n"),
          template("§fPregnancy: §d♥ Pregnant! §7(~%sd)\n"),
          template("§fMilk: %s"),
          template("Generation %s"));

  private HusbandryLanguage() {}

  public static MutableComponent literal(String source) {
    for (Template template : TEMPLATES) {
      var matcher = template.pattern().matcher(source);
      if (!matcher.matches()) continue;
      var arguments = new ArrayList<String>();
      for (int index = 1; index <= matcher.groupCount(); index++)
        arguments.add(matcher.group(index));
      return Component.translatableWithFallback(
          ConfigLiteralTranslations.key(template.source()), template.source(), arguments.toArray());
    }
    return Component.translatableWithFallback(ConfigLiteralTranslations.key(source), source);
  }

  public static String text(String source) {
    return literal(source).getString();
  }

  private static Template template(String source) {
    String expression =
        String.join(
            "(.*?)", java.util.Arrays.stream(source.split("%s", -1)).map(Pattern::quote).toList());
    return new Template(source, Pattern.compile(expression, Pattern.DOTALL));
  }

  private record Template(String source, Pattern pattern) {}
}
