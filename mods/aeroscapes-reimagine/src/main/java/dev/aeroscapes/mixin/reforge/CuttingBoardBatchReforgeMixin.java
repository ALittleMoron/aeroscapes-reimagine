package dev.aeroscapes.mixin.reforge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import vectorwing.farmersdelight.common.block.CuttingBoardBlock;
import vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity;

@Mixin(value = CuttingBoardBlock.class, remap = false)
abstract class CuttingBoardBatchReforgeMixin {
  @Unique private static final ThreadLocal<Boolean> aeroscapes$batchCut = new ThreadLocal<>();

  @WrapOperation(
      method = "useItemOn",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lvectorwing/farmersdelight/common/block/entity/CuttingBoardBlockEntity;processStoredItemUsingTool(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)Z"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$cutBatch(
      CuttingBoardBlockEntity board,
      ItemStack knife,
      Player user,
      Operation<Boolean> original,
      @Local(argsOnly = true) InteractionHand hand) {
    boolean cut = original.call(board, knife, user);
    if (!cut
        || !(user instanceof ServerPlayer player)
        || !player.isShiftKeyDown()
        || Boolean.TRUE.equals(aeroscapes$batchCut.get())
        || !ReforgeProfiles.has(knife, "knife_legendary_batch")) return cut;
    var side = player.getDirection().getClockWise();
    var level = player.serverLevel();
    aeroscapes$batchCut.set(true);
    try {
      for (var direction : new Direction[] {side, side.getOpposite()}) {
        var pos = board.getBlockPos().relative(direction);
        if (knife.isEmpty() || player.getItemInHand(hand) != knife) break;
        if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
            && level.mayInteract(player, pos)
            && player.canInteractWithBlock(pos, 0.0)
            && level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity next
            && !next.getStoredItem().isEmpty()) {
          var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
          player.gameMode.useItemOn(player, level, knife, hand, hit);
        }
      }
    } finally {
      aeroscapes$batchCut.remove();
    }
    return cut;
  }
}
