package dev.aeroscapes.qualityoflife;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.CreativeModeTabSearchRegistry;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Prepare the native creative lists and their indices before the first inventory opens. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class CreativeSearchPreparation {
  private CreativeSearchPreparation() {}

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    var client = Minecraft.getInstance();
    var connection = client.getConnection();
    if (connection == null
        || client.level == null
        || client.player == null
        || client.gameMode == null
        || !client.gameMode.hasInfiniteItems()) return;
    boolean operatorItems =
        client.options.operatorItemsTab().get() && client.player.canUseGameMasterBlocks();
    if (!CreativeModeTabs.tryRebuildTabContents(
        connection.enabledFeatures(), operatorItems, client.level.registryAccess())) return;
    for (var tab : CreativeModeTabs.allTabs()) {
      if (!tab.hasSearchBar()) continue;
      var items = List.copyOf(tab.getDisplayItems());
      connection
          .searchTrees()
          .updateCreativeTooltips(
              client.level.registryAccess(),
              items,
              CreativeModeTabSearchRegistry.getNameSearchKey(tab));
      connection
          .searchTrees()
          .updateCreativeTags(items, CreativeModeTabSearchRegistry.getTagSearchKey(tab));
    }
  }
}
