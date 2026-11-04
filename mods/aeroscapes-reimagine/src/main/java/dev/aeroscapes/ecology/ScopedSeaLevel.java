package dev.aeroscapes.ecology;

import java.util.function.Supplier;

/** A local waterline only while one pinned provider operation is executing. */
public final class ScopedSeaLevel {
  private static final ThreadLocal<Integer> CURRENT = new ThreadLocal<>();

  private ScopedSeaLevel() {}

  public static int resolve(int nativeLevel) {
    Integer local = CURRENT.get();
    return local == null ? nativeLevel : local;
  }

  public static <T> T call(int waterline, Supplier<T> operation) {
    Integer previous = CURRENT.get();
    CURRENT.set(waterline);
    try {
      return operation.get();
    } finally {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    }
  }
}
