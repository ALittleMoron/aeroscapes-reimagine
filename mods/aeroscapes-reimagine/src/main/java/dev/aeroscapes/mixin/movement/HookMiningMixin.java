package dev.aeroscapes.mixin.movement;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yori3o.yo_hooks.common.util.interfaces.PlayerWithHookData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class HookMiningMixin {
  @WrapOperation(
      method =
          "getDigSpeed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)F",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Only the airborne mining penalty uses this check.
  private boolean aeroscapes$anchoredMining(Player player, Operation<Boolean> original) {
    if (original.call(player)) return true;
    var hook = ((PlayerWithHookData) player).yo_hooks$getHook();
    return hook != null && hook.isInBlock() && hook.isControlledBy(player);
  }
}
