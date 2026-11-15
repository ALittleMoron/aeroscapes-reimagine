package dev.aeroscapes.navigation;

public final class MapLayerSelection {
  public static final int DEPTH = 64;
  public static final int STEP = 16;

  public enum Mode {
    FOLLOW,
    MANUAL,
    NATIVE
  }

  private Mode mode = Mode.NATIVE;
  private int manualCeiling;

  public Mode mode() {
    return mode;
  }

  public int ceiling(int playerY, int minimum, int maximum) {
    int ceiling =
        mode == Mode.MANUAL ? manualCeiling : Math.floorDiv(playerY + 8, STEP) * STEP + STEP - 1;
    return Math.clamp(ceiling, minimum, maximum);
  }

  public void move(int amount, int playerY, int minimum, int maximum) {
    manualCeiling = Math.clamp(ceiling(playerY, minimum, maximum) + amount, minimum, maximum);
    mode = Mode.MANUAL;
  }

  public void toggleFollowing() {
    mode = mode == Mode.FOLLOW ? Mode.NATIVE : Mode.FOLLOW;
  }

  public void reset() {
    mode = Mode.NATIVE;
    manualCeiling = 0;
  }
}
