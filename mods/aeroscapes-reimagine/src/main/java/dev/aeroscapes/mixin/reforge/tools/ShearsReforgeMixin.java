package dev.aeroscapes.mixin.reforge.tools;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.equipment.tools.ToolReforges;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.IShearable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ShearsItem.class)
abstract class ShearsReforgeMixin {
  private static final String WEAR =
      "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V";

  @WrapOperation(method = "mineBlock", at = @At(value = "INVOKE", target = WEAR))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$foliageWear(
      ItemStack stack,
      int amount,
      LivingEntity user,
      EquipmentSlot slot,
      Operation<Void> original,
      @Local(argsOnly = true) BlockState block) {
    original.call(stack, ToolReforges.miningWear(stack, block, user, amount), user, slot);
  }

  @WrapOperation(method = "interactLivingEntity", at = @At(value = "INVOKE", target = WEAR))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$sheepWear(
      ItemStack stack,
      int amount,
      LivingEntity user,
      EquipmentSlot slot,
      Operation<Void> original,
      @Local(argsOnly = true) LivingEntity target) {
    int chance =
        target instanceof Sheep && ReforgeProfiles.has(stack, "shears_poor_with_snagging")
            ? -20
            : 0;
    original.call(stack, ToolReforges.modifyWear(user, amount, chance), user, slot);
  }

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
