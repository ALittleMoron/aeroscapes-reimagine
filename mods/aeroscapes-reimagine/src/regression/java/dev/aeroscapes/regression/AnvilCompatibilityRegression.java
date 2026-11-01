package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import fuzs.easyanvils.EasyAnvils;
import fuzs.easyanvils.config.ServerConfig;
import fuzs.easyanvils.world.inventory.ModAnvilMenu;
import fuzs.easyanvils.world.inventory.state.AnvilMenuState;
import fuzs.easyanvils.world.inventory.state.BuiltInAnvilMenu;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/** Real menu outputs versus transformed native baseline; no custom-recipe simulation. */
public final class AnvilCompatibilityRegression {
  private AnvilCompatibilityRegression() {}

  /** Call immediately after assigning this live ModAnvilMenu to player.containerMenu. */
  public static void verify(ModAnvilMenu menu, ServerPlayer player, ServerLevel level) {
    var configuration = EasyAnvils.CONFIG.get(ServerConfig.class);
    LogUtils.getLogger()
        .info(
            "Anvil policy config: freeRenames={} tooExpensiveLimit={} repairCostMode={} priorWork={}",
            configuration.costs.freeRenames,
            configuration.costs.tooExpensiveLimit,
            configuration.priorWorkPenalty.renameAndRepairCosts,
            configuration.priorWorkPenalty.priorWorkPenalty);
    try {
      for (int prior : new int[] {0, 7, 40}) {
        ItemStack left = new ItemStack(Items.IRON_SWORD);
        left.set(DataComponents.REPAIR_COST, prior);
        apply(menu, left, ItemStack.EMPTY, "Anvil Policy Test");
        diagnose(menu, player, "rename-prior-" + prior);
        ItemStack output = menu.getSlot(2).getItem();
        require(menu.getCost() == 0 && !output.isEmpty(), "Pure rename is free at prior " + prior);
        require(
            output.getOrDefault(DataComponents.REPAIR_COST, 0) == prior
                && output.getHoverName().getString().equals("Anvil Policy Test"),
            "Pure rename preserves prior penalty and correct name");
      }

      for (int prior : new int[] {0, 40}) {
        ItemStack left = pickaxe(false, prior);
        ItemStack right = new ItemStack(Items.DIAMOND);
        compareNative(menu, player, left, right, "", "ordinary-repair-prior-" + prior);
        require(menu.getCost() == prior + 1, "Ordinary repair cost retains full prior penalty");
      }

      // Right-nonempty differences must retain the native custom-recipe path.
      for (int prior : new int[] {7, 40}) {
        ItemStack left = pickaxe(true, prior);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        compareNative(menu, player, left, diamond, "", "quark-diamond-repair-" + prior);
        require(menu.getCost() == prior + 1, "Quark diamond repair retains native full cost");
        compareNative(
            menu, player, left, diamond, "Combined Repair", "quark-diamond-rename-" + prior);
        require(menu.getCost() == prior + 2, "Custom combined repair/name retains native cost");
      }

      // Ordinary high-cost combined operation also stays on the existing native fallback.
      compareNative(
          menu,
          player,
          pickaxe(false, 40),
          new ItemStack(Items.DIAMOND),
          "Combined Repair",
          "ordinary-high-combined");
      require(menu.getCost() == 42, "High ordinary combined repair/name retains native cost42");

      var sharpness =
          level
              .registryAccess()
              .lookupOrThrow(Registries.ENCHANTMENT)
              .getOrThrow(Enchantments.SHARPNESS);
      ItemStack sword = new ItemStack(Items.IRON_SWORD);
      sword.enchant(sharpness, sharpness.value().getMaxLevel());
      sword.set(DataComponents.REPAIR_COST, 7);
      ItemStack tome = ancientTome(sharpness);
      compareNative(menu, player, sword, tome, "", "quark-ancient-tome");
      require(
          menu.getCost() == 30
              && EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(2).getItem())
                      .getLevel(sharpness)
                  == 6,
          "Native tome fee and SharpnessVI are preserved");
      compareNative(menu, player, sword, tome, "Named Tome Upgrade", "quark-tome-with-name");
      require(menu.getCost() == 31, "Custom tome+name retains native cost31");
    } finally {
      menu.getSlot(0).set(ItemStack.EMPTY);
      menu.getSlot(1).set(ItemStack.EMPTY);
      menu.setItemName("");
      menu.createResult();
    }
    LogUtils.getLogger()
        .info(
            "Anvil compatibility regression passed: freeRenamePrior=0,7,40 nativeRepair=true quarkDiamond=true quarkTome=true combinedCostsPreserved=true");
  }

  private static ItemStack pickaxe(boolean netherite, int prior) {
    ItemStack item = new ItemStack(netherite ? Items.NETHERITE_PICKAXE : Items.DIAMOND_PICKAXE);
    item.setDamageValue(1000);
    item.set(DataComponents.REPAIR_COST, prior);
    return item;
  }

  private static void apply(ModAnvilMenu menu, ItemStack left, ItemStack right, String name) {
    menu.getSlot(1).set(ItemStack.EMPTY);
    menu.getSlot(0).set(left.copy());
    menu.getSlot(1).set(right.copy());
    menu.setItemName(name);
    menu.createResult();
  }

  private static void compareNative(
      ModAnvilMenu menu,
      ServerPlayer player,
      ItemStack left,
      ItemStack right,
      String name,
      String label) {
    BuiltInAnvilMenu nativeMenu =
        new BuiltInAnvilMenu(player.getInventory(), ContainerLevelAccess.NULL);
    nativeMenu.init(left, right, name);
    nativeMenu.fillResultSlots();
    apply(menu, left, right, name);
    diagnose(menu, player, label);
    require(!nativeMenu.getResult().isEmpty(), label + " native operation is available");
    require(
        menu.getCost() == nativeMenu.getLevelCost()
            && ItemStack.matches(menu.getSlot(2).getItem(), nativeMenu.getResult()),
        label + " preserves actual native cost and complete output");
  }

  public static void diagnose(ModAnvilMenu menu, ServerPlayer player, String label) {
    AnvilMenuState actual = state(menu, "builtInAnvilState");
    AnvilMenuState vanilla = state(menu, "vanillaAnvilState");
    LogUtils.getLogger()
        .info(
            "Anvil diagnosis {}: menuCost={} actualCost={} vanillaCost={} materialCount={}/{} name={}/{} outputEqual={} leftEqual={} rightEqual={} output={} actual={} vanilla={}",
            label,
            menu.getCost(),
            actual.getLevelCost(),
            vanilla.getLevelCost(),
            actual.getRepairItemCountCost(),
            vanilla.getRepairItemCountCost(),
            actual.getItemName(),
            vanilla.getItemName(),
            ItemStack.matches(actual.getResult(), vanilla.getResult()),
            ItemStack.matches(actual.getLeftInput(), vanilla.getLeftInput()),
            ItemStack.matches(actual.getRightInput(), vanilla.getRightInput()),
            describe(menu.getSlot(2).getItem(), player),
            describe(actual.getResult(), player),
            describe(vanilla.getResult(), player));
  }

  private static String describe(ItemStack stack, ServerPlayer player) {
    return stack.isEmpty() ? "EMPTY" : stack.save(player.registryAccess()).toString();
  }

  private static AnvilMenuState state(ModAnvilMenu menu, String name) {
    try {
      var field = ModAnvilMenu.class.getDeclaredField(name);
      require(field.trySetAccessible(), "Inspect native anvil probe field");
      Object value = field.get(menu);
      require(value instanceof AnvilMenuState, "Native anvil probe state type");
      return (AnvilMenuState) value;
    } catch (NoSuchFieldException | IllegalAccessException failure) {
      throw new IllegalStateException("Pinned anvil state changed", failure);
    }
  }

  private static ItemStack ancientTome(Holder<?> enchantment) {
    try {
      var method =
          Class.forName("org.violetmoon.quark.content.tools.item.AncientTomeItem")
              .getMethod("getEnchantedItemStack", Holder.class);
      Object value = method.invoke(null, enchantment);
      require(value instanceof ItemStack, "Native tome factory returns an item stack");
      return (ItemStack) value;
    } catch (ClassNotFoundException
        | NoSuchMethodException
        | IllegalAccessException
        | InvocationTargetException failure) {
      throw new IllegalStateException("Cannot use pinned Quark native tome factory", failure);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
