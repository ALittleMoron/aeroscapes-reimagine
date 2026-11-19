package dev.aeroscapes.inventory;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

public final class AccessoryUse {
  private AccessoryUse() {}

  @Nullable
  public static InteractionResultHolder<ItemStack> equip(
      Player player, InteractionHand hand, ItemStack held) {
    if (held.isEmpty() || player.isSpectator() || player.getItemInHand(hand) != held) return null;
    var inventory = CuriosApi.getCuriosInventory(player);
    if (inventory.isEmpty()) return null;
    var single = held.copyWithCount(1);
    Destination swap = null;
    for (var entry : inventory.get().getCurios().entrySet()) {
      var slot = entry.getValue();
      var stacks = slot.getStacks();
      var active = slot.getActiveStates();
      var renders = slot.getRenders();
      for (int index = 0; index < stacks.getSlots(); index++) {
        if (index >= active.size()
            || !active.get(index)
            || stacks.getSlotLimit(index) < 1
            || !stacks.isItemValid(index, single)) continue;
        var old = stacks.getStackInSlot(index);
        var context =
            new SlotContext(
                entry.getKey(), player, index, false, index < renders.size() && renders.get(index));
        var destination = new Destination(stacks, context);
        if (old.isEmpty()) return destination.wear(player, hand, held, single);
        if (swap == null
            && held.getCount() == 1
            && !ItemStack.isSameItemSameComponents(old, held)
            && stacks.extractItem(index, old.getCount(), true).getCount() == old.getCount())
          swap = destination;
      }
    }
    return swap == null ? null : swap.wear(player, hand, held, single);
  }

  private record Destination(IDynamicStackHandler stacks, SlotContext context) {
    InteractionResultHolder<ItemStack> wear(
        Player player, InteractionHand hand, ItemStack held, ItemStack single) {
      if (player.level().isClientSide()) return InteractionResultHolder.success(held);
      var old = stacks.getStackInSlot(context.index());
      var remaining = old.isEmpty() ? held.copyWithCount(held.getCount() - 1) : old.copy();
      stacks.setStackInSlot(context.index(), single);
      player.setItemInHand(hand, remaining);
      CuriosApi.getCurio(single).ifPresent(curio -> curio.onEquipFromUse(context));
      return InteractionResultHolder.consume(remaining);
    }
  }

  @EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
  public static final class Hint {
    private Hint() {}

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
      var player = event.getEntity();
      if (ModList.get().isLoaded("curios")
          && player != null
          && !CuriosApi.getItemStackSlots(event.getItemStack(), player).isEmpty()) {
        event
            .getToolTip()
            .add(
                Component.translatable("aeroscapes_reimagine.accessory.equip_hint")
                    .withStyle(ChatFormatting.GRAY));
      }
    }
  }
}
