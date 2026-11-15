package dev.aeroscapes.mixin.husbandry;

import dev.aeroscapes.husbandry.TroughAutomation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.athisworkshop.animalhusbandry.block.FeedingTroughBlock;
import org.athisworkshop.animalhusbandry.block.FeedingTroughBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FeedingTroughBlock.class, remap = false)
abstract class HusbandryTroughInteractionMixin {
  @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes this pinned provider callback.
  private void aeroscapes$wholeBucket(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos position,
      Player player,
      InteractionHand hand,
      BlockHitResult hit,
      CallbackInfoReturnable<ItemInteractionResult> callback) {
    if (!(level.getBlockEntity(position) instanceof FeedingTroughBlockEntity trough)) return;
    int water = TroughAutomation.waterUnits(trough);
    boolean partialBucket =
        (stack.is(Items.BUCKET) && water > 0 && water < 16000)
            || (stack.is(Items.WATER_BUCKET) && water > 0 && water < 16000);
    boolean mixedContents = TroughAutomation.food(stack) && water > 0;
    if (partialBucket || mixedContents) {
      if (!level.isClientSide())
        player.displayClientMessage(
            Component.translatable("aeroscapes_reimagine.husbandry.trough.whole_bucket"), true);
      callback.setReturnValue(ItemInteractionResult.FAIL);
    }
  }
}
