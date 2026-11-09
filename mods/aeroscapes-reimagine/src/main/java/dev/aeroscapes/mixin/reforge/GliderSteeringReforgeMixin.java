package dev.aeroscapes.mixin.reforge;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.equipment.GliderReforge;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
abstract class GliderSteeringReforgeMixin {
  @ModifyReturnValue(method = "getFrictionInfluencedSpeed", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$gliderSteering(float inputSpeed) {
    if (!((Object) this instanceof Player player)) return inputSpeed;
    return switch (GliderReforge.profile(player)) {
      case "glider_poor" -> inputSpeed * 0.90F;
      case "glider_fine" -> inputSpeed * 1.05F;
      case "glider_exceptional" -> inputSpeed * 1.12F;
      case "glider_epic", "glider_legendary_catch" -> inputSpeed * 1.10F;
      default -> inputSpeed;
    };
  }
}
