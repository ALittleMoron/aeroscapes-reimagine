package dev.aeroscapes.magic.client;

import java.util.List;

public final class AbilityInputPressesTest {
  private AbilityInputPressesTest() {}

  public static void main(String[] arguments) {
    var presses = new AbilityInputPresses();
    presses.begin(List.of("key.keyboard.r"));
    require(presses.claim("key.keyboard.r"), "An existing native remap activates on its press");
    require(
        !presses.claim("key.keyboard.r"), "An overlapping added binding cannot duplicate the cast");
    presses.begin(List.of("key.keyboard.r"));
    require(
        !presses.claim("key.keyboard.r"),
        "Key-repeat cannot cast again while the same key remains held");
    presses.begin(List.of());
    presses.begin(List.of("key.keyboard.r"));
    require(presses.claim("key.keyboard.r"), "A fresh press works after release");
    require(presses.claim("key.mouse.middle"), "A distinct physical input has its own activation");
    require(!presses.claim("key.mouse.middle"), "Mouse aliases also activate once");
    presses.begin(List.of("key.keyboard.r", "key.mouse.middle"));
    require(
        !presses.claim("key.keyboard.r"),
        "A press rejected by a context guard cannot resume while held");
    presses.reset();
    presses.begin(List.of("key.keyboard.r"));
    require(presses.claim("key.keyboard.r"), "A new session has no stale held inputs");

    presses.begin(List.of("key.mouse.4"));
    require(presses.claim("key.mouse.4"), "A mouse toggle can open on its first press");
    presses.release("key.mouse.4");
    require(
        presses.claim("key.mouse.4"),
        "An observed release permits a closing press between client ticks");
    presses.begin(List.of("key.mouse.4"));
    require(
        !presses.claim("key.mouse.4"),
        "A Pre-handled closing press cannot reopen or cast through an alias next tick");
    presses.release("key.mouse.4");
    require(
        presses.claim("key.mouse.4"),
        "Another observed release permits the next opening press without an up-state polling tick");
    System.out.println(
        "Ability input: native aliases, repeat, release, mouse and session reset passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
