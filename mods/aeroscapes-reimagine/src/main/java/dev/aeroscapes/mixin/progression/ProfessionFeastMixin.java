package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.FeastBlock;
import vectorwing.farmersdelight.common.block.HoneyGlazedHamBlock;
import vectorwing.farmersdelight.common.block.RiceRollMedleyBlock;
import vectorwing.farmersdelight.common.block.RoastChickenBlock;
import vectorwing.farmersdelight.common.block.ShepherdsPieBlock;

@Mixin(
    value = {FeastBlock.class, RiceRollMedleyBlock.class},
    remap = false)
abstract class ProfessionFeastMixin {
  @Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$mealState(
      StateDefinition.Builder<Block, BlockState> builder, CallbackInfo callback) {
    Class<?> type = getClass();
    if (type == FeastBlock.class
        || type == HoneyGlazedHamBlock.class
        || type == RoastChickenBlock.class
        || type == ShepherdsPieBlock.class
        || type == RiceRollMedleyBlock.class) builder.add(ProfessionEffects.FEAST_TRAITS);
  }

  @ModifyReturnValue(method = "getServingItem", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private ItemStack aeroscapes$serveMeal(ItemStack portion, BlockState state) {
    return ProfessionEffects.prepare(
        portion, state.getOptionalValue(ProfessionEffects.FEAST_TRAITS).orElse(0));
  }
}
