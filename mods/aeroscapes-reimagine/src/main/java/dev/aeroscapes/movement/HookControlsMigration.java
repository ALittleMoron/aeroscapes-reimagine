package dev.aeroscapes.movement;

/** Upgrade only the pack's previous pair, once, without changing personal bindings. */
public final class HookControlsMigration {
  public static final int VERSION = 1;
  public static final String OPTIONS_KEY = "aeroscapesHookControlsVersion";

  private HookControlsMigration() {}

  public static boolean shouldMigrate(
      int version, String climb, String descend, boolean unmodified) {
    return version == 0
        && unmodified
        && climb.equals("key.keyboard.page.up")
        && descend.equals("key.keyboard.page.down");
  }
}
