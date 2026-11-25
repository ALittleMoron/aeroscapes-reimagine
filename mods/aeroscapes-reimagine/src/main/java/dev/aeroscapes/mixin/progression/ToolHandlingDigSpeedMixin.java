package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.aeroscapes.equipment.tools.ToolProficiency;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class ToolHandlingDigSpeedMixin {
  @ModifyExpressionValue(
      method = "getDigSpeed",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Inventory;getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$baseToolSpeed(float speed) {
    return ToolProficiency.adjustBaseSpeed((Player) (Object) this, speed);
  }
}
