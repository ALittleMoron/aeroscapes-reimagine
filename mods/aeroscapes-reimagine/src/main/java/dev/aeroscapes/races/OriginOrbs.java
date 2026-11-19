package dev.aeroscapes.races;

import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class OriginOrbs {
  public static final TagKey<Item> HIDDEN =
      TagKey.create(Registries.ITEM, ResourceLocation.parse("aeroscapes_reimagine:origin_orbs"));

  private OriginOrbs() {}

  @SubscribeEvent
  public static void use(PlayerInteractEvent.RightClickItem event) {
    if (!event.getItemStack().is(HIDDEN)) return;
    event.setCancellationResult(InteractionResult.FAIL);
    event.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void creative(BuildCreativeModeTabContentsEvent event) {
    Stream.concat(event.getParentEntries().stream(), event.getSearchEntries().stream())
        .filter(stack -> stack.is(HIDDEN))
        .toList()
        .forEach(
            stack -> event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
  }
}
