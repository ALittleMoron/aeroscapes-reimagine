package dev.aeroscapes.diagnostics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded independent wait tokens, including reentrant waits on the same thread. */
final class AwaitTracker {
  record Await(long token, long startedNanos, String detail) {}

  private static final int LIMIT = 64;
  private static final int DETAIL_LIMIT = 256;
  private final Map<Long, Await> awaits = new LinkedHashMap<>();
  private long sequence;
  private long dropped;

  synchronized long begin(String detail, long now) {
    if (awaits.size() == LIMIT) {
      dropped++;
      return 0;
    }
    long token;
    do {
      token = ++sequence;
    } while (token == 0 || awaits.containsKey(token));
    String bounded = String.valueOf(detail).replace('\n', ' ').replace('\r', ' ');
    if (bounded.length() > DETAIL_LIMIT) bounded = bounded.substring(0, DETAIL_LIMIT);
    awaits.put(token, new Await(token, now, bounded));
    return token;
  }

  synchronized void end(long token) {
    awaits.remove(token);
  }

  synchronized List<Await> snapshot() {
    return List.copyOf(awaits.values());
  }

  synchronized long dropped() {
    return dropped;
  }

  synchronized void clear() {
    awaits.clear();
    dropped = 0;
  }
}
