package dev.aeroscapes.magic;

import com.mojang.blaze3d.platform.InputConstants;
import com.yori3o.yo_hooks.common.util.interfaces.PlayerWithHookData;
import dev.aeroscapes.equipment.combat.MartialTechnique;
import dev.aeroscapes.magic.client.AbilityClient;
import dev.aeroscapes.magic.client.AbilityWheel;
import dev.ryanhcode.sable.mixinhelpers.camera.new_camera_types.SableCameraTypes;
import dev.simulated_team.simulated.SimulatedClient;
import dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;
import team.creative.littletiles.api.common.tool.ILittleTool;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class SpellMouseControls {
  private SpellMouseControls() {}

  public static boolean canUseAbilityBinding(KeyMapping binding) {
    return canUseAbilityBinding(binding, "");
  }

  public static boolean canUseAbilityBinding(KeyMapping binding, String ability) {
    var game = Minecraft.getInstance();
    return Input.press == null
        && Input.canUse(game, ability)
        && (game.mouseHandler.isMouseGrabbed() || AbilityWheel.active())
        && !(binding.getKey().equals(Input.MIDDLE_BUTTON)
            && (Screen.hasControlDown() || Input.creativePickOwnsButton(game)));
  }

  public static boolean ownsOpenWheel() {
    return Input.press != null
        && Input.GESTURE.ownsWheel()
        && Input.canContinue(Minecraft.getInstance());
  }

  @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
  public static void mouseButton(InputEvent.MouseButton.Pre event) {
    if (!ModList.get().isLoaded("irons_spellbooks")) return;
    if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
      Input.button(Minecraft.getInstance(), event);
    } else if (!event.isCanceled() && event.getAction() == GLFW.GLFW_PRESS) {
      Input.cancel(Minecraft.getInstance(), false);
    }
  }

  @SubscribeEvent
  public static void renderFrame(RenderFrameEvent.Pre event) {
    if (ModList.get().isLoaded("irons_spellbooks")) Input.update(Minecraft.getInstance());
  }

  @SubscribeEvent
  public static void initializeScreen(ScreenEvent.Init.Post event) {
    if (ModList.get().isLoaded("irons_spellbooks")) Input.cancel(Minecraft.getInstance(), false);
  }

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    if (ModList.get().isLoaded("irons_spellbooks")) Input.cancel(Minecraft.getInstance(), false);
  }

  private static final class Input {
    private static final InputConstants.Key MIDDLE_BUTTON =
        InputConstants.Type.MOUSE.getOrCreate(GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
    private static final SpellMouseGesture GESTURE = new SpellMouseGesture();
    private static Press press;

    private record Press(LocalPlayer player, ClientLevel level, String selected) {}

    private static void button(Minecraft game, InputEvent.MouseButton.Pre event) {
      if (event.getAction() == GLFW.GLFW_RELEASE && press != null) {
        if (event.isCanceled() || !canContinue(game)) {
          cancel(game, true);
          return;
        }
        String initial = press.selected();
        press = null;
        event.setCanceled(true);
        switch (GESTURE.release(Util.getMillis())) {
          case CAST -> {
            if (!initial.isEmpty() && initial.equals(selectedAbility())) AbilityClient.cast();
          }
          case SELECT -> AbilityWheel.release();
          case NONE -> {}
        }
        return;
      }
      if (event.isCanceled()
          || event.getAction() != GLFW.GLFW_PRESS
          || press != null
          || !game.mouseHandler.isMouseGrabbed()
          || !canUseMiddle(game, selectedAbility())
          || creativePickOwnsButton(game)
          || AbilityWheel.active()
          || anotherBindingOwnsButton(game)) return;
      press = new Press(game.player, game.level, selectedAbility());
      GESTURE.press(Util.getMillis());
      event.setCanceled(true);
    }

    private static void update(Minecraft game) {
      if (press == null) return;
      if (!canContinue(game)) {
        cancel(game, true);
        return;
      }
      if (GESTURE.shouldOpenWheel(Util.getMillis())
          && !game.options.hideGui
          && GLFW.glfwGetMouseButton(game.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE)
              == GLFW.GLFW_PRESS) {
        AbilityWheel.open();
        if (AbilityWheel.active()) GESTURE.wheelOpened();
      }
    }

    private static String selectedAbility() {
      return AbilityClient.slot(AbilityClient.selectedSlot());
    }

    private static boolean canContinue(Minecraft game) {
      return game.player == press.player()
          && game.level == press.level()
          && canUseMiddle(game, press.selected())
          && (GESTURE.ownsWheel()
              ? AbilityWheel.active()
                  && !game.options.hideGui
                  && !game.mouseHandler.isMouseGrabbed()
              : game.mouseHandler.isMouseGrabbed() && !AbilityWheel.active());
    }

    private static boolean canUse(Minecraft game, String ability) {
      LocalPlayer player = game.player;
      return player != null
          && game.level != null
          && game.isWindowActive()
          && game.screen == null
          && game.getOverlay() == null
          && !game.options.touchscreen().get()
          && player.isAlive()
          && !player.isSpectator()
          && !player.isPassenger()
          && (!player.isUsingItem() || worksWhileUsing(player, ability))
          && !toolOwnsInput(game, player);
    }

    private static boolean worksWhileUsing(LocalPlayer player, String ability) {
      var technique = MartialTechnique.byId(ability);
      if (technique == null) return false;
      return switch (technique) {
        case SHIELD_BRACE -> player.isBlocking();
        case SLOWING_SHOT, MARKING_SHOT, QUICK_SHOT -> player.getUseItem().is(Items.BOW);
        default -> false;
      };
    }

    private static boolean canUseMiddle(Minecraft game, String ability) {
      return canUse(game, ability) && !Screen.hasControlDown();
    }

    private static boolean creativePickOwnsButton(Minecraft game) {
      return game.player.getAbilities().instabuild
          && game.options.keyPickItem.isActiveAndMatches(MIDDLE_BUTTON)
          && game.hitResult != null
          && game.hitResult.getType() != HitResult.Type.MISS;
    }

    private static boolean anotherBindingOwnsButton(Minecraft game) {
      for (var binding : game.options.keyMappings) {
        if (binding != game.options.keyPickItem && binding.isActiveAndMatches(MIDDLE_BUTTON)) {
          return true;
        }
      }
      return false;
    }

    private static boolean toolOwnsInput(Minecraft game, LocalPlayer player) {
      var mods = ModList.get();
      if (mods.isLoaded("littletiles") && player.getMainHandItem().getItem() instanceof ILittleTool)
        return true;
      if (mods.isLoaded("sable")
          && (game.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW
              || game.options.getCameraType() == SableCameraTypes.SUB_LEVEL_VIEW_UNLOCKED))
        return true;
      if (mods.isLoaded("simulated")
          && (HoldInteractionManager.isActive()
              || SimulatedClient.PHYSICS_STAFF_CLIENT_HANDLER.getDragSession() != null))
        return true;
      if (mods.isLoaded("yo_hooks") && player instanceof PlayerWithHookData hookData) {
        var hook = hookData.yo_hooks$getHook();
        return hook != null && !hook.isRemoved() && hook.isInBlock() && hook.isControlledBy(player);
      }
      return false;
    }

    private static void cancel(Minecraft game, boolean restoreMouse) {
      boolean owned = GESTURE.ownsWheel() && AbilityWheel.active();
      press = null;
      GESTURE.cancel();
      if (!owned) return;
      AbilityWheel.cancel(restoreMouse);
    }
  }
}
