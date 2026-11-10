package dev.aeroscapes.movement;

import com.yori3o.yo_hooks.common.compat.Compats;
import com.yori3o.yo_hooks.common.compat.sable.SableCompat;
import com.yori3o.yo_hooks.common.entity.HookEntity;
import com.yori3o.yo_hooks.common.util.interfaces.PlayerWithHookData;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class HookReforgeSpeed {
  private static final String SOFTENED_CATCH = "aeroscapes_reimagine:softened_hook_catch";

  private HookReforgeSpeed() {}

  public static int bonusPercent(ItemStack stack, boolean ascent) {
    return switch (ReforgeProfiles.basePath(stack)) {
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
    if (!(player instanceof PlayerWithHookData data)) return velocity;
    var hook = data.yo_hooks$getHook();
    var offset = attachedOffset(player, hook);
    if (offset == null) return velocity;
    String profile = ReforgeProfiles.path(hook.getHeldStack(player));
    if (profile.equals("hook_legendary_stabilizer")) {
      if (!player.isShiftKeyDown()) return velocity;
      return adjustSway(velocity, offset, 0.45);
    }
    if (offset.lengthSqr() < hook.getLength() * hook.getLength()) return velocity;
    return switch (profile) {
      case "hook_poor_with_sway" -> adjustSway(velocity, offset, -0.12);
      case "hook_fine_with_steadiness" ->
          velocity.horizontalDistanceSqr() < 0.16 ? adjustSway(velocity, offset, 0.16) : velocity;
      case "hook_exceptional_with_short_rope" ->
          hook.getLength() <= 6.0 ? adjustSway(velocity, offset, 0.22) : velocity;
      case "hook_epic_descent_with_short_rope" ->
          hook.getLength() <= 8.0 ? adjustSway(velocity, offset, 0.30) : velocity;
      case "hook_legendary_stabilizer_with_first_catch" ->
          firstCatch(hook, velocity, player.getDeltaMovement(), offset);
      default -> velocity;
    };
  }

  public static boolean taut(Player player) {
    if (!(player instanceof PlayerWithHookData data)) return false;
    var hook = data.yo_hooks$getHook();
    var offset = attachedOffset(player, hook);
    return offset != null && offset.lengthSqr() >= hook.getLength() * hook.getLength();
  }

  private static Vec3 attachedOffset(Player player, HookEntity hook) {
    if (hook == null
        || hook.isRemoved()
        || (!hook.isInBlock() && !hook.isInSableBlock())
        || hook.getLength() <= 0
        || hook.getHeldStack(player).isEmpty()) return null;
    var anchor = hook.position();
    if (Compats.isSableLoaded) anchor = SableCompat.getHookPos(anchor, hook);
    return anchor == null ? null : anchor.subtract(player.getEyePosition());
  }

  private static Vec3 adjustSway(Vec3 velocity, Vec3 offset, double damping) {
    var radial = offset.multiply(1, 0, 1).normalize();
    var lateral = velocity.multiply(1, 0, 1).subtract(radial.scale(velocity.dot(radial)));
    return velocity.subtract(lateral.scale(damping));
  }

  private static Vec3 firstCatch(HookEntity hook, Vec3 velocity, Vec3 previous, Vec3 offset) {
    var data = hook.getPersistentData();
    if (data.getBoolean(SOFTENED_CATCH)) return velocity;
    data.putBoolean(SOFTENED_CATCH, true);
    if (offset.y <= 0.1 || previous.y >= -0.20) return velocity;
    return new Vec3(velocity.x, velocity.y * 0.60, velocity.z);
  }
}
