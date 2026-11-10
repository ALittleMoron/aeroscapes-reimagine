package dev.aeroscapes.equipment.emi;

import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class ReforgeEmiReload {
  private static boolean pending;

  private ReforgeEmiReload() {}

  public static void invalidate() {
    pending = true;
  }

  @SubscribeEvent
  public static void tagsUpdated(TagsUpdatedEvent event) {
    if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED
        && ModList.get().isLoaded("tieredneo")
        && ModList.get().isLoaded("emi")) {
      invalidate();
    }
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    // TieredNeo and tag data can arrive before a client level exists.
    if (pending && Minecraft.getInstance().level != null) {
      pending = false;
      EmiReloadManager.reload();
    }
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    pending = false;
  }
}
