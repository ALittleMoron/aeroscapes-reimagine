package dev.aeroscapes.diagnostics;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded recent history and output window for one observed client level at a time. */
public final class SableTraceBuffer {
  private final int historyLimit;
  private final int followLimit;
  private final int outputLimit;
  private final ArrayDeque<String> history = new ArrayDeque<>();
  private WeakReference<Object> level = new WeakReference<>(null);
  private long session;
  private long sequence;
  private int remainingFollow;
  private int emitted;

  public SableTraceBuffer(int historyLimit, int followLimit, int outputLimit) {
    if (historyLimit <= 0 || followLimit < 0 || outputLimit <= 0) {
      throw new IllegalArgumentException("Invalid diagnostic limits");
    }
    this.historyLimit = historyLimit;
    this.followLimit = followLimit;
    this.outputLimit = outputLimit;
  }

  public synchronized List<String> record(Object currentLevel, String event, boolean missing) {
    if (currentLevel == null) return List.of();
    if (level.get() != currentLevel) {
      level = new WeakReference<>(currentLevel);
      history.clear();
      session++;
      sequence = 0;
      remainingFollow = 0;
      emitted = 0;
    }
    String entry = "session=" + session + " seq=" + ++sequence + " " + event;
    if (remainingFollow > 0) {
      remainingFollow--;
      return emit(List.of(entry));
    }
    if (history.size() == historyLimit) history.removeFirst();
    history.addLast(entry);
    if (!missing || emitted >= outputLimit) return List.of();
    List<String> result = emit(new ArrayList<>(history));
    history.clear();
    remainingFollow = followLimit;
    return result;
  }

  private List<String> emit(List<String> entries) {
    int count = Math.min(entries.size(), outputLimit - emitted);
    emitted += count;
    return List.copyOf(entries.subList(entries.size() - count, entries.size()));
  }
}
