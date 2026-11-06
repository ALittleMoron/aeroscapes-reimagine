package dev.aeroscapes.movement;

import com.mojang.blaze3d.platform.InputConstants;
import com.yori3o.yo_hooks.common.YoHooksClient;
import com.yori3o.yo_hooks.common.util.interfaces.PlayerWithHookData;
import dev.aeroscapes.mixin.movement.HookToggleKeyAccess;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.loading.ClientModLoader;
import net.neoforged.neoforge.client.settings.KeyModifier;

@EventBusSubscriber(modid = "aeroscapes_reimagine", value = Dist.CLIENT)
public final class HookInputCompatibility {
  private static boolean bindingsChecked;

  private HookInputCompatibility() {}

  @SubscribeEvent
  public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
    HookHangingPose.reset();
    HookLineAttachment.reset();
  }

  @SubscribeEvent
  public static void setup(FMLClientSetupEvent event) {
    if (!ModList.get().isLoaded("yo_hooks")) return;
    event.enqueueWork(
        () -> {
          for (var expected : Map.of("yo_hooks", "5.4.0", "neoforge", "21.1.244").entrySet()) {
            String actual =
                ModList.get()
                    .getModContainerById(expected.getKey())
                    .orElseThrow()
                    .getModInfo()
                    .getVersion()
                    .toString();
            if (!expected.getValue().equals(actual))
              throw new IllegalStateException(
                  "Review hook input compatibility for " + expected.getKey() + " " + actual);
          }
        });
  }

  @SubscribeEvent
  public static void migrateControls(ClientTickEvent.Post event) {
    if (bindingsChecked || ClientModLoader.isLoading() || !ModList.get().isLoaded("yo_hooks"))
      return;
    var options = Minecraft.getInstance().options;
    var stored = (HookOptionsAccess) (Object) options;
    if (!stored.aeroscapes$hookMappingsLoaded()
        || !Arrays.asList(options.keyMappings)
            .containsAll(java.util.List.of(YoHooksClient.CLIMB, YoHooksClient.CLIMB_DOWN))) return;
    bindingsChecked = true;
    int version = stored.aeroscapes$hookControlsVersion();
    if (version != 0) return;
    if (HookControlsMigration.shouldMigrate(
        version,
        YoHooksClient.CLIMB.saveString(),
        YoHooksClient.CLIMB_DOWN.saveString(),
        YoHooksClient.CLIMB.getKeyModifier() == KeyModifier.NONE
            && YoHooksClient.CLIMB_DOWN.getKeyModifier() == KeyModifier.NONE)) {
      YoHooksClient.CLIMB.setKeyModifierAndCode(
          KeyModifier.NONE, InputConstants.getKey("key.keyboard.left.shift"));
      YoHooksClient.CLIMB_DOWN.setKeyModifierAndCode(
          KeyModifier.NONE, InputConstants.getKey("key.keyboard.left.control"));
      KeyMapping.resetMapping();
    }
    stored.aeroscapes$hookControlsVersion(HookControlsMigration.VERSION);
    options.save();
  }

  @SubscribeEvent
  public static void updateInput(MovementInputUpdateEvent event) {
    Player player = event.getEntity();
    var options = Minecraft.getInstance().options;
    if (consumesSneak(player)) {
      event.getInput().shiftKeyDown = false;
      if (options.toggleCrouch().get()) resetToggle(options.keyShift);
    }
    if (consumesSprint(player)) {
      player.setSprinting(false);
      if (options.toggleSprint().get()) resetToggle(options.keySprint);
    }
  }

  public static boolean consumesSneak(Player player) {
    return consumes(player, Minecraft.getInstance().options.keyShift);
  }

  public static boolean consumesSprint(Player player) {
    return consumes(player, Minecraft.getInstance().options.keySprint);
  }

  private static boolean consumes(Player player, KeyMapping vanillaKey) {
    if (!ModList.get().isLoaded("yo_hooks")
        || player != Minecraft.getInstance().player
        || !(player instanceof PlayerWithHookData data)) return false;
    var hook = data.yo_hooks$getHook();
    if (hook == null || hook.isRemoved() || !hook.isInBlock()) return false;
    return (YoHooksClient.CLIMB.isDown() && YoHooksClient.CLIMB.same(vanillaKey))
        || (YoHooksClient.CLIMB_DOWN.isDown() && YoHooksClient.CLIMB_DOWN.same(vanillaKey));
  }

  private static void resetToggle(KeyMapping key) {
    if (key instanceof ToggleKeyMapping toggle) ((HookToggleKeyAccess) toggle).aeroscapes$reset();
  }
}
