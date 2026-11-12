package dev.aeroscapes.mixin.engineering.logistics;

import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.field.BuilderActions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
    targets =
        "com.yision.fluidlogistics.content.equipment.handPointer.client.HandPointerInteractionHandler",
    remap = false)
public abstract class HandPointerReforgeMixin {
  @Inject(method = "handleUseClick", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Native pointer actions bypass ItemStack.useOn.
  private void aeroscapes$successfulConfiguration(
      Player player,
      Level level,
      BlockPos pos,
      BlockState state,
      BlockHitResult hit,
      CallbackInfoReturnable<Boolean> result) {
    if (result.getReturnValueZ())
      BuilderActions.apply(
          Minecraft.getInstance(),
          ReforgeProfiles.path(player.getMainHandItem()),
          new UseOnContext(player, InteractionHand.MAIN_HAND, hit),
          InteractionResult.SUCCESS,
          false);
  }
}
