package dev.aeroscapes.regression;

import dev.aeroscapes.races.CharacterChanges;
import net.minecraft.server.level.ServerPlayer;

/**
 * Loaded only by the upgraded pack. The baseline fixture also runs against the original 0.6.5 JAR.
 */
final class ChangeSaveUpgrade {
  private ChangeSaveUpgrade() {}

  static void changeRace(ServerPlayer player) {
    var choice = new CharacterChanges.Selection("race", "elf", 2, "", "");
    var quote = CharacterChanges.preview(player, choice);
    if (quote == null || !CharacterChanges.change(player, choice, quote))
      throw new IllegalStateException("Cannot change the confirmed 0.6.5 character");
  }
}
