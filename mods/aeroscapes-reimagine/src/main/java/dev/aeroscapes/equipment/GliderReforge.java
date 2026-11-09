package dev.aeroscapes.equipment;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import tictim.paraglider.ParagliderUtils;
import tictim.paraglider.api.movement.Movement;

public final class GliderReforge {
  private GliderReforge() {}

  public static String profile(Player player) {
    if (!Movement.get(player).state().paragliding()) return "";
    var main = player.getMainHandItem();
    var off = player.getOffhandItem();
    if (deployed(main)) return ReforgeProfiles.path(main);
    return deployed(off) ? ReforgeProfiles.path(off) : "";
  }

  private static boolean deployed(ItemStack stack) {
    if (stack.isEmpty()) return false;
    var capability = ParagliderUtils.getCaps(stack);
    return capability != null && capability.isParagliding(stack);
  }
}
