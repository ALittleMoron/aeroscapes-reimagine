package dev.aeroscapes.diagnostics;

/** Verifies nested cleanup and strict retained-state bounds without starting Minecraft. */
public final class AwaitTrackerTest {
  private AwaitTrackerTest() {}

  public static void main(String[] arguments) {
    var tracker = new AwaitTracker();
    long outer = tracker.begin("outer", 10);
    long inner = tracker.begin("inner", 20);
    require(outer != 0 && inner != 0 && outer != inner, "Independent wait tokens");
    tracker.end(inner);
    require(tracker.snapshot().size() == 1, "Inner completion retains outer wait");
    require(tracker.snapshot().getFirst().token() == outer, "Outer token preserved");
    tracker.end(0);
    require(tracker.snapshot().size() == 1, "Disabled token cannot remove active wait");
    tracker.end(outer);
    require(tracker.snapshot().isEmpty(), "Outer cleanup empties registry");

    long surviving = tracker.begin("outer exception scope", 21);
    try {
      long failing = tracker.begin("inner exception scope", 22);
      try {
        throw new IllegalStateException("wait failure");
      } finally {
        tracker.end(failing);
      }
    } catch (IllegalStateException expected) {
      require(expected.getMessage().equals("wait failure"), "Original failure propagates");
      require(tracker.snapshot().size() == 1, "Exceptional inner cleanup preserves outer wait");
      require(
          tracker.snapshot().getFirst().token() == surviving,
          "Exceptional cleanup keeps outer token");
    } finally {
      tracker.end(surviving);
    }
    require(tracker.snapshot().isEmpty(), "Exceptional outer cleanup empties registry");

    long stale = tracker.begin("old session", 30);
    tracker.clear();
    long current = tracker.begin("new session", 40);
    tracker.end(stale);
    require(
        current != stale && tracker.snapshot().size() == 1,
        "Late cleanup cannot clear new session");
    tracker.clear();
    for (int index = 0; index < 64; index++) {
      require(tracker.begin("chunk " + index, index) != 0, "Capacity admits each bounded wait");
    }
    long first = tracker.snapshot().getFirst().token();
    require(tracker.begin("overflow", 100) == 0, "Overflow is untracked");
    require(tracker.dropped() == 1, "Overflow count exposes incomplete coverage");
    require(tracker.snapshot().size() == 64, "No retained overflow");
    require(tracker.snapshot().getFirst().token() == first, "Overflow never evicts original wait");
    tracker.end(first);
    require(tracker.begin("replacement", 101) != 0, "Cleanup restores capacity");
    tracker.clear();
    require(tracker.dropped() == 0, "New observation session resets overflow count");
    tracker.begin("x".repeat(300) + "\nignored", 123);
    var bounded = tracker.snapshot().getFirst();
    require(bounded.detail().length() == 256, "Bounded diagnostic detail");
    require(bounded.startedNanos() == 123, "Original wait clock retained");
    tracker.clear();
    tracker.begin("first\nsecond\rthird", 0);
    require(
        tracker.snapshot().getFirst().detail().equals("first second third"), "Single-line detail");
    System.out.println(
        "Await tracker: nested cleanup, session isolation and capacity checks passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
