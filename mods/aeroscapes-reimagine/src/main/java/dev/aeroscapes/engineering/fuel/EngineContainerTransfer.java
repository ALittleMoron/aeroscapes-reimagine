package dev.aeroscapes.engineering.fuel;

import com.jesz.createdieselgenerators.CDGConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

public final class EngineContainerTransfer {
  private EngineContainerTransfer() {}

  public static @Nullable ItemInteractionResult transfer(
      ItemStack input, Level level, BlockPos pos, Player player, InteractionHand hand) {
    if (!CDGConfig.ENGINES_FILLED_WITH_ITEMS.get() || !ConservedFuel.isFluidContainer(input))
      return null;
    var destination = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
    if (destination == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    var result = ConservedFuel.emptyContainer(input, destination, !level.isClientSide);
    if (!result.isSuccess()) return ItemInteractionResult.FAIL;
    if (!level.isClientSide && !player.isCreative()) {
      if (input.getCount() == 1) player.setItemInHand(hand, result.getResult());
      else {
        input.shrink(1);
        player.getInventory().placeItemBackInInventory(result.getResult());
      }
    }
    return ItemInteractionResult.SUCCESS;
  }
}
