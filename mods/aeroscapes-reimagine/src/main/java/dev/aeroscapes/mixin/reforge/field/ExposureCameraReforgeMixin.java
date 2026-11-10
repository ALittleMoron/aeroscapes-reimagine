package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "io.github.mortuusars.exposure.world.item.camera.CameraItem", remap = false)
abstract class ExposureCameraReforgeMixin {
  @ModifyReturnValue(method = "calculateCooldownAfterShot", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$cameraRecovery(int ticks, @Local(argsOnly = true) ItemStack stack) {
    return switch (ReforgeProfiles.path(stack)) {
      case "camera_epic_frame", "camera_legendary_deck" -> Math.max(1, ticks - 2);
      default -> ticks;
    };
  }
}
