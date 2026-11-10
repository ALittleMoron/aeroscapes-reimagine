package dev.aeroscapes.equipment;

import dev.aeroscapes.movement.HookReforgeSpeed;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
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

  public static float steering(Player player, float inputSpeed) {
    String profile = profile(player);
    float baseline =
        switch (ReforgeProfiles.basePath(profile)) {
          case "glider_poor" -> 0.90F;
          case "glider_fine" -> 1.05F;
          case "glider_exceptional" -> 1.12F;
          case "glider_epic", "glider_legendary_catch" -> 1.10F;
          default -> 1.0F;
        };
    if (!profile.contains("_with_") || ropeTaut(player)) return inputSpeed * baseline;
    var velocity = player.getDeltaMovement().multiply(1, 0, 1);
    var direction = inputDirection(player, new Vec3(player.xxa, 0, player.zza));
    double alignment = velocity.normalize().dot(direction);
    boolean moving = velocity.horizontalDistanceSqr() >= 0.01;
    float specialty =
        switch (profile) {
          case "glider_poor_with_wide_turn" -> moving && alignment < 0.85 ? 0.88F : 1.0F;
          case "glider_fine_with_steady_turn" -> moving && alignment < 0.85 ? 1.06F : 1.0F;
          case "glider_exceptional_with_straight_line" -> moving && alignment > 0.94 ? 1.08F : 1.0F;
          case "glider_epic_with_stable_turn" -> moving && alignment < 0.72 ? 1.10F : 1.0F;
          default -> 1.0F;
        };
    return inputSpeed * baseline * specialty;
  }

  public static void retainMomentum(Player player, Vec3 previous, Vec3 input) {
    if (player.horizontalCollision
        || ropeTaut(player)
        || player.onGround()
        || player.isInWater()
        || !profile(player).equals("glider_legendary_catch_with_turn_momentum")) return;
    var current = player.getDeltaMovement();
    double oldSpeed = previous.horizontalDistance();
    double speed = current.horizontalDistance();
    if (speed < 0.06 || speed >= oldSpeed || oldSpeed < 0.12) return;
    var direction = inputDirection(player, input);
    double alignment = (previous.x * direction.x + previous.z * direction.z) / oldSpeed;
    if (alignment >= 0.866 || alignment < -0.25) return;
    double kept = speed + Math.min(0.03, (oldSpeed - speed) * 0.45);
    double scale = kept / speed;
    player.setDeltaMovement(current.x * scale, current.y, current.z * scale);
  }

  private static Vec3 inputDirection(Player player, Vec3 input) {
    return input.multiply(1, 0, 1).yRot(-player.getYRot() * Mth.DEG_TO_RAD).normalize();
  }

  private static boolean ropeTaut(Player player) {
    return ModList.get().isLoaded("yo_hooks") && HookReforgeSpeed.taut(player);
  }

  private static boolean deployed(ItemStack stack) {
    if (stack.isEmpty()) return false;
    var capability = ParagliderUtils.getCaps(stack);
    return capability != null && capability.isParagliding(stack);
  }
}
