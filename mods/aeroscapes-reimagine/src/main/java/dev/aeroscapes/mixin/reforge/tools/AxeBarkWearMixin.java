package dev.aeroscapes.mixin.reforge.tools;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import dev.aeroscapes.equipment.tools.ToolReforges;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AxeItem.class)
abstract class AxeBarkWearMixin {
  @WrapOperation(
      method = "useOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private BlockState aeroscapes$recognizeLog(
      Level level,
      BlockPos position,
      Operation<BlockState> original,
      @Share("aeroscapes$strippableLog") LocalBooleanRef strippable) {
    BlockState before = original.call(level, position);
    strippable.set(before.is(BlockTags.LOGS) && AxeItem.getAxeStrippingState(before) != null);
    return before;
  }

  @WrapOperation(
      method = "useOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$barkWear(
      ItemStack stack,
      int amount,
      LivingEntity user,
      EquipmentSlot slot,
      Operation<Void> original,
      @Share("aeroscapes$strippableLog") LocalBooleanRef strippable) {
    original.call(
        stack, strippable.get() ? ToolReforges.barkWear(stack, user, amount) : amount, user, slot);
  }
}
