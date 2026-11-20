package dev.aeroscapes.races;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

public final class ProfessionPot {
  private static final String INGREDIENTS = "aeroscapes_profession_ingredients";
  private final List<ItemStack> before = new ArrayList<>(CookingPotBlockEntity.MEAL_DISPLAY_SLOT);
  private final CompoundTag credits;

  public ProfessionPot(CookingPotBlockEntity pot) {
    credits = pot.getPersistentData().getCompound(INGREDIENTS).copy();
    for (int slot = 0; slot < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; slot++) {
      before.add(pot.getInventory().getStackInSlot(slot).copy());
    }
  }

  /** The pot consumes one ingredient per occupied slot in insertion order. */
  public void afterClick(
      CookingPotBlockEntity pot, @Nullable ServerPlayer cook, int exchangedSlot) {
    int traits = ProfessionEffects.mealTraits(cook);
    var supplied = new CompoundTag();
    if (cook != null && traits != 0) {
      supplied.putUUID("cook", cook.getUUID());
      supplied.putInt("traits", traits);
    }
    for (int slot = 0; slot < before.size(); slot++) {
      String key = Integer.toString(slot);
      ItemStack old = before.get(slot);
      ItemStack current = pot.getInventory().getStackInSlot(slot);
      if (slot != exchangedSlot && ItemStack.matches(old, current)) continue;
      var portions = credits.getList(key, Tag.TAG_COMPOUND);
      int oldCount = old.getCount();
      if (slot == exchangedSlot || !ItemStack.isSameItemSameComponents(old, current)) {
        portions = new ListTag();
        oldCount = 0;
      }
      // Unrecorded units have no personal source, including old saves and automation.
      while (portions.size() < oldCount) portions.add(new CompoundTag());
      while (portions.size() > current.getCount()) portions.remove(0);
      while (portions.size() < current.getCount()) portions.add(supplied.copy());
      storeSlot(credits, key, portions);
    }
    store(pot, credits);
  }

  private static void storeSlot(CompoundTag credits, String slot, ListTag portions) {
    if (portions.stream().anyMatch(entry -> !((CompoundTag) entry).isEmpty()))
      credits.put(slot, portions);
    else credits.remove(slot);
  }

  private static void store(CookingPotBlockEntity pot, CompoundTag credits) {
    if (credits.isEmpty()) pot.getPersistentData().remove(INGREDIENTS);
    else pot.getPersistentData().put(INGREDIENTS, credits);
    pot.setChanged();
  }

  public static boolean tracks(CookingPotBlockEntity pot, int slot) {
    return slot < CookingPotBlockEntity.MEAL_DISPLAY_SLOT
        && pot.getPersistentData().getCompound(INGREDIENTS).contains(Integer.toString(slot));
  }

  /**
   * Replacing a stack directly clears its provenance. Insert/extract and menu operations reconcile
   * their actual deltas.
   */
  public static void changed(CookingPotBlockEntity pot, int slot) {
    if (!tracks(pot, slot)) return;
    var credits = pot.getPersistentData().getCompound(INGREDIENTS);
    credits.remove(Integer.toString(slot));
    store(pot, credits);
  }

  public static void consumed(CookingPotBlockEntity pot) {
    if (!pot.getPersistentData().contains(INGREDIENTS)) return;
    var credits = pot.getPersistentData().getCompound(INGREDIENTS);
    for (int slot = 0; slot < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; slot++) {
      String key = Integer.toString(slot);
      var portions = credits.getList(key, Tag.TAG_COMPOUND);
      if (!portions.isEmpty()) {
        portions.remove(0);
        storeSlot(credits, key, portions);
      }
    }
    store(pot, credits);
  }

  public static int traits(CookingPotBlockEntity pot) {
    if (!pot.getPersistentData().contains(INGREDIENTS)) return 0;
    var credits = pot.getPersistentData().getCompound(INGREDIENTS);
    UUID cook = null;
    int traits = 3;
    for (int slot = 0; slot < CookingPotBlockEntity.MEAL_DISPLAY_SLOT; slot++) {
      if (pot.getInventory().getStackInSlot(slot).isEmpty()) continue;
      var portions = credits.getList(Integer.toString(slot), Tag.TAG_COMPOUND);
      if (portions.isEmpty()) return 0;
      var entry = portions.getCompound(0);
      if (!entry.hasUUID("cook")) return 0;
      var source = entry.getUUID("cook");
      if (cook != null && !cook.equals(source)) return 0;
      cook = source;
      traits &= entry.getInt("traits");
    }
    return cook == null ? 0 : traits;
  }
}
