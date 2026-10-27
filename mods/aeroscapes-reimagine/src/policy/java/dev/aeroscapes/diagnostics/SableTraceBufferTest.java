package dev.aeroscapes.diagnostics;

import java.util.List;

/** Checks bounded diagnostic history and world-session isolation without Minecraft. */
public final class SableTraceBufferTest {
  private SableTraceBufferTest() {}

  public static void main(String[] arguments) {
    var buffer = new SableTraceBuffer(3, 2, 5);
    Object first = new Object();
    require(buffer.record(first, "a", false).isEmpty(), "normal records must stay buffered");
    buffer.record(first, "b", false);
    buffer.record(first, "c", false);
    List<String> miss = buffer.record(first, "missing", true);
    require(
        miss.size() == 3 && miss.getFirst().endsWith("b") && miss.getLast().endsWith("missing"),
        "history must retain newest events in order");
    require(
        buffer.record(first, "recovered", false).size() == 1, "following event must be emitted");
    require(buffer.record(first, "stop", false).size() == 1, "following window must be emitted");
    require(buffer.record(first, "again", true).isEmpty(), "session output must remain bounded");
    var partial = new SableTraceBuffer(3, 0, 3);
    partial.record(first, "initial-miss", true);
    partial.record(first, "old-one", false);
    partial.record(first, "old-two", false);
    List<String> truncated = partial.record(first, "latest-miss", true);
    require(
        truncated.size() == 2 && truncated.getLast().endsWith("latest-miss"),
        "truncation must keep the triggering miss");
    Object second = new Object();
    List<String> newSession = buffer.record(second, "new-miss", true);
    require(
        newSession.size() == 1 && newSession.getFirst().endsWith("new-miss"),
        "history and output budget must reset for a new level");
    require(
        !newSession.getFirst().contains("recovered"), "old world events must not cross sessions");
    System.out.println(
        "Sable diagnostic buffer: history, follow-up, caps and level isolation passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
