package dev.aeroscapes.mixin.reforge.field;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.field.ExtendoReforges;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockItem.class)
abstract class ExtendoAxisReforgeMixin {
  @WrapOperation(
      method = "getPlacementState",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/block/Block;getStateForPlacement(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/level/block/state/BlockState;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private BlockState aeroscapes$lockedAxis(
      Block block, BlockPlaceContext context, Operation<BlockState> original) {
    return ExtendoReforges.placement(context, original.call(block, context));
  }
}
