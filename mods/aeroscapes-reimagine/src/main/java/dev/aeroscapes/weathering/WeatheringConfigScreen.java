package dev.aeroscapes.weathering;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class WeatheringConfigScreen {
  private WeatheringConfigScreen() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(
        () ->
            ModList.get()
                .getModContainerById("immersive_weathering")
                .ifPresent(
                    container -> {
                      if (!"1.0.4".equals(container.getModInfo().getVersion().toString()))
                        throw new IllegalStateException("Re-audit weathering configuration screen");
                      if (container.getCustomExtension(IConfigScreenFactory.class).isEmpty())
                        container.registerExtensionPoint(
                            IConfigScreenFactory.class, ConfigurationScreen::new);
                    }));
  }
}
