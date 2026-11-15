package dev.aeroscapes.husbandry;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.athisworkshop.animalhusbandry.block.FeedingTroughBlockEntity;
import org.athisworkshop.animalhusbandry.registry.ModBlockEntities;

public final class TroughAutomation {
  private TroughAutomation() {}

  public static void register(IEventBus modBus) {
    modBus.addListener(TroughAutomation::capabilities);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TroughAutomation::onTroughClick);
  }

  private static void capabilities(RegisterCapabilitiesEvent event) {
    event.registerBlockEntity(
        Capabilities.ItemHandler.BLOCK,
        ModBlockEntities.FEEDING_TROUGH_BE.get(),
        (trough, side) -> new FoodHandler(trough));
    event.registerBlockEntity(
        Capabilities.FluidHandler.BLOCK,
        ModBlockEntities.FEEDING_TROUGH_BE.get(),
        (trough, side) -> new WaterHandler(trough));
  }

  public static boolean food(ItemStack stack) {
    return stack.is(Items.WHEAT)
        || stack.is(Items.CARROT)
        || stack.is(Items.WHEAT_SEEDS)
        || stack.is(ItemTags.LEAVES)
        || stack.is(ItemTags.FLOWERS)
        || stack.is(ItemTags.SAPLINGS)
        || stack.is(ItemTags.TALL_FLOWERS)
        || stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS);
  }

  public static int waterUnits(FeedingTroughBlockEntity trough) {
    int remainder = ((TroughWaterAccess) trough).aeroscapes$waterRemainder();
    return Math.clamp(trough.waterLevel * 1000 + remainder, 0, 16000);
  }

  private static void waterUnits(FeedingTroughBlockEntity trough, int units) {
    trough.waterLevel = units / 1000;
    ((TroughWaterAccess) trough).aeroscapes$waterRemainder(units % 1000);
    trough.updateVisualState();
  }

  public static void spill(FeedingTroughBlockEntity trough) {
    waterUnits(trough, 0);
  }

  private static void onTroughClick(PlayerInteractEvent.RightClickBlock event) {
    var player = event.getEntity();
    var stack = event.getItemStack();
    var level = event.getLevel();
    var position = event.getPos();
    if (!player.isShiftKeyDown()
        || player.isSpectator()
        || !stack.is(Items.BUCKET)
        || event.getUseBlock().isFalse()
        || event.getUseItem().isFalse()
        || !level.mayInteract(player, position)
        || !player.mayUseItemAt(position, event.getHitVec().getDirection(), stack)
        || !(level.getBlockEntity(position) instanceof FeedingTroughBlockEntity trough)) return;
    int water = waterUnits(trough);
    if (water <= 0 || water >= 16000) return;
    if (!level.isClientSide()) {
      spill(trough);
      level.playSound(null, position, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
      player.displayClientMessage(
          Component.translatable("aeroscapes_reimagine.husbandry.trough.spilled"), true);
    }
    event.setCanceled(true);
    event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
  }

  private static final class FoodHandler extends InvWrapper {
    private final FeedingTroughBlockEntity trough;

    private FoodHandler(FeedingTroughBlockEntity trough) {
      super(trough);
      this.trough = trough;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
      return slot == 0 && food(stack) && waterUnits(trough) == 0;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
      return isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;
    }
  }

  private static final class WaterHandler implements IFluidHandler {
    private final FeedingTroughBlockEntity trough;

    private WaterHandler(FeedingTroughBlockEntity trough) {
      this.trough = trough;
    }

    @Override
    public int getTanks() {
      return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
      int amount = tank == 0 ? waterUnits(trough) / 16 : 0;
      return amount == 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, amount);
    }

    @Override
    public int getTankCapacity(int tank) {
      return tank == 0 ? 1000 : 0;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
      return tank == 0 && stack.is(Fluids.WATER);
    }

    @Override
    public int fill(FluidStack stack, FluidAction action) {
      if (!isFluidValid(0, stack) || !trough.isEmpty()) return 0;
      int before = waterUnits(trough);
      int accepted = Math.min(stack.getAmount(), (16000 - before) / 16);
      if (accepted > 0 && action.execute()) waterUnits(trough, before + accepted * 16);
      return accepted;
    }

    @Override
    public FluidStack drain(FluidStack stack, FluidAction action) {
      return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int amount, FluidAction action) {
      return FluidStack.EMPTY;
    }
  }
}
