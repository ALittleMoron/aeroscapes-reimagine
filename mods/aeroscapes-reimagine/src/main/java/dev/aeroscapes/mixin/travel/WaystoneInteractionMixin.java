package dev.aeroscapes.mixin.travel;

import dev.aeroscapes.travel.TravelIntegration;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.block.WaystoneBlock;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WaystoneBlock.class, remap = false)
public abstract class WaystoneInteractionMixin {
  @Inject(method = "handleActivation", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Stop the native success message when payment fails.
  private void aeroscapes$checkActivation(
      Level level,
      BlockPos pos,
      Player player,
      WaystoneBlockEntityBase blockEntity,
      Waystone stone,
      CallbackInfoReturnable<InteractionResult> callback) {
    if (!TravelIntegration.canActivate(player, stone, false)) {
      callback.setReturnValue(InteractionResult.FAIL);
    }
  }
}
