package dev.aeroscapes.mixin.movement;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.yori3o.yo_hooks.common.entity.HookEntity;
import com.yori3o.yo_hooks.common.network.ServerReceiver;
import dev.aeroscapes.movement.HookReforgeSpeed;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = ServerReceiver.class, remap = false)
public abstract class HookReforgeClimbMixin {
  @ModifyExpressionValue(
      method = "climb",
      at =
          @At(
              value = "FIELD",
              target = "Lcom/yori3o/yo_hooks/common/util/PhysicVariables;climbSpeedMultiplier:F"),
      require = 1,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static float aeroscapes$ascentSpeed(
      float speed, @Local(argsOnly = true) Player player, @Local HookEntity hook) {
    return speed * HookReforgeSpeed.multiplier(hook, player);
  }

  @ModifyConstant(method = "climb", constant = @Constant(doubleValue = 1.5), require = 1, allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static double aeroscapes$descentSpeed(
      double speed, @Local(argsOnly = true) Player player, @Local HookEntity hook) {
    return speed * HookReforgeSpeed.multiplier(hook, player);
  }
}
