package dev.aeroscapes.atmosphere;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * One aurora per sky. Complementary draws its own aurora, so the separate aurora renderer stays
 * hidden under that pack; without shaders and under MakeUp, which has no aurora, it draws.
 */
public final class AuroraOwnership {
  private AuroraOwnership() {}

  public static boolean shaderPackDrawsAurora() {
    return drawsOwnAurora(ActiveShaderPack.name());
  }

  /** Shader packs in this pack that render an aurora of their own. */
  public static boolean drawsOwnAurora(@Nullable String packName) {
    return packName != null && packName.toLowerCase(Locale.ROOT).contains("complementary");
  }
}
