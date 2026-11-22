package dev.aeroscapes.equipment.expedition;

import dev.aeroscapes.progression.PracticeActions;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import top.theillusivec4.curios.api.CuriosApi;

public enum ExceptionalAccessory {
  INVISIBILITY(
      "borrowed_invisibility",
      "irons_spellbooks:invisibility_ring",
      "ring",
      SpellRegistry.INVISIBILITY_SPELL),
  FROSTWAVE(
      "borrowed_frostwave",
      "irons_spellbooks:frostward_ring",
      "ring",
      SpellRegistry.FROSTWAVE_SPELL),
  BREATH(
      "stored_breath", "crittersandcompanions:pearl_necklace_3", "necklace", SpellRegistry::none);

  private static final String MARK = "aeroscapes_reimagine:expedition_accessory";
  public final String id;
  public final String path;
  private final ResourceLocation item;
  private final String slot;
  private final Supplier<AbstractSpell> spell;

  ExceptionalAccessory(String path, String item, String slot, Supplier<AbstractSpell> spell) {
    this.id = "aeroscapes_reimagine:" + path;
    this.path = path;
    this.item = ResourceLocation.parse(item);
    this.slot = slot;
    this.spell = spell;
  }

  public Item item() {
    return BuiltInRegistries.ITEM.get(item);
  }

  public AbstractSpell spell() {
    return spell.get();
  }

  public boolean matches(ItemStack stack) {
    if (!stack.is(item())) return false;
    var data = stack.get(DataComponents.CUSTOM_DATA);
    return data != null && data.contains(MARK) && data.copyTag().getBoolean(MARK);
  }

  public ItemStack equipped(Player player) {
    var inventory = CuriosApi.getCuriosInventory(player).orElse(null);
    if (inventory == null) return ItemStack.EMPTY;
    var handler = inventory.getStacksHandler(slot).orElse(null);
    if (handler == null) return ItemStack.EMPTY;
    var stacks = handler.getStacks();
    // Curios caches named predicate results for one tick, including before a slot change.
    for (int index = 0; index < stacks.getSlots(); index++) {
      var stack = stacks.getStackInSlot(index);
      if (inventory.isSlotActive(slot, index) && matches(stack)) return stack;
    }
    return ItemStack.EMPTY;
  }

  public static ExceptionalAccessory byId(String id) {
    for (var accessory : values()) if (accessory.id.equals(id)) return accessory;
    return null;
  }

  public static ExceptionalAccessory bySpell(AbstractSpell spell) {
    if (spell == INVISIBILITY.spell()) return INVISIBILITY;
    if (spell == FROSTWAVE.spell()) return FROSTWAVE;
    return null;
  }

  public static CompoundTag mark() {
    var tag = new CompoundTag();
    tag.putBoolean(MARK, true);
    return tag;
  }

  public static boolean canMarkCraft(ItemStack result) {
    return result.getCount() == 1
        && result.getMaxStackSize() == 1
        && (result.is(FROSTWAVE.item()) || result.is(BREATH.item()));
  }

  public static void completeCraft(ItemStack result, Player player) {
    if (!canMarkCraft(result) || !PracticeActions.personal(player)) return;
    var crafter = (ServerPlayer) player;
    long mastery = Math.clamp(APIUtils.getLevel("crafting", crafter), 0, 45);
    // 2% on the first craft, rising to 11% at crafting level 45.
    if (crafter.getRandom().nextInt(10000) < 200 + mastery * 20)
      CustomData.update(DataComponents.CUSTOM_DATA, result, data -> data.putBoolean(MARK, true));
  }
}
