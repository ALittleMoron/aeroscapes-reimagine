package dev.aeroscapes.magic.client;

/** Repeated server snapshots do not renew an empty-selection hint. */
public final class AbilityHudSelection {
  private boolean initialized;
  private int selectedSlot;
  private String selectedAbility = "";
  private long changedAt;

  public void accept(int slot, String ability, long now) {
    if (!initialized || slot != selectedSlot || !ability.equals(selectedAbility)) {
      initialized = true;
      selectedSlot = slot;
      selectedAbility = ability;
      changedAt = now;
    }
  }

  public boolean showEmptyHint(long now) {
    return initialized && selectedAbility.isEmpty() && now - changedAt < 5000;
  }

  public void reset() {
    initialized = false;
    selectedAbility = "";
  }
}
