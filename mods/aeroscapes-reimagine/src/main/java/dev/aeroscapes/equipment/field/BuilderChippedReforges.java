package dev.aeroscapes.equipment.field;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class BuilderChippedReforges {
  private static final Map<ItemStack, String> FILTERS = new WeakHashMap<>();
  private static Opening opening;

  private BuilderChippedReforges() {}

  public static void opening(Player player, ItemStack stack) {
    if (ReforgeProfiles.path(stack).startsWith("builder_chisel_")) {
      opening =
          new Opening(
              new WeakReference<>(player), new WeakReference<>(stack), player.tickCount + 20);
    } else {
      opening = null;
    }
  }

  public static ItemStack opened(Player player) {
    var pending = opening;
    opening = null;
    if (pending == null
        || player != pending.player().get()
        || player.tickCount > pending.expires()) {
      return ItemStack.EMPTY;
    }
    ItemStack tool = pending.tool().get();
    return tool != null && (player.getMainHandItem() == tool || player.getOffhandItem() == tool)
        ? tool
        : ItemStack.EMPTY;
  }

  public static String filter(ItemStack stack) {
    return FILTERS.getOrDefault(stack, "");
  }

  public static void setFilter(ItemStack stack, String filter) {
    if (filter.isEmpty()) FILTERS.remove(stack);
    else FILTERS.put(stack, filter);
  }

  private record Opening(
      WeakReference<Player> player, WeakReference<ItemStack> tool, int expires) {}
}
