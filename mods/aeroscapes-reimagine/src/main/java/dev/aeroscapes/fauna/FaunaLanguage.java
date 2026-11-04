package dev.aeroscapes.fauna;

import dev.aeroscapes.atmosphere.ConfigLiteralTranslations;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class FaunaLanguage {
  private FaunaLanguage() {}

  public static MutableComponent literal(String source) {
    return Component.translatableWithFallback(ConfigLiteralTranslations.key(source), source);
  }
}
