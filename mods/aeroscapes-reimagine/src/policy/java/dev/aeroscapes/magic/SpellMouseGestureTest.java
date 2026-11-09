package dev.aeroscapes.magic;

import dev.aeroscapes.magic.SpellMouseGesture.Release;

public final class SpellMouseGestureTest {
  private SpellMouseGestureTest() {}

  public static void main(String[] args) {
    var gesture = new SpellMouseGesture();
    gesture.press(100);
    require(!gesture.shouldOpenWheel(299), "A short press keeps the wheel closed");
    require(gesture.release(299) == Release.CAST, "A short release casts");
    require(gesture.release(299) == Release.NONE, "A duplicate release does not cast again");

    gesture.press(400);
    require(gesture.shouldOpenWheel(600), "A held press opens the wheel at the threshold");
    gesture.wheelOpened();
    require(!gesture.shouldOpenWheel(650), "The wheel opens only once");
    require(gesture.release(650) == Release.SELECT, "Releasing the wheel selects without casting");

    gesture.press(700);
    require(gesture.release(900) == Release.NONE, "A missed wheel frame never turns into a cast");
    gesture.press(1000);
    gesture.cancel();
    require(gesture.release(1010) == Release.NONE, "A canceled short press does not cast");
    gesture.press(1100);
    gesture.wheelOpened();
    gesture.cancel();
    require(
        !gesture.ownsWheel() && gesture.release(1400) == Release.NONE,
        "A canceled wheel does not select");
    System.out.println("Spell mouse gesture: tap, hold, duplicate release and cancellation passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
