package dev.aeroscapes.sky;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class WhaleConfigScreen {
  private WhaleConfigScreen() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(
        () -> {
          var container = ModList.get().getModContainerById("skywhales").orElseThrow();
          String actual = container.getModInfo().getVersion().toString();
          if (!actual.equals("0.1.9")) {
            throw new IllegalStateException("Review Sky Whales configuration screen for " + actual);
          }
          if (container.getCustomExtension(IConfigScreenFactory.class).isEmpty()) {
            container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
          }
        });
  }
}
