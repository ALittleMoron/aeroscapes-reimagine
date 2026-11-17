package dev.aeroscapes.progression;

import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

/** Every cast trains magic, scaled by the spell's own mana cost so scrolls count too. */
public final class SpellPractice {
  private SpellPractice() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(SpellPractice::award);
  }

  public static long experience(int manaCost) {
    return Math.max(1, manaCost / 2);
  }

  private static void award(SpellOnCastEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      APIUtils.addXp("magic", player, experience(event.getOriginalManaCost()));
    }
  }
}
