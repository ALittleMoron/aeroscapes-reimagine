package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.races.ProfessionEffects;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Block.class)
abstract class ProfessionCropMixin {
  @ModifyReturnValue(
      method =
          "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)Ljava/util/List;",
      at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static List<ItemStack> aeroscapes$harvest(
      List<ItemStack> drops,
      BlockState state,
      ServerLevel level,
      BlockPos pos,
      BlockEntity blockEntity,
      Entity actor,
      ItemStack tool) {
    if (actor != null && ProfessionEffects.mature(state))
      ProfessionEffects.addProduce(drops, actor);
    return drops;
  }
}
