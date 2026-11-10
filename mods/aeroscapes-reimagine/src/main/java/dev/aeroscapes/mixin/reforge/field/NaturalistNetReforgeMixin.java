package dev.aeroscapes.mixin.reforge.field;

import dev.aeroscapes.equipment.field.NetReforges;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.crispytwig.naturalist.world.item.BugNetItem", remap = false)
abstract class NaturalistNetReforgeMixin {
  @Inject(method = "use", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$aimAssist(
      Level level,
      Player player,
      InteractionHand hand,
      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> result) {
    if (player instanceof ServerPlayer serverPlayer
        && NetReforges.captureNearAim(serverPlayer, hand).consumesAction()) {
      result.setReturnValue(InteractionResultHolder.consume(player.getItemInHand(hand)));
    }
  }
}
