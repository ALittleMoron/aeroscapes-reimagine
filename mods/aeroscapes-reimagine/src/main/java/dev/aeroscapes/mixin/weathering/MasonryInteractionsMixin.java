package dev.aeroscapes.mixin.weathering;

import com.ordana.immersive_weathering.blocks.cracked.Crackable;
import com.ordana.immersive_weathering.configs.CommonConfigs;
import com.ordana.immersive_weathering.events.ModEvents;
import com.ordana.immersive_weathering.reg.ModItems;
import dev.aeroscapes.weathering.MasonryWeathering;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModEvents.class, remap = false)
public abstract class MasonryInteractionsMixin {
  @Inject(method = "pickaxeCracking", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The provider's masonry lacks IW's Crackable interface.
  private static void aeroscapes$crackMasonry(
      Item item,
      ItemStack stack,
      BlockPos pos,
      BlockState state,
      Player player,
      Level level,
      InteractionHand hand,
      BlockHitResult hit,
      CallbackInfoReturnable<InteractionResult> result) {
    if (!(item instanceof PickaxeItem) || !MasonryWeathering.cracks(state)) return;
    if (!CommonConfigs.PICKAXE_CRACKING.get()
        || CommonConfigs.PICKAXE_CRACKING_SHIFT.get() && !player.isSecondaryUseActive()) return;
    BlockState cracked = Crackable.getCrackedBlock(state);
    if (cracked == state) return;
    result.setReturnValue(aeroscapes$changeMasonry(stack, pos, player, level, hand, cracked, true));
  }

  @Inject(method = "brickRepair", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$repairMasonry(
      Item item,
      ItemStack stack,
      BlockPos pos,
      BlockState state,
      Player player,
      Level level,
      InteractionHand hand,
      BlockHitResult hit,
      CallbackInfoReturnable<InteractionResult> result) {
    if (item != ModItems.MORTAR.get() || !MasonryWeathering.repairs(state)) return;
    BlockState repaired = Crackable.getUncrackedCrackBlock(state);
    if (repaired == state) return;
    result.setReturnValue(
        aeroscapes$changeMasonry(stack, pos, player, level, hand, repaired, false));
  }

  @Unique
  private static InteractionResult aeroscapes$changeMasonry(
      ItemStack stack,
      BlockPos pos,
      Player player,
      Level level,
      InteractionHand hand,
      BlockState replacement,
      boolean cracking) {
    if (level.isClientSide) return InteractionResult.SUCCESS;
    if (!level.setBlockAndUpdate(pos, replacement)) return InteractionResult.FAIL;
    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger((ServerPlayer) player, pos, stack);
    player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
    if (!player.getAbilities().instabuild) {
      if (cracking) stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
      else stack.shrink(1);
    }
    var sound = replacement.getSoundType(level, pos, player);
    level.playSound(
        null,
        pos,
        cracking ? sound.getHitSound() : sound.getPlaceSound(),
        SoundSource.BLOCKS,
        sound.getVolume(),
        sound.getPitch());
    return InteractionResult.CONSUME;
  }
}
