package dev.aeroscapes.equipment.combat;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

final class StackCombatWindows {
  // The readiness belongs to the original stack and ends when that live stack is discarded.
  private static final Map<ItemStack, Window> WINDOWS = new WeakHashMap<>();

  private StackCombatWindows() {}

  record Window(UUID owner, ResourceKey<Level> dimension, long expires, UUID firstTarget) {}

  static Window get(ServerPlayer player, ItemStack stack) {
    Window window = WINDOWS.get(stack);
    return window != null
            && window.owner().equals(player.getUUID())
            && window.dimension().equals(player.level().dimension())
            && window.expires() > player.level().getGameTime()
        ? window
        : null;
  }

  static void start(ServerPlayer player, ItemStack stack, int duration, UUID firstTarget) {
    WINDOWS.put(
        stack,
        new Window(
            player.getUUID(),
            player.level().dimension(),
            player.level().getGameTime() + duration,
            firstTarget));
  }

  static boolean consume(ServerPlayer player, ItemStack stack) {
    if (get(player, stack) == null) {
      return false;
    }
    WINDOWS.remove(stack);
    return true;
  }
}
