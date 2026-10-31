package dev.aeroscapes.atmosphere;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** Localizes recognized display strings without changing configuration metadata. */
public final class ConfigLiteralTranslations {
  private static final List<String> METADATA_PREFIXES =
      List.of(" Default: ", " Range: ", " Allowed Values: ");
  private static final Map<String, String> COMMENT_ALIASES =
      Map.of(
          "cropsloverain.configuration.chance_description",
          "cropsloverain.configuration.rain_growth_speed.tooltip",
          "cropsloverain.configuration.chance_description_individual",
          "cropsloverain.configuration.use_individual_speeds.tooltip");

  private ConfigLiteralTranslations() {}

  public static String key(String source) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
      return "aeroscapes_reimagine.config.literal." + HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Required SHA-256 digest is unavailable", exception);
    }
  }

  public static String translate(String source, BiFunction<String, String, String> translations) {
    String fallback = translations.apply(key(source), source);
    String alias = COMMENT_ALIASES.get(source);
    // Crops Love Rain passes these two obsolete language IDs as literal comments.
    return alias == null
        ? fallback
        : translations.apply(source, translations.apply(alias, fallback));
  }

  public static String translateCommentLine(
      String sourceLine, String sourceBlock, BiFunction<String, String, String> translations) {
    if (COMMENT_ALIASES.containsKey(sourceLine)) return translate(sourceLine, translations);
    if (sourceBlock != null) {
      String translatedBlock = translations.apply(key(sourceBlock), null);
      if (translatedBlock != null) {
        String[] sourceLines = sourceBlock.split("\n", -1);
        String[] translatedLines = translatedBlock.split("\n", -1);
        if (sourceLines.length == translatedLines.length) {
          String candidate = null;
          boolean ambiguous = false;
          for (int index = 0; index < sourceLines.length; index++) {
            if (!sourceLines[index].equals(sourceLine)) continue;
            if (candidate != null && !candidate.equals(translatedLines[index])) ambiguous = true;
            candidate = translatedLines[index];
          }
          if (candidate != null && !ambiguous) return candidate;
        }
      }
    }
    return translateDisplayLine(sourceLine, translations);
  }

  public static String translateCommentBlock(
      String source, BiFunction<String, String, String> translations) {
    if (COMMENT_ALIASES.containsKey(source)) return translate(source, translations);
    String exact = translations.apply(key(source), null);
    if (exact != null) return exact;
    String[] lines = source.split("\n", -1);
    for (int index = 0; index < lines.length; index++) {
      lines[index] = translateDisplayLine(lines[index], translations);
    }
    return String.join("\n", lines);
  }

  private static String translateDisplayLine(
      String source, BiFunction<String, String, String> translations) {
    if (COMMENT_ALIASES.containsKey(source)) return translate(source, translations);
    String exact = translations.apply(key(source), null);
    if (exact != null) return exact;
    for (String prefix : METADATA_PREFIXES) {
      if (source.startsWith(prefix)) {
        return translate(prefix, translations) + source.substring(prefix.length());
      }
    }
    return source;
  }
}
