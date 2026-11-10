package dev.aeroscapes.mixin.movement;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.yori3o.yo_hooks.common.entity.HookEntity;
import com.yori3o.yo_hooks.common.network.ServerReceiver;
import dev.aeroscapes.movement.HookReforgeBonuses;
import dev.aeroscapes.movement.HookReforgeSpeed;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = ServerReceiver.class, remap = false)
public abstract class HookReforgeClimbMixin {
  @WrapOperation(
      method = {"climb", "jump"},
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/player/Player;causeFoodExhaustion(F)V"),
      require = 2,
      allow = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$movementHunger(
      Player player, float exhaustion, Operation<Void> original, @Local HookEntity hook) {
    original.call(
        player, HookReforgeBonuses.of(hook.getHeldStack(player)).applyHungerSaving(exhaustion));
  }

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
    return speed * HookReforgeSpeed.multiplier(hook, player, true);
  }

  @ModifyConstant(method = "climb", constant = @Constant(doubleValue = 1.5), require = 1, allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static double aeroscapes$descentSpeed(
      double speed, @Local(argsOnly = true) Player player, @Local HookEntity hook) {
    return speed * HookReforgeSpeed.multiplier(hook, player, false);
  }
}
