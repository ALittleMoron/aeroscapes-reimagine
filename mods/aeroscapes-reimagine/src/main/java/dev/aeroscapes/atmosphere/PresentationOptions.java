package dev.aeroscapes.atmosphere;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class PresentationOptions {
  private PresentationOptions() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    ModList.get()
        .getModContainerById("aeroscapes_reimagine")
        .orElseThrow()
        .registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
  }

  @SubscribeEvent
  public static void reloaded(ModConfigEvent.Reloading event) {
    if (event.getConfig().getSpec() != PresentationConfig.SPEC) return;
    var client = Minecraft.getInstance();
    client.execute(
        () -> {
          if (ItemModelResources.enabled() != ItemModelResources.requested())
            client.reloadResourcePacks();
        });
  }
}
