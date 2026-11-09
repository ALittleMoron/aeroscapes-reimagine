package dev.aeroscapes.mixin.reforge.tools;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.tools.ToolReforges;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.IShearable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShearsItem.class)
abstract class ShearsReforgeMixin {
  @WrapOperation(
      method = "interactLivingEntity",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/common/IShearable;onSheared(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Ljava/util/List;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private List<ItemStack> aeroscapes$increaseSheepWool(
      IShearable target,
      Player player,
      ItemStack stack,
      Level level,
      BlockPos pos,
      Operation<List<ItemStack>> original) {
    return ToolReforges.bonusWool(
        target, player, stack, original.call(target, player, stack, level, pos));
  }
}
