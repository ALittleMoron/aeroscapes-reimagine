package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.field.RopeLauncherReforge;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
    targets =
        "net.sprocketgames.create_aeronautics_throwable_rope_connector.item.RopeConnectorLauncherItem",
    remap = false)
abstract class RopeLauncherReforgeMixin {
  @WrapOperation(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/phys/Vec3;add(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private Vec3 aeroscapes$alignLauncher(
      Vec3 aim,
      Vec3 nativeOffset,
      Operation<Vec3> original,
      @Local(argsOnly = true) Player player,
      @Local(argsOnly = true) InteractionHand hand) {
    double factor =
        RopeLauncherReforge.offsetFactor(player.getItemInHand(hand), player.isShiftKeyDown());
    return original.call(aim, factor == 1.0 ? nativeOffset : nativeOffset.scale(factor));
  }

  @WrapOperation(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$launcherRecovery(
      ItemCooldowns cooldowns,
      Item item,
      int ticks,
      Operation<Void> original,
      @Local(argsOnly = true) Player player,
      @Local(argsOnly = true) InteractionHand hand) {
    String profile = ReforgeProfiles.path(player.getItemInHand(hand));
    int adjusted =
        switch (profile) {
          case "rope_launcher_exceptional_ready" -> ticks - 3;
          case "rope_launcher_epic_align", RopeLauncherReforge.AIM_GUIDE -> ticks - 4;
          default -> ticks;
        };
    original.call(cooldowns, item, Math.max(1, adjusted));
  }
}
