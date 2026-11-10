package dev.aeroscapes.mixin.reforge.field;

import dev.aeroscapes.equipment.field.BuilderChippedReforges;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "earth.terrarium.chipped.common.items.WorkbenchItem", remap = false)
abstract class BuilderChippedItemMixin {
  @Inject(method = "use", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$rememberTool(
      Level level,
      Player player,
      InteractionHand hand,
      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callback) {
    if (level.isClientSide()) BuilderChippedReforges.opening(player, player.getItemInHand(hand));
  }
}
