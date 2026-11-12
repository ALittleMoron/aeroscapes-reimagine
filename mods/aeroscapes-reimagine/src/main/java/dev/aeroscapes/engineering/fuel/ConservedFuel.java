package dev.aeroscapes.engineering.fuel;

import com.forsteri.createliquidfuel.core.IHasStomach;
import com.forsteri.createliquidfuel.core.LiquidFuels;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import dev.aeroscapes.mixin.engineering.fuel.BurnerFuelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class ConservedFuel {
  private ConservedFuel() {}

  public static InteractionResultHolder<ItemStack> insert(
      Level level, BlockPos pos, ItemStack input, boolean creative, boolean simulate) {
    if (!(level.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity burner)
        || !(burner instanceof IHasStomach stomachOwner)
        || burner.isCreative()) return InteractionResultHolder.fail(ItemStack.EMPTY);
    var stomach = stomachOwner.getCapability();
    if (stomach == null) return InteractionResultHolder.fail(ItemStack.EMPTY);
    var transfer = emptyContainer(input, stomach, !simulate && !level.isClientSide);
    if (!transfer.isSuccess()) return InteractionResultHolder.fail(ItemStack.EMPTY);
    if (creative) return InteractionResultHolder.success(ItemStack.EMPTY);
    // Create's mechanical arm simulates against a working copy and observes its count.
    if (!level.isClientSide) input.shrink(1);
    return InteractionResultHolder.success(transfer.getResult());
  }

  public static void tick(BlazeBurnerBlockEntity burner) {
    var level = burner.getLevel();
    if (level == null
        || level.isClientSide
        || burner.isCreative()
        || burner.getActiveFuel() == BlazeBurnerBlockEntity.FuelType.SPECIAL
        || !(burner instanceof IHasStomach owner)) return;
    var stomach = owner.getCapability();
    if (stomach == null || stomach.isEmpty()) return;
    var properties = LiquidFuels.get(stomach.getFluid().getFluid());
    if (properties == null
        || properties.superHeat()
        || properties.burnTime() <= 0
        || properties.amountConsumedPerTick() <= 0
        || stomach.getFluidAmount() < properties.amountConsumedPerTick()
        || properties.burnTime()
            > BlazeBurnerBlockEntity.MAX_HEAT_CAPACITY - burner.getRemainingBurnTime()) return;
    var access = (BurnerFuelAccess) burner;
    stomach.drain(
        properties.amountConsumedPerTick(),
        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
    access.aeroscapes$setActiveFuel(BlazeBurnerBlockEntity.FuelType.NORMAL);
    access.aeroscapes$setRemainingBurnTime(burner.getRemainingBurnTime() + properties.burnTime());
    access.aeroscapes$setBlockHeat(BlazeBurnerBlock.HeatLevel.KINDLED);
    burner.setChanged();
  }

  public static boolean isFluidContainer(ItemStack stack) {
    return stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
  }

  public static FluidActionResult emptyContainer(
      ItemStack input, IFluidHandler destination, boolean execute) {
    var source = input.copyWithCount(1).getCapability(Capabilities.FluidHandler.ITEM);
    if (source == null) return FluidActionResult.FAILURE;
    var available = source.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
    if (available.isEmpty()) return FluidActionResult.FAILURE;
    int accepted = destination.fill(available, IFluidHandler.FluidAction.SIMULATE);
    if (accepted <= 0) return FluidActionResult.FAILURE;
    var requested = available.copyWithAmount(accepted);
    var drainable = source.drain(requested, IFluidHandler.FluidAction.SIMULATE);
    // FluidUtil's simulation alone accepts partial buckets that cannot actually be drained.
    if (drainable.getAmount() != accepted
        || !FluidStack.isSameFluidSameComponents(requested, drainable))
      return FluidActionResult.FAILURE;
    return FluidUtil.tryEmptyContainer(
        input.copyWithCount(1), destination, Integer.MAX_VALUE, null, execute);
  }
}
