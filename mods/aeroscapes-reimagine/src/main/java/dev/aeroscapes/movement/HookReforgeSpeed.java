package dev.aeroscapes.movement;

import com.yori3o.yo_hooks.common.entity.HookEntity;
import com.yori3o.yo_hooks.common.util.interfaces.PlayerWithHookData;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class HookReforgeSpeed {
  private HookReforgeSpeed() {}

  public static int bonusPercent(ItemStack stack, boolean ascent) {
    return switch (ReforgeProfiles.path(stack)) {
      case "hook_poor" -> -10;
      case "hook_fine" -> 12;
      case "hook_exceptional" -> 22;
      case "hook_epic_ascent", "hook_legendary_stabilizer" -> ascent ? 30 : 15;
      case "hook_epic_descent" -> ascent ? 15 : 30;
      default -> 0;
    };
  }

  public static float multiplier(HookEntity hook, Player player, boolean ascent) {
    return (100 + bonusPercent(hook.getHeldStack(player), ascent)) / 100.0F;
  }

  public static float climbingMotion(float speed, Player player) {
    var hook = ((PlayerWithHookData) player).yo_hooks$getHook();
    return hook == null ? speed : speed * multiplier(hook, player, true);
  }

  public static Vec3 stabilize(Vec3 velocity, Player player) {
    if (!player.isShiftKeyDown()) return velocity;
    var hook = ((PlayerWithHookData) player).yo_hooks$getHook();
    if (hook == null
        || (!hook.isInBlock() && !hook.isInSableBlock())
        || !ReforgeProfiles.has(hook.getHeldStack(player), "hook_legendary_stabilizer"))
      return velocity;
    var radial = hook.position().subtract(player.position()).multiply(1, 0, 1).normalize();
    var lateral = velocity.multiply(1, 0, 1).subtract(radial.scale(velocity.dot(radial)));
    return velocity.subtract(lateral.scale(0.45));
  }
}
