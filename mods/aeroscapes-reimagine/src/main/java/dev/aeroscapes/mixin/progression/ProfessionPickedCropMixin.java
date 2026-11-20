package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.races.ProfessionEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import vectorwing.farmersdelight.common.block.TomatoVineBlock;

@Mixin(
    value = {SweetBerryBushBlock.class, TomatoVineBlock.class},
    remap = false)
abstract class ProfessionPickedCropMixin {
  @ModifyArg(
      method = "useWithoutItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;<init>(Lnet/minecraft/world/level/ItemLike;I)V"),
      index = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$ripePick(
      int amount, @Local(argsOnly = true) BlockState crop, @Local(argsOnly = true) Player player) {
    return ProfessionEffects.mature(crop) && ProfessionEffects.extraHarvest(player)
        ? amount + 1
        : amount;
  }
}
