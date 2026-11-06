package dev.aeroscapes.movement;

import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Keeps old stack identities while removing the unselected companion grappling tool. */
public final class RemovedCompanionHook {
  private static final ResourceLocation ITEM =
      ResourceLocation.fromNamespaceAndPath("crittersandcompanions", "grappling_hook");

  private RemovedCompanionHook() {}

  public static void register(IEventBus modBus) {
    modBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, RemovedCompanionHook::creative);
    NeoForge.EVENT_BUS.addListener(RemovedCompanionHook::use);
    NeoForge.EVENT_BUS.addListener(RemovedCompanionHook::tooltip);
  }

  private static boolean removed(ItemStack stack) {
    return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ITEM);
  }

  private static void creative(BuildCreativeModeTabContentsEvent event) {
    Stream.concat(event.getParentEntries().stream(), event.getSearchEntries().stream())
        .filter(RemovedCompanionHook::removed)
        .toList()
        .forEach(
            stack -> event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
  }

  private static void use(PlayerInteractEvent.RightClickItem event) {
    if (!removed(event.getItemStack())) return;
    event.setCancellationResult(InteractionResult.FAIL);
    event.setCanceled(true);
  }

  private static void tooltip(ItemTooltipEvent event) {
    if (removed(event.getItemStack()))
      event
          .getToolTip()
          .add(
              Component.translatable("aeroscapes_reimagine.equipment.companion_hook_disabled")
                  .withStyle(ChatFormatting.GRAY));
  }
}
