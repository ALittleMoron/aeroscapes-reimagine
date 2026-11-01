package dev.aeroscapes.regression;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import fuzs.easyanvils.world.inventory.ModAnvilMenu;
import fuzs.easyanvils.world.level.block.entity.AnvilBlockEntity;
import fuzs.easymagic.world.inventory.ModEnchantmentMenu;
import fuzs.easymagic.world.level.block.entity.EnchantmentTableWithInventoryBlockEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
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
    level.setBlockAndUpdate(
        position, ((BlockItem) Items.ENCHANTING_TABLE).getBlock().defaultBlockState());
    require(
        level.getBlockEntity(position) instanceof EnchantmentTableWithInventoryBlockEntity,
        "Native enchanting inventory");
    var inventory = (EnchantmentTableWithInventoryBlockEntity) level.getBlockEntity(position);
    var menu =
        new ModEnchantmentMenu(
            61, player.getInventory(), inventory, ContainerLevelAccess.create(level, position));
    player.containerMenu = menu;
    inventory.setItem(0, new ItemStack(Items.DIAMOND_PICKAXE));
    inventory.setItem(1, new ItemStack(Items.LAPIS_LAZULI, 3));
    EasyMagicExperienceRegression.verify(player, menu, inventory);
    var saved = inventory.saveWithoutMetadata(level.registryAccess());
    var restored =
        new EnchantmentTableWithInventoryBlockEntity(position, level.getBlockState(position));
    restored.loadWithComponents(saved, level.registryAccess());
    require(
        restored.getItem(0).is(Items.DIAMOND_PICKAXE)
            && restored.getItem(1).is(Items.LAPIS_LAZULI)
            && restored.getItem(1).getCount() == 1,
        "Enchanting inventory serialization preserves item and remaining lapis");
    inventory.clearContent();
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
