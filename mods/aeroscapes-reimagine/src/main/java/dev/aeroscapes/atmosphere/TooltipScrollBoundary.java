package dev.aeroscapes.atmosphere;

import java.util.function.DoublePredicate;
import java.util.function.IntSupplier;

/** A tooltip consumes the wheel only while its native scroll target can move. */
public final class TooltipScrollBoundary {
  private TooltipScrollBoundary() {}

  public static boolean scroll(double amount, IntSupplier position, DoublePredicate original) {
    int before = position.getAsInt();
    return original.test(amount) && position.getAsInt() != before;
  }
}
