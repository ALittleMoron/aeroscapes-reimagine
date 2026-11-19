package dev.aeroscapes.magic.client;

public final class AbilityHudSelectionTest {
  private AbilityHudSelectionTest() {}

  public static void main(String[] args) {
    var hud = new AbilityHudSelection();
    require(!hud.showEmptyHint(1000), "No empty hint before authoritative knowledge arrives");
    hud.accept(0, "", 1000);
    require(hud.showEmptyHint(1000), "The initial empty selection briefly explains itself");
    for (long now = 2000; now < 6000; now += 1000) {
      hud.accept(0, "", now);
      require(hud.showEmptyHint(now), "Repeated snapshots preserve the original five-second hint");
    }
    hud.accept(0, "", 6000);
    require(!hud.showEmptyHint(6000), "The hint expires after exactly five seconds");
    hud.accept(0, "", 8000);
    require(!hud.showEmptyHint(8000), "Later periodic snapshots cannot revive an expired hint");
    hud.accept(1, "", 9000);
    require(hud.showEmptyHint(13999), "Selecting another empty slot starts a new five-second hint");
    require(!hud.showEmptyHint(14000), "The changed selection also expires");
    hud.accept(1, "irons_spellbooks:firebolt", 15000);
    require(!hud.showEmptyHint(15000), "Prepared spells never show an empty warning");
    hud.accept(1, "", 16000);
    require(hud.showEmptyHint(16000), "Clearing the selected ability starts a new short hint");
    hud.reset();
    require(!hud.showEmptyHint(16000), "Leaving a session clears its hint");
    hud.accept(1, "", 17000);
    require(hud.showEmptyHint(17000), "A new session receives its own initial hint");
    System.out.println(
        "Ability HUD: five-second expiry, repeated snapshots and session changes passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
