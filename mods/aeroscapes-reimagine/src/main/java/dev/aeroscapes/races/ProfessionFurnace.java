package dev.aeroscapes.races;

import java.util.Arrays;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public final class ProfessionFurnace {
  private static final String PORTIONS = "aeroscapes_cooking_input";
  private final ItemStack before;
  private final byte[] portions;

  public ProfessionFurnace(AbstractFurnaceBlockEntity furnace) {
    before = furnace.getItem(0).copy();
    portions = batch(furnace, before.getCount());
  }

  /**
   * A player's slot addition supplies only the new portions. Cooking consumes them in input order.
   */
  public void afterClick(AbstractFurnaceBlockEntity furnace, ServerPlayer cook, boolean exchanged) {
    ItemStack input = furnace.getItem(0);
    if (!exchanged && ItemStack.matches(before, input)) return;
    boolean same = !exchanged && ItemStack.isSameItemSameComponents(before, input);
    int count = input.getCount();
    byte[] next;
    if (!same) next = new byte[count];
    else if (count < portions.length)
      next = Arrays.copyOfRange(portions, portions.length - count, portions.length);
    else next = Arrays.copyOf(portions, count);
    int suppliedFrom = same ? before.getCount() : 0;
    if (suppliedFrom < next.length)
      Arrays.fill(next, suppliedFrom, next.length, (byte) ProfessionEffects.mealTraits(cook));
    store(furnace, next);
  }

  public static void replaceInput(AbstractFurnaceBlockEntity furnace, ItemStack replacement) {
    if (!furnace.getPersistentData().contains(PORTIONS, Tag.TAG_BYTE_ARRAY)) return;
    var previous = furnace.getItem(0);
    // A hopper can append to an existing stack. These extra entries have no cooking traits.
    if (!ItemStack.isSameItemSameComponents(previous, replacement)
        || previous.getCount() == replacement.getCount()) {
      furnace.getPersistentData().remove(PORTIONS);
      return;
    }
    byte[] existing = batch(furnace, previous.getCount());
    int count = replacement.getCount();
    store(
        furnace,
        count < existing.length
            ? Arrays.copyOfRange(existing, existing.length - count, existing.length)
            : Arrays.copyOf(existing, count));
  }

  public static ItemStack prepare(AbstractFurnaceBlockEntity furnace, ItemStack result) {
    if (!furnace.getPersistentData().contains(PORTIONS, Tag.TAG_BYTE_ARRAY)) return result;
    var portions = furnace.getPersistentData().getByteArray(PORTIONS);
    int count = furnace.getItem(0).getCount();
    if (portions.length == 0 || count < portions.length) return result;
    return ProfessionEffects.prepare(result, portions[0]);
  }

  public static void removed(AbstractFurnaceBlockEntity furnace, int count) {
    if (count == 0 || !furnace.getPersistentData().contains(PORTIONS, Tag.TAG_BYTE_ARRAY)) return;
    int beforeCount = furnace.getItem(0).getCount() + count;
    store(furnace, Arrays.copyOfRange(batch(furnace, beforeCount), count, beforeCount));
  }

  private static byte[] batch(AbstractFurnaceBlockEntity furnace, int count) {
    byte[] saved = furnace.getPersistentData().getByteArray(PORTIONS);
    return saved.length > count ? new byte[count] : Arrays.copyOf(saved, count);
  }

  private static void store(AbstractFurnaceBlockEntity furnace, byte[] portions) {
    for (byte portion : portions) {
      if (portion == 0) continue;
      furnace.getPersistentData().putByteArray(PORTIONS, portions);
      furnace.setChanged();
      return;
    }
    furnace.getPersistentData().remove(PORTIONS);
    furnace.setChanged();
  }
}
