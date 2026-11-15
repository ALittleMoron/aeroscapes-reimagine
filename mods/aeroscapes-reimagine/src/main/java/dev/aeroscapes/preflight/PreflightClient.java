package dev.aeroscapes.preflight;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class PreflightClient {
  private static boolean enabled;
  private static final KeyMapping OPEN =
      new KeyMapping(
          "key.aeroscapes_preflight.open",
          KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM,
          InputConstants.UNKNOWN.getValue(),
          "key.categories.aeroscapes_preflight");

  private PreflightClient() {}

  @SubscribeEvent
  public static void registerKeys(RegisterKeyMappingsEvent event) {
    enabled = ModList.get().isLoaded("aeronautics") && ModList.get().isLoaded("sable");
    if (enabled) event.register(OPEN);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    if (!enabled) return;
    Minecraft game = Minecraft.getInstance();
    boolean requested = false;
    while (OPEN.consumeClick()) requested = true;
    if (requested && game.player != null && game.screen == null)
      game.setScreen(new PreflightScreen());
  }

  public static void receive(PreflightNetwork.Snapshot snapshot) {
    Minecraft game = Minecraft.getInstance();
    if (game.level != null
        && game.level.dimension().location().equals(snapshot.dimension())
        && game.screen instanceof PreflightScreen screen) screen.receive(snapshot);
  }
}
