package dev.aeroscapes.mixin.workshop;

import dev.aeroscapes.equipment.workshop.ManualForgeAnvil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.stirdrem.overgeared.block.custom.AbstractSmithingAnvil", remap = false)
abstract class ForgeStrikeMixin {
  @Inject(
      method = "useItemOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$prepareForgeStrike(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos position,
      Player player,
      InteractionHand hand,
      BlockHitResult hit,
      CallbackInfoReturnable<ItemInteractionResult> callback) {
    // Simple hammering does not set the player through the timing minigame.
    if (level.getBlockEntity(position) instanceof ManualForgeAnvil anvil
        && !anvil.aeroscapes$prepareStrike(player))
      callback.setReturnValue(ItemInteractionResult.CONSUME);
  }
}
