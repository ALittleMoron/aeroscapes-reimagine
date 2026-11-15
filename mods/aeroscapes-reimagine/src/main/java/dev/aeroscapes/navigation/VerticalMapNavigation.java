package dev.aeroscapes.navigation;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;
import xaero.map.gui.GuiMap;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class VerticalMapNavigation {
  private static final String CATEGORY = "key.categories.aeroscapes_navigation";
  private static final KeyMapping UP = binding("up", GLFW.GLFW_KEY_PAGE_UP);
  private static final KeyMapping DOWN = binding("down", GLFW.GLFW_KEY_PAGE_DOWN);
  private static final KeyMapping FOLLOW = binding("follow", GLFW.GLFW_KEY_HOME);
  private static final MapLayerSelection SELECTION = new MapLayerSelection();
  private static volatile View view;

  public record View(int ceiling, int bottom, MapLayerSelection.Mode mode) {}

  private VerticalMapNavigation() {}

  private static KeyMapping binding(String name, int key) {
    return new KeyMapping(
        "key.aeroscapes_navigation." + name,
        KeyConflictContext.UNIVERSAL,
        KeyModifier.ALT,
        InputConstants.Type.KEYSYM,
        key,
        CATEGORY);
  }

  @SubscribeEvent
  public static void registerKeys(RegisterKeyMappingsEvent event) {
    event.register(UP);
    event.register(DOWN);
    event.register(FOLLOW);
  }

  @SubscribeEvent
  public static void tick(ClientTickEvent.Post event) {
    update();
    Minecraft game = Minecraft.getInstance();
    boolean changed = false;
    while (UP.consumeClick()) {
      if (view != null && game.screen == null) {
        move(MapLayerSelection.STEP);
        changed = true;
      }
    }
    while (DOWN.consumeClick()) {
      if (view != null && game.screen == null) {
        move(-MapLayerSelection.STEP);
        changed = true;
      }
    }
    while (FOLLOW.consumeClick()) {
      if (view != null && game.screen == null) {
        toggleFollowing();
        changed = true;
      }
    }
    if (changed && game.player != null) game.player.displayClientMessage(description(), true);
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    view = null;
    SELECTION.reset();
  }

  private static void update() {
    Minecraft game = Minecraft.getInstance();
    if (game.level == null || game.player == null || game.level.dimension() != Level.OVERWORLD) {
      view = null;
      return;
    }
    int minimum = game.level.getMinBuildHeight();
    int ceiling =
        SELECTION.ceiling(game.player.getBlockY(), minimum, game.level.getMaxBuildHeight() - 1);
    view =
        new View(
            ceiling, Math.max(minimum, ceiling + 1 - MapLayerSelection.DEPTH), SELECTION.mode());
  }

  public static View view() {
    return view;
  }

  public static boolean usesLayers(Level level, View current) {
    return current != null
        && current.mode() != MapLayerSelection.Mode.NATIVE
        && level != null
        && level.dimension() == Level.OVERWORLD;
  }

  public static Component description() {
    View current = view;
    if (current == null || current.mode() == MapLayerSelection.Mode.NATIVE)
      return Component.translatable("gui.aeroscapes_navigation.native");
    return Component.translatable(
        current.mode() == MapLayerSelection.Mode.FOLLOW
            ? "gui.aeroscapes_navigation.follow"
            : "gui.aeroscapes_navigation.manual",
        current.bottom(),
        current.ceiling());
  }

  @SubscribeEvent
  public static void screenKey(ScreenEvent.KeyPressed.Pre event) {
    if (ModList.get().isLoaded("xaeroworldmap")
        && event.getScreen() instanceof GuiMap map
        && map.getMapProcessor().getMapWorld().getCurrentDimensionId() == Level.OVERWORLD
        && !(event.getScreen().getFocused() instanceof EditBox)
        && handleKey(event.getKeyCode(), event.getScanCode())) event.setCanceled(true);
  }

  private static boolean handleKey(int keyCode, int scanCode) {
    if (view == null) return false;
    InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
    if (UP.isActiveAndMatches(key)) move(MapLayerSelection.STEP);
    else if (DOWN.isActiveAndMatches(key)) move(-MapLayerSelection.STEP);
    else if (FOLLOW.isActiveAndMatches(key)) toggleFollowing();
    else return false;
    Minecraft game = Minecraft.getInstance();
    if (game.player != null) game.player.displayClientMessage(description(), true);
    return true;
  }

  private static void move(int amount) {
    Minecraft game = Minecraft.getInstance();
    if (game.level == null || game.player == null) return;
    SELECTION.move(
        amount,
        game.player.getBlockY(),
        game.level.getMinBuildHeight(),
        game.level.getMaxBuildHeight() - 1);
    update();
  }

  private static void toggleFollowing() {
    SELECTION.toggleFollowing();
    update();
  }

  @SubscribeEvent
  public static void mapButtons(ScreenEvent.Init.Post event) {
    if (!ModList.get().isLoaded("xaeroworldmap")
        || !(event.getScreen() instanceof GuiMap map)
        || map.getMapProcessor().getMapWorld().getCurrentDimensionId() != Level.OVERWORLD
        || view == null) return;
    int left = 24;
    int top = 6;
    var status = new LayerModeButton(left + 22, top);
    status.setTooltip(Tooltip.create(Component.translatable("gui.aeroscapes_navigation.help")));
    event.addListener(status);
    event.addListener(
        Button.builder(Component.literal("−"), button -> move(-MapLayerSelection.STEP))
            .bounds(left, top, 20, 20)
            .tooltip(Tooltip.create(Component.translatable("gui.aeroscapes_navigation.lower")))
            .build());
    event.addListener(
        Button.builder(Component.literal("+"), button -> move(MapLayerSelection.STEP))
            .bounds(left + 194, top, 20, 20)
            .tooltip(Tooltip.create(Component.translatable("gui.aeroscapes_navigation.higher")))
            .build());
  }

  @SubscribeEvent
  public static void renderMap(ScreenEvent.Render.Pre event) {
    if (!ModList.get().isLoaded("xaeroworldmap") || !(event.getScreen() instanceof GuiMap)) return;
    for (var child : event.getScreen().children()) {
      if (child instanceof LayerModeButton button) button.setMessage(description());
    }
  }

  private static final class LayerModeButton extends Button {
    private LayerModeButton(int x, int y) {
      super(x, y, 170, 20, description(), button -> toggleFollowing(), DEFAULT_NARRATION);
    }
  }
}
