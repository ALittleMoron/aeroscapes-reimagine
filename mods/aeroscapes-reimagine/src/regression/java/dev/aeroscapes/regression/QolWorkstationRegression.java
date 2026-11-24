package dev.aeroscapes.regression;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import fuzs.easyanvils.world.inventory.ModAnvilMenu;
import fuzs.easyanvils.world.level.block.entity.AnvilBlockEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.alfie.immersiveenchanting.gui.EnchantingTableMenu;
import me.alfie.immersiveenchanting.util.IEnchantingTableInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;

/** Runs native costs, persistent inventory serialization and transformed mount behavior. */
public final class QolWorkstationRegression {
  private QolWorkstationRegression() {}

  public static void verify(ServerLevel level) {
    verifyLootConfiguration(level);
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("a4ed87df-a813-4c33-bdec-c577b3154094"),
            "WorkstationTests",
            Vec3.atCenterOf(level.getSharedSpawnPos()).add(32, 32, 32),
            null)) {
      ServerPlayer player = session.player;
      BlockPos position = player.blockPosition().offset(2, 0, 0);
      level.getChunkAt(position);
      var previous = level.getBlockState(position);
      var support = level.getBlockState(position.below());
      var menu = player.containerMenu;
      try {
        level.setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
        HorseRecallRegression.verify(level, player);
        verifyAnvil(level, player, position);
        verifyEnchanting(level, player, position);
        verifyCleansing(level, player, position);
        verifyMounts(level, player);
      } finally {
        player.stopRiding();
        player.containerMenu = menu;
        player.getInventory().clearContent();
        if (level.getBlockEntity(position) instanceof net.minecraft.world.Container container) {
          container.clearContent();
        }
        level.setBlockAndUpdate(position, previous);
        level.setBlockAndUpdate(position.below(), support);
      }
    }
  }

  private static void verifyCleansing(ServerLevel level, ServerPlayer player, BlockPos position) {
    level.setBlockAndUpdate(position, Blocks.GRINDSTONE.defaultBlockState());
    var menu =
        new GrindstoneMenu(62, player.getInventory(), ContainerLevelAccess.create(level, position));
    player.containerMenu = menu;
    var enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    var sharpness = enchantments.getOrThrow(Enchantments.SHARPNESS);
    var vanishing = enchantments.getOrThrow(Enchantments.VANISHING_CURSE);
    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
    sword.enchant(sharpness, 3);
    sword.enchant(vanishing, 1);
    sword.setDamageValue(100);
    sword.set(DataComponents.CUSTOM_NAME, Component.literal("Cleansing witness"));
    sword.set(DataComponents.REPAIR_COST, 7);

    menu.setCarried(sword.copy());
    menu.clicked(0, 0, ClickType.PICKUP, player);
    require(
        EnchantmentHelper.getEnchantmentsForCrafting(menu.getSlot(2).getItem()).getLevel(vanishing)
            == 1,
        "Ordinary grindstone does not remove curses");

    ItemStack rune =
        new ItemStack(
            BuiltInRegistries.ITEM.get(ResourceLocation.parse("irons_spellbooks:holy_rune")), 2);
    require(!rune.isEmpty(), "Holy Rune is registered");
    menu.setCarried(rune);
    menu.clicked(1, 0, ClickType.PICKUP, player);
    require(menu.getCarried().isEmpty(), "Grindstone accepts the reagent in the lower slot");
    require(menu.getSlot(1).getItem().getCount() == 2, "Preview does not spend runes");

    ItemStack expected = sword.copy();
    EnchantmentHelper.updateEnchantments(expected, mutable -> mutable.set(vanishing, 0));
    menu.clicked(2, 0, ClickType.PICKUP, player);
    require(
        ItemStack.matches(menu.getCarried(), expected),
        "Cleanse removes only the curse, preserving all other item components");
    require(menu.getSlot(0).getItem().isEmpty(), "Cleansing consumes the original item");
    require(menu.getSlot(1).getItem().getCount() == 1, "Cleansing pays exactly one rune");
    require(menu.getSlot(2).getItem().isEmpty(), "Cleansing cannot be repeated without input");
    menu.setCarried(ItemStack.EMPTY);
    menu.getSlot(1).set(ItemStack.EMPTY);
  }

  private static void verifyLootConfiguration(ServerLevel level) {
    try (var reader =
        Files.newBufferedReader(FMLPaths.CONFIGDIR.get().resolve("ali/ali_common.json"))) {
      Class<?> type = Class.forName("com.yanny.ali.configuration.AliConfig");
      Object source = type.getField("CODEC").get(null);
      require(source instanceof Codec<?>, "Native loot config codec");
      Object parsed =
          ((Codec<?>) source)
              .parse(
                  RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()),
                  JsonParser.parseReader(reader))
              .getOrThrow();
      for (var expected :
          Map.of(
                  "blockCategories",
                  2,
                  "entityCategories",
                  1,
                  "gameplayCategories",
                  11,
                  "tradeCategories",
                  0)
              .entrySet()) {
        Object categories = type.getField(expected.getKey()).get(parsed);
        require(
            categories instanceof List<?> entries && entries.size() == expected.getValue(),
            "Native loot category selection: " + expected.getKey());
      }
    } catch (IOException | ReflectiveOperationException failure) {
      throw new IllegalStateException("Pinned loot configuration failed", failure);
    }
  }

  private static void verifyAnvil(ServerLevel level, ServerPlayer player, BlockPos position) {
    level.setBlockAndUpdate(position, ((BlockItem) Items.ANVIL).getBlock().defaultBlockState());
    require(level.getBlockEntity(position) instanceof AnvilBlockEntity, "Native anvil inventory");
    AnvilBlockEntity inventory = (AnvilBlockEntity) level.getBlockEntity(position);
    var menu =
        new ModAnvilMenu(
            60, player.getInventory(), inventory, ContainerLevelAccess.create(level, position));
    player.containerMenu = menu;
    AnvilCompatibilityRegression.verify(menu, player, level);
    ItemStack named = new ItemStack(Items.IRON_SWORD);
    named.set(DataComponents.REPAIR_COST, 7);
    menu.getSlot(0).set(named);
    menu.setItemName("Workstation Test");
    require(menu.getCost() == 0, "Rename-only operation is free; actual cost=" + menu.getCost());
    ItemStack renamed = menu.getSlot(2).getItem();
    require(
        !renamed.isEmpty() && renamed.getOrDefault(DataComponents.REPAIR_COST, 0) == 7,
        "Free renaming preserves the existing prior-work penalty");
    require(renamed.getHoverName().getString().equals("Workstation Test"), "Native output name");

    ItemStack damaged = new ItemStack(Items.DIAMOND_PICKAXE);
    damaged.setDamageValue(1000);
    menu.setItemName("");
    menu.getSlot(0).set(damaged);
    menu.getSlot(1).set(new ItemStack(Items.DIAMOND));
    menu.createResult();
    ItemStack repaired = menu.getSlot(2).getItem();
    require(menu.getCost() == 1, "One repair material costs one ordinary level");
    require(
        repaired.getDamageValue() == 1000 - (int) Math.floor(damaged.getMaxDamage() * 0.25),
        "One material restores ordinary quarter durability");
    require(
        repaired.getOrDefault(DataComponents.REPAIR_COST, 0) == 1,
        "Repair increases ordinary prior-work penalty");
    damaged.set(DataComponents.REPAIR_COST, 40);
    menu.getSlot(0).set(damaged);
    menu.createResult();
    require(
        !menu.getSlot(2).getItem().isEmpty() && menu.getCost() == 41,
        "Costs above forty remain payable without being discounted");

    var saved = inventory.saveWithoutMetadata(level.registryAccess());
    var restored = new AnvilBlockEntity(position, level.getBlockState(position));
    restored.loadWithComponents(saved, level.registryAccess());
    require(
        ItemStack.isSameItemSameComponents(restored.getItem(0), damaged)
            && restored.getItem(1).is(Items.DIAMOND)
            && restored.getItem(1).getCount() == 1,
        "Anvil serialization retains both inputs and their components");
    inventory.clearContent();
  }

  private static void verifyEnchanting(ServerLevel level, ServerPlayer player, BlockPos position) {
    var state = Blocks.ENCHANTING_TABLE.defaultBlockState();
    level.setBlockAndUpdate(position, state);
    var table = level.getBlockEntity(position);
    require(table instanceof EnchantingTableBlockEntity, "Native enchanting table");
    require(table instanceof IEnchantingTableInventory, "Immersive Enchanting inventory");
    var provider = state.getMenuProvider(level, position);
    require(provider != null, "Enchanting table exposes a menu provider");
    var menu = provider.createMenu(61, player.getInventory(), player);
    require(menu instanceof EnchantingTableMenu, "Enchanting table opens Immersive Enchanting");

    List<ItemStack> inputs =
        List.of(
            new ItemStack(Items.DIAMOND_PICKAXE),
            new ItemStack(Items.LAPIS_LAZULI, 3),
            new ItemStack(Items.REDSTONE, 2));
    inputs.getFirst().setDamageValue(91);
    for (int slot = 0; slot < inputs.size(); slot++) menu.getSlot(slot).set(inputs.get(slot));
    var saved = table.saveWithoutMetadata(level.registryAccess());
    var restored = new EnchantingTableBlockEntity(position, state);
    restored.loadWithComponents(saved, level.registryAccess());
    require(restored instanceof IEnchantingTableInventory, "Recreated enchanting inventory");
    var inventory = (IEnchantingTableInventory) restored;
    for (int slot = 0; slot < inputs.size(); slot++) {
      require(
          ItemStack.matches(inventory.immersive$getItem(slot), inputs.get(slot)),
          "Enchanting table serialization preserves slot " + slot);
      menu.getSlot(slot).set(ItemStack.EMPTY);
    }
  }

  private static void verifyMounts(ServerLevel level, ServerPlayer player) {
    ResourceLocation horseModifier =
        ResourceLocation.fromNamespaceAndPath("horseman", "mounted-break-speed");
    ResourceLocation unrelatedId =
        ResourceLocation.fromNamespaceAndPath("aeroscapes_regression", "unrelated-mining");
    var attribute = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
    require(attribute != null, "Player mining attribute exists");
    var unrelated =
        new AttributeModifier(unrelatedId, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    attribute.addTransientModifier(unrelated);
    Entity horse = EntityType.HORSE.create(level);
    Entity donkey = EntityType.DONKEY.create(level);
    Entity boat = EntityType.BOAT.create(level);
    require(horse != null && donkey != null && boat != null, "Native mount entities exist");
    try {
      for (Entity mount : List.of(horse, donkey, boat)) {
        mount.moveTo(player.position());
        level.addFreshEntity(mount);
        require(player.startRiding(mount, true), "Native mounting succeeds");
        var modifier = attribute.getModifier(horseModifier);
        require(
            mount == boat ? modifier == null : modifier != null && modifier.amount() == 5.0,
            "Horse mining boost is limited to horse-type mounts");
        require(attribute.getModifier(unrelatedId) != null, "Other mining modifiers survive");
        player.stopRiding();
        require(attribute.getModifier(horseModifier) == null, "Dismount removes horse boost");
      }
    } finally {
      player.stopRiding();
      for (Entity mount : List.of(horse, donkey, boat)) mount.discard();
      attribute.removeModifier(unrelatedId);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
