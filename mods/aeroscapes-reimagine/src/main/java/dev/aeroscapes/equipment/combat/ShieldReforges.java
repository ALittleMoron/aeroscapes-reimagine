package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class ShieldReforges {
  private ShieldReforges() {}

  public static int wear(Player player, int damage) {
    float multiplier =
        switch (ReforgeProfiles.path(player.getUseItem())) {
          case "shield_poor" -> 1.10F;
          case "shield_fine" -> 0.94F;
          case "shield_exceptional" -> 0.85F;
          case "shield_epic", "shield_legendary_riposte" -> 0.88F;
          default -> 1.0F;
        };
    if (multiplier == 1.0F) {
      return damage;
    }
    float scaled = damage * multiplier;
    int whole = Mth.floor(scaled);
    return whole + (player.getRandom().nextFloat() < scaled - whole ? 1 : 0);
  }

  static boolean nimble(Player player) {
    if (!player.isBlocking()) {
      return false;
    }
    var rank = ReforgeProfiles.path(player.getUseItem());
    return "shield_epic".equals(rank) || "shield_legendary_riposte".equals(rank);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void block(LivingShieldBlockEvent event) {
    if (event.getEntity() instanceof ServerPlayer player
        && event.getOriginalBlock()
        && event.getBlocked()
        && event.getBlockedDamage() > 0.0F
        && ReforgeProfiles.has(player.getUseItem(), "shield_legendary_riposte")) {
      MeleeReforges.armRiposte(player);
    }
  }
}
