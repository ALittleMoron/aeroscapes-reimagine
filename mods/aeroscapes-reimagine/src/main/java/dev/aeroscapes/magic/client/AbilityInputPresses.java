package dev.aeroscapes.magic.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Aliased bindings and key-repeat events share one activation until the physical key is released.
 */
public final class AbilityInputPresses {
  private final Set<String> held = new HashSet<>();
  private final Set<String> claimed = new HashSet<>();

  public void begin(Collection<String> down) {
    held.retainAll(down);
    claimed.clear();
  }

  public boolean claim(String key) {
    if (!claimed.add(key) || !held.add(key)) return false;
    return true;
  }

  public void release(String key) {
    held.remove(key);
    claimed.remove(key);
  }

  public void reset() {
    held.clear();
    claimed.clear();
  }
}
