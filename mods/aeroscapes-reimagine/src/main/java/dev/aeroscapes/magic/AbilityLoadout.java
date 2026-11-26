package dev.aeroscapes.magic;

import java.util.Collections;
import java.util.List;

public record AbilityLoadout(List<Preparation> wheels, int activeWheel) {
  public static final int WHEEL_COUNT = 3;

  public AbilityLoadout {
    if (wheels.size() != WHEEL_COUNT || !validWheel(activeWheel))
      throw new IllegalArgumentException("Invalid ability wheels");
    wheels = List.copyOf(wheels);
  }

  public static AbilityLoadout empty() {
    return new AbilityLoadout(Collections.nCopies(WHEEL_COUNT, Preparation.empty()), 0);
  }

  public static boolean validWheel(int wheel) {
    return wheel >= 0 && wheel < WHEEL_COUNT;
  }

  public Preparation active() {
    return wheels.get(activeWheel);
  }

  public boolean contains(String id) {
    return wheels.stream().anyMatch(wheel -> wheel.slots().contains(id));
  }

  public List<String> assignedIds() {
    return wheels.stream()
        .flatMap(wheel -> wheel.slots().stream())
        .filter(id -> !id.isEmpty())
        .distinct()
        .toList();
  }
}
