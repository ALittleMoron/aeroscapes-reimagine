package dev.aeroscapes.survival;

import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class SurvivalConfigScreens {
  private SurvivalConfigScreens() {}

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    event.enqueueWork(
        () -> {
          for (var expected :
              Map.of("legendarysurvivaloverhaul", "2.4.7.2", "nutritionalbalance", "1.21.1-7.0.3")
                  .entrySet()) {
            var container = ModList.get().getModContainerById(expected.getKey()).orElseThrow();
            String actual = container.getModInfo().getVersion().toString();
            if (!expected.getValue().equals(actual))
              throw new IllegalStateException(
                  "Review survival configuration screen for " + expected.getKey() + " " + actual);
            if (container.getCustomExtension(IConfigScreenFactory.class).isEmpty())
              container.registerExtensionPoint(
                  IConfigScreenFactory.class, ConfigurationScreen::new);
          }
        });
  }
}
