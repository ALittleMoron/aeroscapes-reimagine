package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.tools.ToolProficiency;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
abstract class ToolHandlingBlockUseMixin {
  @WrapOperation(
      method = "useOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/common/CommonHooks;onPlaceItemIntoWorld(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private InteractionResult aeroscapes$practiceToolUse(
      UseOnContext context, Operation<InteractionResult> original) {
    var item = ((ItemStack) (Object) this).getItem();
    if (!(context.getPlayer() instanceof ServerPlayer player) || !ToolProficiency.isTracked(item))
      return original.call(context);
    var level = context.getLevel();
    var position = context.getClickedPos();
    var before = level.getBlockState(position);
    var result = original.call(context);
    if (result.consumesAction() && before != level.getBlockState(position))
      ToolProficiency.completeToolUse(player, item);
    return result;
  }
}
