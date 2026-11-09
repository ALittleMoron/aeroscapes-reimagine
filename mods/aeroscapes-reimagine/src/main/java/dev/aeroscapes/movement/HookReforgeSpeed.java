package dev.aeroscapes.movement;

import com.herobrot.tieredneo.api.TieredNeoAPI;
import com.yori3o.yo_hooks.common.entity.HookEntity;
import com.yori3o.yo_hooks.common.util.interfaces.PlayerWithHookData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class HookReforgeSpeed {
  private HookReforgeSpeed() {}

  public static int bonusPercent(ItemStack stack) {
    var tier = TieredNeoAPI.getTierId(stack);
    if (tier == null || !tier.getNamespace().equals("aeroscapes_reimagine")) return 0;
    return switch (tier.getPath()) {
      case "hook_poor" -> -10;
      case "hook_fine" -> 12;
      case "hook_exceptional" -> 22;
      default -> 0;
    };
  }

  public static float multiplier(HookEntity hook, Player player) {
    return (100 + bonusPercent(hook.getHeldStack(player))) / 100.0F;
  }

  public static float climbingMotion(float speed, Player player) {
    var hook = ((PlayerWithHookData) player).yo_hooks$getHook();
    return hook == null ? speed : speed * multiplier(hook, player);
  }
}
