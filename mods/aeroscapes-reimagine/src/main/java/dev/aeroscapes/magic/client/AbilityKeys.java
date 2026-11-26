package dev.aeroscapes.magic.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aeroscapes.magic.AbilityLoadout;
import dev.aeroscapes.magic.SpellMouseControls;
import io.redspace.ironsspellbooks.player.KeyMappings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Existing provider remappings and optional pack bindings share one guarded input route. */
@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class AbilityKeys {
  private static final KeyMapping CAST = binding("cast");
  private static final KeyMapping WHEEL = binding("wheel");
  private static final List<KeyMapping> SLOTS = slots();
  private static final AbilityInputPresses PRESSES = new AbilityInputPresses();
  private static boolean processed;
  private static int capturedWheelKeys;
  private static WheelOwner wheelOwner;

  private enum Action {
    HOLD_WHEEL,
    TOGGLE_WHEEL,
    QUICK_CAST,
    CAST,
    SELECT
  }

  private record Request(KeyMapping binding, Action action, int slot) {}

  private record WheelOwner(
      KeyMapping binding, boolean toggle, LocalPlayer player, ClientLevel level) {}

  private AbilityKeys() {}

  private static KeyMapping binding(String name) {
    return new KeyMapping(
        "key.aeroscapes_reimagine.abilities." + name,
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        InputConstants.UNKNOWN.getValue(),
        "key.categories.aeroscapes_reimagine.abilities");
  }

  private static List<KeyMapping> slots() {
    var result = new ArrayList<KeyMapping>();
    for (int slot = 1; slot <= AbilityClient.SLOT_COUNT; slot++)
      result.add(binding("slot_" + slot));
    return List.copyOf(result);
  }

  @SubscribeEvent
  public static void register(RegisterKeyMappingsEvent event) {
    if (!ModList.get().isLoaded("irons_spellbooks")) return;
    event.register(CAST);
    event.register(WHEEL);
    SLOTS.forEach(event::register);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void begin(ClientTickEvent.Pre event) {
    processed = false;
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void tick(ClientTickEvent.Post event) {
    if (ModList.get().isLoaded("irons_spellbooks")) forwardNativeBindings();
  }

  /** Called before the provider consumes its bindings, without skipping its maintenance code. */
  public static void forwardNativeBindings() {
    if (processed) return;
    processed = true;
    updateWheel();
    var requests = new ArrayList<Request>();
    consume(WHEEL, Action.HOLD_WHEEL, -1, requests);
    if (KeyMappings.SPELL_WHEEL_KEYMAP.consume())
      requests.add(new Request(KeyMappings.SPELL_WHEEL_KEYMAP, Action.HOLD_WHEEL, -1));
    if (KeyMappings.SPELL_WHEEL_TOGGLE_KEYMAP.consume())
      requests.add(new Request(KeyMappings.SPELL_WHEEL_TOGGLE_KEYMAP, Action.TOGGLE_WHEEL, -1));
    for (int slot = 0; slot < KeyMappings.QUICK_CAST_MAPPINGS.size(); slot++) {
      var binding = KeyMappings.QUICK_CAST_MAPPINGS.get(slot);
      if (binding.consume()) requests.add(new Request(binding, Action.QUICK_CAST, slot));
    }
    consume(CAST, Action.CAST, -1, requests);
    if (KeyMappings.SPELLBOOK_CAST_ACTIVE_KEYMAP.consume())
      requests.add(new Request(KeyMappings.SPELLBOOK_CAST_ACTIVE_KEYMAP, Action.CAST, -1));
    for (int slot = 0; slot < SLOTS.size(); slot++)
      consume(SLOTS.get(slot), Action.SELECT, slot, requests);
    PRESSES.begin(
        bindings().stream()
            .filter(AbilityKeys::physicallyDown)
            .map(binding -> binding.getKey().getName())
            .toList());
    for (var request : requests) {
      String activatedAbility =
          switch (request.action()) {
            case QUICK_CAST -> AbilityClient.slot(request.slot());
            case CAST -> AbilityClient.slot(AbilityClient.selectedSlot());
            default -> "";
          };
      if (PRESSES.claim(request.binding().getKey().getName())
          && SpellMouseControls.canUseAbilityBinding(request.binding(), activatedAbility))
        dispatch(request);
    }
  }

  private static List<KeyMapping> bindings() {
    var result = new ArrayList<KeyMapping>(SLOTS);
    result.add(CAST);
    result.add(WHEEL);
    result.add(KeyMappings.SPELLBOOK_CAST_ACTIVE_KEYMAP);
    result.add(KeyMappings.SPELL_WHEEL_KEYMAP);
    result.add(KeyMappings.SPELL_WHEEL_TOGGLE_KEYMAP);
    result.addAll(KeyMappings.QUICK_CAST_MAPPINGS);
    return result;
  }

  private static boolean physicallyDown(KeyMapping binding) {
    var key = binding.getKey();
    if (key.getValue() < 0) return false;
    long window = Minecraft.getInstance().getWindow().getWindow();
    return switch (key.getType()) {
      case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
      case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
      case SCANCODE -> binding.isDown();
    };
  }

  public static boolean scroll(int delta) {
    if (delta == 0
        || AbilityWheel.active()
        || !SpellMouseControls.canUseAbilityBinding(KeyMappings.SPELLBAR_SCROLL_MODIFIER_KEYMAP))
      return false;
    AbilityClient.select(
        Math.floorMod(AbilityClient.selectedSlot() - delta, AbilityClient.SLOT_COUNT));
    return true;
  }

  private static void consume(KeyMapping binding, Action action, int slot, List<Request> requests) {
    boolean clicked = false;
    while (binding.consumeClick()) clicked = true;
    if (clicked) requests.add(new Request(binding, action, slot));
  }

  private static void dispatch(Request request) {
    if (request.action() == Action.TOGGLE_WHEEL && wheelOwner != null) {
      if (wheelOwner.toggle()) {
        AbilityWheel.release();
        wheelOwner = null;
      }
      return;
    }
    if (AbilityWheel.active()) return;
    switch (request.action()) {
      case HOLD_WHEEL, TOGGLE_WHEEL -> {
        if (request.action() == Action.HOLD_WHEEL && !request.binding().isDown()) return;
        var game = Minecraft.getInstance();
        AbilityWheel.open();
        if (AbilityWheel.active())
          wheelOwner =
              new WheelOwner(
                  request.binding(),
                  request.action() == Action.TOGGLE_WHEEL,
                  game.player,
                  game.level);
      }
      case QUICK_CAST -> {
        if (request.slot() < AbilityClient.SLOT_COUNT) {
          AbilityClient.select(request.slot());
          AbilityClient.cast();
        }
      }
      case CAST -> AbilityClient.cast();
      case SELECT -> AbilityClient.select(request.slot());
    }
  }

  private static void updateWheel() {
    if (wheelOwner == null) return;
    if (!wheelContextValid()) cancelWheel(true);
    else if (!wheelOwner.toggle() && !wheelOwner.binding().isDown()) {
      AbilityWheel.release();
      wheelOwner = null;
    }
  }

  private static boolean wheelContextValid() {
    if (wheelOwner == null) return false;
    var game = Minecraft.getInstance();
    return AbilityWheel.active()
        && SpellMouseControls.canUseAbilityBinding(wheelOwner.binding())
        && !game.mouseHandler.isMouseGrabbed()
        && !game.options.hideGui
        && game.player == wheelOwner.player()
        && game.level == wheelOwner.level();
  }

  /** Called before Minecraft and other bindings queue the same number-key press. */
  public static boolean wheelKey(int key, int action) {
    int wheel = key - GLFW.GLFW_KEY_1;
    if (!AbilityLoadout.validWheel(wheel)) return false;
    int mask = 1 << wheel;
    if ((capturedWheelKeys & mask) != 0 && action != GLFW.GLFW_PRESS) {
      if (action == GLFW.GLFW_RELEASE) capturedWheelKeys &= ~mask;
      return true;
    }
    if (action != GLFW.GLFW_PRESS) return false;
    capturedWheelKeys &= ~mask;
    if (!SpellMouseControls.ownsOpenWheel() && !wheelContextValid()) return false;
    if (wheelOwner != null
        && wheelOwner.toggle()
        && wheelOwner.binding().getKey().equals(InputConstants.Type.KEYSYM.getOrCreate(key)))
      return false;
    capturedWheelKeys |= mask;
    AbilityWheel.showWheel(wheel);
    return true;
  }

  private static void cancelWheel(boolean restoreMouse) {
    if (wheelOwner != null) AbilityWheel.cancel(restoreMouse);
    wheelOwner = null;
  }

  @SubscribeEvent
  public static void screen(ScreenEvent.Opening event) {
    if (event.getNewScreen() != null) cancelWheel(false);
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    cancelWheel(false);
    PRESSES.reset();
  }

  @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
  public static void mouse(InputEvent.MouseButton.Pre event) {
    var key = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
    if (event.getAction() == GLFW.GLFW_RELEASE) {
      PRESSES.release(key.getName());
      if (wheelOwner != null && !wheelOwner.toggle() && wheelOwner.binding().getKey().equals(key)) {
        if (!event.isCanceled() && wheelContextValid()) AbilityWheel.release();
        else AbilityWheel.cancel(!event.isCanceled());
        wheelOwner = null;
      }
      return;
    }
    if (wheelOwner == null || event.getAction() != GLFW.GLFW_PRESS) return;
    if (event.isCanceled() || !wheelOwner.binding().isActiveAndMatches(key)) {
      cancelWheel(false);
      return;
    }
    if (!wheelContextValid()) {
      cancelWheel(false);
      return;
    }
    if (wheelOwner.toggle()) {
      // Vanilla regrabs and recenters before the key edge, so commit this toggle in Pre.
      event.setCanceled(true);
      PRESSES.claim(key.getName());
      AbilityWheel.release();
      wheelOwner = null;
    }
  }
}
