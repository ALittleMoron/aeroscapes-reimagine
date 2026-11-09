package dev.aeroscapes.magic;

final class SpellMouseGesture {
  private static final long HOLD_MILLIS = 200;

  enum Release {
    NONE,
    CAST,
    SELECT
  }

  private long pressedAt;
  private boolean pressed;
  private boolean wheelOpen;

  void press(long now) {
    pressedAt = now;
    pressed = true;
  }

  boolean shouldOpenWheel(long now) {
    return pressed && !wheelOpen && now - pressedAt >= HOLD_MILLIS;
  }

  void wheelOpened() {
    wheelOpen = true;
  }

  boolean ownsWheel() {
    return wheelOpen;
  }

  Release release(long now) {
    Release action = Release.NONE;
    if (pressed) {
      if (wheelOpen) action = Release.SELECT;
      else if (now - pressedAt < HOLD_MILLIS) action = Release.CAST;
    }
    cancel();
    return action;
  }

  void cancel() {
    pressed = false;
    wheelOpen = false;
  }
}
