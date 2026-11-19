package dev.aeroscapes.magic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An immutable prepared set; moving an assigned ability swaps its existing slot. */
public record Preparation(List<String> slots, int selected) {
  public static final int SLOT_COUNT = 8;

  public Preparation {
    if (slots.size() != SLOT_COUNT || selected < 0 || selected >= SLOT_COUNT)
      throw new IllegalArgumentException("Invalid ability preparation");
    slots = List.copyOf(slots);
  }

  public static Preparation empty() {
    return new Preparation(Collections.nCopies(SLOT_COUNT, ""), 0);
  }

  public Preparation assign(int slot, String ability) {
    if (slot < 0 || slot >= SLOT_COUNT) return this;
    var changed = new ArrayList<>(slots);
    int previous = ability.isEmpty() ? -1 : changed.indexOf(ability);
    if (previous >= 0) changed.set(previous, changed.get(slot));
    changed.set(slot, ability);
    return new Preparation(changed, selected);
  }

  public Preparation select(int slot) {
    return slot < 0 || slot >= SLOT_COUNT ? this : new Preparation(slots, slot);
  }
}
