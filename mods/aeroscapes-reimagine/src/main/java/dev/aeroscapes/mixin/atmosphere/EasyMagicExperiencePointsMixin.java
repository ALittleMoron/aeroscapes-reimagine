package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import fuzs.easymagic.util.PlayerExperienceHelper;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PlayerExperienceHelper.class, remap = false)
public abstract class EasyMagicExperiencePointsMixin {
  @ModifyReturnValue(
      method = "getTotalExperience(Lnet/minecraft/world/entity/player/Player;)I",
      at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native point charges leave a rounded float XP bar.
  private static int aeroscapes$roundVisibleExperience(
      int original, @Local(argsOnly = true) Player player) {
    float progressPoints = player.getXpNeededForNextLevel() * player.experienceProgress;
    int rounded = Math.round(progressPoints);
    // Repair float drift at integer boundaries; keep genuinely fractional point budgets floored.
    if (Math.abs(progressPoints - rounded) > 0.0001F) return original;
    return original - (int) progressPoints + rounded;
  }
}
