package dev.aeroscapes.progression.client;

import harmonised.pmmo.setup.ClientSetup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * Registers Project MMO's key mappings, which 2.10.47 creates but never registers.
 *
 * <p>Unregistered mappings still react to their default keys (Left Alt, Tab, brackets, minus, grave
 * accent and P) but are missing from Controls and options, so players can neither see nor rebind
 * them. Registration makes the pack's defaults and player choices persist.
 */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class ProjectMmoKeys {
  private ProjectMmoKeys() {}

  @SubscribeEvent
  public static void registerKeys(RegisterKeyMappingsEvent event) {
    if (!ModList.get().isLoaded("pmmo")) return;
    event.register(ClientSetup.OPEN_MENU);
    event.register(ClientSetup.SHOW_LIST);
    event.register(ClientSetup.TOGGLE_BRKSPD);
    event.register(ClientSetup.VEIN_KEY);
    event.register(ClientSetup.SHOW_VEIN);
    event.register(ClientSetup.ADD_VEIN);
    event.register(ClientSetup.SUB_VEIN);
    event.register(ClientSetup.CYCLE_VEIN);
  }
}
