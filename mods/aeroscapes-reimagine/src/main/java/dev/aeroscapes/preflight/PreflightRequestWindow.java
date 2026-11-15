package dev.aeroscapes.preflight;

import java.util.OptionalInt;

final class PreflightRequestWindow {
  private int sequence;
  private boolean pending;
  private boolean served;
  private long lastAnswer;

  void offer(int value) {
    sequence = value;
    pending = true;
  }

  OptionalInt take(long now) {
    if (!pending || (served && now >= lastAnswer && now - lastAnswer < 20))
      return OptionalInt.empty();
    pending = false;
    served = true;
    lastAnswer = now;
    return OptionalInt.of(sequence);
  }
}
