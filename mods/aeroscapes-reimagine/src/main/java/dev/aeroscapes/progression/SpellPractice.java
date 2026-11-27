package dev.aeroscapes.progression;

import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.races.RaceTraits;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

/** Every cast trains magic, scaled by the spell's own mana cost so scrolls count too. */
public final class SpellPractice {
  private SpellPractice() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpellPractice::award);
  }

  public static long experience(int manaCost) {
    return Math.max(1, manaCost / 2);
  }

  private static void award(SpellOnCastEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      boolean recast =
          MagicData.getPlayerMagicData(player)
              .getPlayerRecasts()
              .hasRecastForSpell(dev.aeroscapes.magic.SpellKnowledge.spell(event.getSpellId()));
      // Guardian Aid settles paid Blessing practice after the native target's health changes.
      if (!recast
          && !player.isCreative()
          && event.getCastSource() == CastSource.SPELLBOOK
          && event.getSpellId().equals("irons_spellbooks:blessing_of_life")
          && MagicData.getPlayerMagicData(player).isCasting()) return;
      int cost =
          event.getCastSource() == CastSource.SCROLL
              ? event.getOriginalManaCost()
              : event.getManaCost();
      boolean subclass = !recast && RaceTraits.qualifiesMagicPractice(player, event.getSpellId());
      RaceTraits.withClassPractice(
          player,
          "magic",
          false,
          subclass,
          () -> MagicPractice.award(player, recast ? 1 : experience(cost)));
    }
  }
}
