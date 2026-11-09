package dev.aeroscapes.mixin.reforge.tools;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.equipment.tools.AreaToolActions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerPlayerGameMode.class)
abstract class AreaToolActionsMixin {
  @Shadow protected ServerLevel level;
  @Shadow @Final protected ServerPlayer player;
  @Unique private boolean aeroscapes$workingArea;

  @WrapMethod(method = "destroyBlock")
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$mineArea(BlockPos pos, Operation<Boolean> original) {
    if (aeroscapes$workingArea) return original.call(pos);
    var pattern = AreaToolActions.miningPattern(player);
    if (pattern == null) return original.call(pos);
    aeroscapes$workingArea = true;
    try {
      return AreaToolActions.mine(
          (ServerPlayerGameMode) (Object) this,
          player,
          level,
          pos,
          pattern,
          () -> original.call(pos));
    } finally {
      aeroscapes$workingArea = false;
    }
  }

  @WrapMethod(method = "useItemOn")
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private InteractionResult aeroscapes$useToolInArea(
      ServerPlayer user,
      Level world,
      ItemStack stack,
      InteractionHand hand,
      BlockHitResult hit,
      Operation<InteractionResult> original) {
    if (aeroscapes$workingArea || !(world instanceof ServerLevel serverLevel))
      return original.call(user, world, stack, hand, hit);
    var pattern = AreaToolActions.usePattern(user, stack, hand);
    if (pattern == null) return original.call(user, world, stack, hand, hit);
    aeroscapes$workingArea = true;
    try {
      return AreaToolActions.use(
          (ServerPlayerGameMode) (Object) this,
          user,
          serverLevel,
          stack,
          hand,
          hit,
          pattern,
          () -> original.call(user, world, stack, hand, hit));
    } finally {
      aeroscapes$workingArea = false;
    }
  }
}
