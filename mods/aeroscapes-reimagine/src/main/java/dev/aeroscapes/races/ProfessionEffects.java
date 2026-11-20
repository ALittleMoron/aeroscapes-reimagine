package dev.aeroscapes.races;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class ProfessionEffects {
  public static final IntegerProperty FEAST_TRAITS =
      IntegerProperty.create("aeroscapes_meal", 0, 3);
  private static final int HEARTY = 1;
  private static final int QUICK = 2;
  private static final String MEAL = "aeroscapes_profession_meal";
  private static final TagKey<Item> PRODUCE = tag("aeroscapes_reimagine:profession_crop_yield");
  private static final TagKey<Item> UNPREPARED = tag("aeroscapes_reimagine:unprepared_food");

  private ProfessionEffects() {}

  private static TagKey<Item> tag(String id) {
    return TagKey.create(Registries.ITEM, ResourceLocation.parse(id));
  }

  public static void register() {
    NeoForge.EVENT_BUS.addListener(ProfessionEffects::tooltip);
  }

  public static boolean learned(Entity actor, String node) {
    return actor instanceof ServerPlayer player
        && !(player instanceof FakePlayer)
        && ProfessionProgression.learned(player, node);
  }

  public static int mealTraits(Entity player) {
    return (learned(player, "hearty_meal") ? HEARTY : 0)
        | (learned(player, "quick_meal") ? QUICK : 0);
  }

  public static int preparedTraits(ItemStack stack) {
    var data = stack.get(DataComponents.CUSTOM_DATA);
    return data == null || !data.contains(MEAL) ? 0 : data.copyTag().getInt(MEAL);
  }

  public static boolean isFeast(ItemStack stack) {
    return stack.getItem() instanceof BlockItem item
        && item.getBlock().defaultBlockState().hasProperty(FEAST_TRAITS);
  }

  public static ItemStack prepare(ItemStack result, int traits) {
    if (traits == 0 || result.is(UNPREPARED) || preparedTraits(result) != 0) {
      return result;
    }
    FoodProperties food = result.get(DataComponents.FOOD);
    if (food == null && !isFeast(result)) return result;
    ItemStack meal = result.copy();
    if (food == null) {
      var state = meal.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
      meal.set(DataComponents.BLOCK_STATE, state.with(FEAST_TRAITS, traits));
    } else
      meal.set(
          DataComponents.FOOD,
          new FoodProperties(
              food.nutrition(),
              food.saturation() + ((traits & HEARTY) != 0 ? 1 : 0),
              food.canAlwaysEat(),
              food.eatSeconds(),
              food.usingConvertsTo(),
              food.effects()));
    CustomData.update(DataComponents.CUSTOM_DATA, meal, data -> data.putInt(MEAL, traits));
    return meal;
  }

  public static int eatingTime(ItemStack dish, int ordinaryTicks) {
    return (preparedTraits(dish) & QUICK) != 0
        ? Math.max(1, Math.round(ordinaryTicks * 0.75F))
        : ordinaryTicks;
  }

  /** A stove or campfire position contains one serving. A placed skillet cooks a full stack. */
  public static void startCooking(BlockEntity station, int slot, Entity cook, int count) {
    var data = station.getPersistentData();
    String key = "aeroscapes_cook_slot_" + slot;
    int traits = mealTraits(cook);
    if (traits == 0 || count == 0) data.remove(key);
    else {
      var batch = new net.minecraft.nbt.CompoundTag();
      batch.putInt("traits", traits);
      batch.putInt("count", count);
      data.put(key, batch);
    }
  }

  public static void cookingSlotChanged(BlockEntity station, int slot, int remaining) {
    var data = station.getPersistentData();
    String key = "aeroscapes_cook_slot_" + slot;
    if (!data.contains(key)) return;
    var batch = data.getCompound(key);
    if (remaining > 0 && remaining < batch.getInt("count")) batch.putInt("count", remaining);
    else data.remove(key);
  }

  public static ItemStack cookedServing(BlockEntity station, int slot, ItemStack result) {
    var data = station.getPersistentData();
    String key = "aeroscapes_cook_slot_" + slot;
    return data.contains(key) ? prepare(result, data.getCompound(key).getInt("traits")) : result;
  }

  private static void tooltip(ItemTooltipEvent event) {
    int traits = preparedTraits(event.getItemStack());
    if ((traits & HEARTY) != 0)
      event.getToolTip().add(Component.translatable("tooltip.aeroscapes_reimagine.hearty_meal"));
    if ((traits & QUICK) != 0)
      event.getToolTip().add(Component.translatable("tooltip.aeroscapes_reimagine.quick_meal"));
  }

  public static int repairUnit(Player player, int menuId, int restored) {
    // Easy Anvils compares two private vanilla probes (menu ID -1) before choosing its route.
    return menuId >= 0 && learned(player, "material_repair") ? restored + restored / 4 : restored;
  }

  public static void finishRepair(AnvilMenu menu, Player player) {
    ItemStack target = menu.getSlot(0).getItem();
    ItemStack material = menu.getSlot(1).getItem();
    ItemStack output = menu.getSlot(2).getItem();
    if (menu.repairItemCountCost <= 0
        || material.isEmpty()
        || material.isDamageableItem()
        || !output.is(target.getItem())
        || target.getDamageValue() <= output.getDamageValue()) return;
    if (menu.getCost() > 1 && learned(player, "frugal_repair"))
      menu.setMaximumCost(menu.getCost() - 1);
  }

  public static boolean mature(BlockState state) {
    if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
    if (state.is(Blocks.NETHER_WART)) return state.getValue(NetherWartBlock.AGE) == 3;
    if (state.is(Blocks.COCOA)) return state.getValue(CocoaBlock.AGE) == 2;
    if (state.is(Blocks.SWEET_BERRY_BUSH)) return state.getValue(SweetBerryBushBlock.AGE) == 3;
    return CaveVines.hasGlowBerries(state);
  }

  public static boolean extraHarvest(Entity actor) {
    return learned(actor, "extra_harvest") && actor.getRandom().nextInt(10) == 0;
  }

  public static void addProduce(List<ItemStack> drops, Entity actor) {
    if (!extraHarvest(actor)) return;
    for (ItemStack drop : drops) {
      if (drop.is(PRODUCE) && !drop.is(Items.POISONOUS_POTATO)) {
        drop.grow(1);
        return;
      }
    }
  }
}
