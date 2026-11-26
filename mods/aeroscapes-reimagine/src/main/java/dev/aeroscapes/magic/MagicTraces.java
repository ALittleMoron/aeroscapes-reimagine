package dev.aeroscapes.magic;

import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class MagicTraces {
  private MagicTraces() {}

  public static void register() {
    NeoForge.EVENT_BUS.addListener(MagicTraces::leaveTrace);
  }

  private static void leaveTrace(SpellOnCastEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
    var particle =
        switch (event.getSpellId()) {
          case "irons_spellbooks:heal", "irons_spellbooks:cleanse" -> ParticleTypes.WAX_ON;
          case "irons_spellbooks:touch_dig" -> ParticleTypes.ELECTRIC_SPARK;
          default -> null;
        };
    if (particle == null) return;
    player
        .serverLevel()
        .sendParticles(
            particle,
            player.getX(),
            player.getY() + 0.14,
            player.getZ(),
            7,
            0.42,
            0.04,
            0.42,
            0.012);
  }
}
