package dev.aeroscapes.races.client;

import com.cyberday1.neoorigins.screen.OriginInfoScreen;
import com.cyberday1.neoorigins.screen.PickerScreen;
import dev.aeroscapes.inventory.InventoryNavigation;
import dev.aeroscapes.races.CharacterClass;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class RaceVisuals {
  private static final String COMMAND = "aeroscapes_race";
  private static boolean openWhenReady;
  @Nullable private static RaceScreen firstChoice;

  private RaceVisuals() {}

  static void requestChoice(RaceClient.Profile profile, boolean changed) {
    if (firstChoice == null || changed) firstChoice = new RaceScreen(profile, false);
    else firstChoice.declined();
  }

  static MutableComponent text(String key, Object... args) {
    return Component.translatable("aeroscapes_reimagine.race." + key, args);
  }

  private static boolean choiceKnown() {
    var player = Minecraft.getInstance().player;
    return player != null && RaceClient.profile(player.getUUID()) != null;
  }

  private static boolean pending(Minecraft game) {
    var profile = game.player == null ? null : RaceClient.profile(game.player.getUUID());
    return profile != null && !profile.complete();
  }

  static void openFromInventory() {
    var game = Minecraft.getInstance();
    if (game.player == null || !choiceKnown() || !InventoryNavigation.openInventory()) return;
    openWhenReady = false;
    var profile = RaceClient.profile(game.player.getUUID());
    if (profile != null)
      game.setScreen(profile.complete() ? new RaceScreen(profile, true) : firstChoice);
  }

  private static boolean changingWorld(@Nullable Screen screen) {
    return screen instanceof DeathScreen
        || screen instanceof DisconnectedScreen
        || screen instanceof LevelLoadingScreen
        || screen instanceof ReceivingLevelScreen
        || screen instanceof ProgressScreen
        || screen instanceof GenericMessageScreen;
  }

  private static boolean ready(Minecraft game) {
    return game.player != null
        && game.level != null
        && game.getOverlay() == null
        && game.player.isAlive()
        && !game.player.isRemoved();
  }

  @SubscribeEvent
  public static void opening(ScreenEvent.Opening event) {
    if (!CharacterClass.creationAvailable()) return;
    var next = event.getNewScreen();
    var game = Minecraft.getInstance();
    if (firstChoice != null
        && ready(game)
        && !changingWorld(event.getCurrentScreen())
        && pending(game)
        && !changingWorld(next)) {
      if (next != firstChoice) {
        if (event.getCurrentScreen() == firstChoice) event.setCanceled(true);
        else event.setNewScreen(firstChoice);
      }
      return;
    }
    if (!(next instanceof OriginInfoScreen || next instanceof PickerScreen)) return;
    if (event.getCurrentScreen() instanceof RaceScreen) {
      event.setCanceled(true);
      return;
    }
    if (!ready(game) || changingWorld(event.getCurrentScreen()) || !choiceKnown()) {
      event.setCanceled(true);
      openWhenReady = true;
      return;
    }
    var profile = RaceClient.profile(game.player.getUUID());
    openWhenReady = false;
    if (profile != null)
      event.setNewScreen(
          new RaceScreen(profile, event.getCurrentScreen() instanceof InventoryScreen));
  }

  @SubscribeEvent
  public static void registerCommand(RegisterClientCommandsEvent event) {
    if (!CharacterClass.creationAvailable()) return;
    event
        .getDispatcher()
        .register(
            Commands.literal(COMMAND)
                .executes(
                    context -> {
                      var game = Minecraft.getInstance();
                      if (!choiceKnown()) return 0;
                      openWhenReady = true;
                      if (game.screen instanceof ChatScreen) game.setScreen(null);
                      return 1;
                    }));
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    var game = Minecraft.getInstance();
    if (firstChoice == null && !openWhenReady || !ready(game) || changingWorld(game.screen)) return;
    var profile = RaceClient.profile(game.player.getUUID());
    if (firstChoice != null && profile != null && !profile.complete()) {
      openWhenReady = false;
      if (game.player.containerMenu != game.player.inventoryMenu) game.player.closeContainer();
      if (game.screen != firstChoice) game.setScreen(firstChoice);
    } else if (profile != null && profile.complete()) {
      firstChoice = null;
      if (openWhenReady && game.screen == null) {
        openWhenReady = false;
        game.setScreen(new RaceScreen(profile, false));
      }
    }
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    openWhenReady = false;
    firstChoice = null;
  }

  @SubscribeEvent
  public static void addLayers(EntityRenderersEvent.AddLayers event) {
    for (var skin : event.getSkins()) {
      if (event.getSkin(skin) instanceof PlayerRenderer renderer)
        renderer.addLayer(new RaceFeatures(renderer));
    }
  }
}
