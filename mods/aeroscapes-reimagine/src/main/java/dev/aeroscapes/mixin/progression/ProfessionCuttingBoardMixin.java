package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

@Mixin(value = CuttingBoardBlockEntity.class, remap = false)
abstract class ProfessionCuttingBoardMixin {
  @ModifyArg(
      method = "lambda$processStoredItemUsingTool$2",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lvectorwing/farmersdelight/common/utility/ItemUtils;spawnItemEntity(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;DDDDDD)V"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ItemStack aeroscapes$cutPortion(ItemStack result, @Local(argsOnly = true) Player actor) {
    var board = (CuttingBoardBlockEntity) (Object) this;
    int carried = ProfessionEffects.preparedTraits(board.getStoredItem());
    return ProfessionEffects.prepare(result, carried | ProfessionEffects.mealTraits(actor));
  }
}
