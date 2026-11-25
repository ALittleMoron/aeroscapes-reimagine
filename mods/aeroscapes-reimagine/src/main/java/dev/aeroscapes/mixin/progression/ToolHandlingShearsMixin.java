package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.equipment.tools.ToolProficiency;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShearsItem.class)
abstract class ToolHandlingShearsMixin {
  @Inject(method = "interactLivingEntity", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$shearsCooldown(
      ItemStack stack,
      Player player,
      LivingEntity target,
      InteractionHand hand,
      CallbackInfoReturnable<InteractionResult> result) {
    if (player.getCooldowns().isOnCooldown(stack.getItem()))
      result.setReturnValue(InteractionResult.FAIL);
  }

  @Inject(
      method = "interactLivingEntity",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$practiceShearing(
      ItemStack stack,
      Player player,
      LivingEntity target,
      InteractionHand hand,
      CallbackInfoReturnable<InteractionResult> result) {
    if (player instanceof ServerPlayer serverPlayer)
      ToolProficiency.completeToolUse(serverPlayer, (Item) (Object) this);
  }
}
