package dev.aeroscapes.magic;

import io.redspace.ironsspellbooks.api.events.ModifySpellLevelEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

public final class SpellCastRules {
  private SpellCastRules() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(SpellCastRules::checkCast);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpellCastRules::limitTeleportLevel);
  }

  private static void limitTeleportLevel(ModifySpellLevelEvent event) {
    if (event.getEntity() instanceof Player
        && event.getSpell() == SpellRegistry.TELEPORT_SPELL.get()) {
      event.setLevel(Math.min(event.getLevel(), event.getSpell().getMaxLevel()));
    }
  }

  private static void checkCast(SpellPreCastEvent event) {
    var spell = SpellRegistry.getSpell(event.getSpellId());
    String message;
    // Native selection and loot check enabled, but an existing spell container can still cast.
    if (!spell.isEnabled()) {
      message = "message.aeroscapes_reimagine.spell_disabled";
    } else if (spell == SpellRegistry.RECALL_SPELL.get()
        && event.getCastSource() == CastSource.SCROLL) {
      // A dimension change clears the native cast state before the scroll is charged.
      message = "message.aeroscapes_reimagine.recall_requires_book";
    } else {
      return;
    }
    event.setCanceled(true);
    event.getEntity().displayClientMessage(Component.translatable(message), true);
  }
}
