package dev.aeroscapes.mixin.inventory;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "tschipp.carryon.common.carry.PickupHandler", remap = false)
abstract class CarryOnRopePickupMixin {
  @WrapOperation(
      method = "tryPickUpBlock",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;removeBlockEntity(Lnet/minecraft/core/BlockPos;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$keepRopeHolderForNativeRemoval(
      Level level, BlockPos position, Operation<Void> remove) {
    // The following block removal tells Simulated to disconnect both endpoints and remove the rope.
    if (!(level.getBlockEntity(position) instanceof RopeStrandHolderBlockEntity))
      remove.call(level, position);
  }
}
