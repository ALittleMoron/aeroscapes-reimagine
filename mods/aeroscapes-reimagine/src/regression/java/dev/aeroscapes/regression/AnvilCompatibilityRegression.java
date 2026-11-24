package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.enchanting.LibraryBooks;
import dev.aeroscapes.progression.ProfessionDiscipline;
import fuzs.easyanvils.EasyAnvils;
import fuzs.easyanvils.config.ServerConfig;
import fuzs.easyanvils.world.inventory.ModAnvilMenu;
import fuzs.easyanvils.world.inventory.state.AnvilMenuState;
import fuzs.easyanvils.world.inventory.state.BuiltInAnvilMenu;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.puffish.skillsmod.SkillsMod;

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
      var unbreaking =
          level
              .registryAccess()
              .lookupOrThrow(Registries.ENCHANTMENT)
              .getOrThrow(Enchantments.UNBREAKING);
      verifyBookPolicy(menu, sharpness, unbreaking);
      verifyBookPolicy(new AnvilMenu(61, player.getInventory()), sharpness, unbreaking);

      ItemStack sword = new ItemStack(Items.IRON_SWORD);
      int maximum = sharpness.value().getMaxLevel();
      sword.enchant(sharpness, maximum - 1);
      sword.set(DataComponents.REPAIR_COST, 7);
      ItemStack tome = ancientTome(sharpness);
      apply(menu, sword, tome, "");
      require(menu.getSlot(2).getItem().isEmpty(), "A tome still requires personal mastery");
      for (int rank = 1; rank <= 4; rank++)
        SkillsMod.getInstance()
            .unlockSkill(
                player, ProfessionDiscipline.ENCHANTING.category(), "enchanting_mastery_" + rank);
      apply(menu, sword, tome, "");
      require(menu.getSlot(2).getItem().isEmpty(), "A tome needs the matching advanced book");
      var swordKnowledge = player.getInventory().getItem(0);
      try {
        player.getInventory().setItem(0, LibraryBooks.ancient(sharpness, maximum));
        compareNative(menu, player, sword, tome, "", "quark-ordinary-tome-upgrade");
        require(
            EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(2).getItem())
                    .getLevel(sharpness)
                == maximum,
            "A tome can finish an ordinary enchantment level after discovering its book");
        compareNative(menu, player, sword, tome, "Named Tome Upgrade", "quark-tome-with-name");
      } finally {
        player.getInventory().setItem(0, swordKnowledge);
      }
      sword.enchant(sharpness, maximum);
      apply(menu, sword, tome, "");
      require(menu.getSlot(2).getItem().isEmpty(), "A tome cannot exceed the native maximum");

      sword.enchant(sharpness, maximum + 1);
      apply(menu, sword, ItemStack.EMPTY, "Keeps Existing Enchantments");
      require(
          EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(2).getItem())
                  .getLevel(sharpness)
              == maximum + 1,
          "Renaming preserves existing enchantments above the native maximum");
      var mending =
          level
              .registryAccess()
              .lookupOrThrow(Registries.ENCHANTMENT)
              .getOrThrow(Enchantments.MENDING);
      verifyAdvancedTransfer(menu, player, mending, sharpness);
      verifyAdvancedTransfer(new AnvilMenu(62, player.getInventory()), player, mending, sharpness);
    } finally {
      menu.getSlot(0).set(ItemStack.EMPTY);
      menu.getSlot(1).set(ItemStack.EMPTY);
      menu.setItemName("");
      menu.createResult();
    }
    LogUtils.getLogger()
        .info(
            "Anvil compatibility regression passed: freeRenamePrior=0,7,40 nativeRepair=true quarkDiamond=true maximumEnforced=true bookMergesBlocked=true");
  }

  private static void verifyAdvancedTransfer(
      AnvilMenu menu,
      ServerPlayer player,
      Holder<Enchantment> mending,
      Holder<Enchantment> sharpness) {
    ItemStack sword = new ItemStack(Items.IRON_SWORD);
    ItemStack enchantedDonor = new ItemStack(Items.IRON_SWORD);
    enchantedDonor.enchant(mending, 1);
    ItemStack ordinaryBook = enchantedBook(mending);
    ItemStack priorKnowledge = player.getInventory().getItem(0);
    int priorLevels = player.experienceLevel;
    try {
      player.giveExperienceLevels(40);
      apply(menu, sword, ordinaryBook, "");
      require(menu.getSlot(2).getItem().isEmpty(), "Ordinary book does not unlock Mending");
      apply(menu, sword, enchantedDonor, "");
      require(menu.getSlot(2).getItem().isEmpty(), "Enchanted equipment does not unlock Mending");

      player.getInventory().setItem(0, LibraryBooks.ancient(mending, 1));
      apply(menu, sword, ordinaryBook, "");
      var result = menu.getSlot(2);
      require(
          EnchantmentHelper.getEnchantmentsForCrafting(result.getItem()).getLevel(mending) == 1
              && result.mayPickup(player),
          "Learned player can transfer an effect with its Ancient Book");
      player.getInventory().setItem(0, ItemStack.EMPTY);
      require(!result.mayPickup(player), "Taking a stale result rechecks the Ancient Book");

      int overMaximum = sharpness.value().getMaxLevel() + 1;
      sword.enchant(sharpness, overMaximum);
      apply(menu, sword, enchantedBook(sharpness), "");
      require(
          result.getItem().isEmpty()
              || EnchantmentHelper.getEnchantmentsForCrafting(result.getItem()).getLevel(sharpness)
                  == overMaximum,
          "Combining cannot downgrade a grandfathered enchantment");
    } finally {
      player.getInventory().setItem(0, priorKnowledge);
      player.giveExperienceLevels(priorLevels - player.experienceLevel);
    }
  }

  private static void verifyBookPolicy(
      AnvilMenu menu, Holder<Enchantment> sharpness, Holder<Enchantment> unbreaking) {
    ItemStack sharpnessBook = enchantedBook(sharpness);
    apply(menu, ItemStack.EMPTY, ItemStack.EMPTY, "");
    var previews = new ArrayList<ItemStack>();
    var listener =
        new ContainerListener() {
          @Override
          public void slotChanged(AbstractContainerMenu source, int slot, ItemStack stack) {
            if (slot == 2 && !stack.isEmpty() && source.getSlot(1).hasItem()) previews.add(stack);
          }

          @Override
          public void dataChanged(AbstractContainerMenu source, int property, int value) {}
        };
    menu.addSlotListener(listener);
    try {
      for (ItemStack right :
          new ItemStack[] {sharpnessBook, enchantedBook(unbreaking), ancientTome(sharpness)}) {
        apply(menu, sharpnessBook, right, "Two Books");
        menu.setItemName("Still Two Books");
        require(menu.getSlot(2).getItem().isEmpty(), "Two books must not produce a result");
        require(menu.getCost() == 0, "Two books must not leave a stale cost");
      }
      require(previews.isEmpty(), "Two books must not broadcast a result");
    } finally {
      menu.removeSlotListener(listener);
    }
    apply(menu, new ItemStack(Items.IRON_SWORD), sharpnessBook, "");
    require(
        EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(2).getItem()).getLevel(sharpness)
            == 1,
        "An enchanted book can still be applied to equipment");
    apply(menu, sharpnessBook, ItemStack.EMPTY, "One Book");
    require(
        menu.getSlot(2).getItem().getHoverName().getString().equals("One Book"),
        "A single book can still be renamed");
  }

  private static ItemStack enchantedBook(Holder<Enchantment> enchantment) {
    ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
    var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
    enchantments.set(enchantment, 1);
    book.set(DataComponents.STORED_ENCHANTMENTS, enchantments.toImmutable());
    return book;
  }

  private static ItemStack pickaxe(boolean netherite, int prior) {
    ItemStack item = new ItemStack(netherite ? Items.NETHERITE_PICKAXE : Items.DIAMOND_PICKAXE);
    item.setDamageValue(1000);
    item.set(DataComponents.REPAIR_COST, prior);
    return item;
  }

  private static void apply(AnvilMenu menu, ItemStack left, ItemStack right, String name) {
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
