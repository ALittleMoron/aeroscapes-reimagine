package dev.aeroscapes.movement;

import com.google.common.collect.AbstractIterator;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/** Keep native rendering stages while drawing attachments after their animated owners. */
public final class DeferredRenderOrder {
  private DeferredRenderOrder() {}

  public static <T> Iterator<T> last(Iterator<T> source, Predicate<T> deferred) {
    return new AbstractIterator<>() {
      private final List<T> pending = new ArrayList<>();
      private int next;

      @Override
      protected T computeNext() {
        while (source.hasNext()) {
          T item = source.next();
          if (deferred.test(item)) pending.add(item);
          else return item;
        }
        return next < pending.size() ? pending.get(next++) : endOfData();
      }
    };
  }
}
