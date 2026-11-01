package dev.aeroscapes.atmosphere;

/** Presentation changes that leave native chunk capture and light values intact. */
public final class DistantSceneryPolicy {
  private DistantSceneryPolicy() {}

  public static boolean showCompatibilityChat(String message) {
    return !message.startsWith("§6Distant Horizons: Chunky detected.§r\n");
  }

  public static byte material(byte original) {
    // The supplied shaders add excessive emission to this synthetic category.
    return original == 15 ? 0 : original;
  }
}
