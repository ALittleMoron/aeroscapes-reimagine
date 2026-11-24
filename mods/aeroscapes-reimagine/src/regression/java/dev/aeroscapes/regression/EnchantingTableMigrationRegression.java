package dev.aeroscapes.regression;

import java.util.UUID;
import me.alfie.immersiveenchanting.gui.EnchantingTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

final class EnchantingTableMigrationRegression {
  private static final BlockPos LOCKED = new BlockPos(149, 225, 645);
  private static final BlockPos UNLOCKED = LOCKED.east();
  private static final String KEY = "Legacy key";

  private EnchantingTableMigrationRegression() {}

  static void verify(ServerLevel level) {
    var oldId = ResourceLocation.fromNamespaceAndPath("easymagic", "enchanting_table");
    require(
        BuiltInRegistries.BLOCK.get(oldId) == Blocks.ENCHANTING_TABLE
            && BuiltInRegistries.BLOCK_ENTITY_TYPE.get(oldId) == BlockEntityType.ENCHANTING_TABLE,
        "Legacy block and block entity IDs resolve to the vanilla table");

    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("3c0265a0-21c7-446a-91b4-437a0d98264b"),
            "TableMigration",
            Vec3.atCenterOf(LOCKED.north()),
            null)) {
      verifyLockedTable(level, session.player);
      verifyUnlockedTable(level, session.player);
    }
    level.getChunkAt(LOCKED).setUnsaved(true);
  }

  private static void verifyLockedTable(ServerLevel level, ServerPlayer player) {
    var state = level.getBlockState(LOCKED);
    require(state.is(Blocks.ENCHANTING_TABLE), "Saved legacy table block survived: " + state);
    var table = level.getBlockEntity(LOCKED);
    require(table instanceof EnchantingTableBlockEntity, "Saved table block entity survived");
    var saved = table.saveWithFullMetadata(level.registryAccess());
    require(
        saved.getString("id").equals("minecraft:enchanting_table"),
        "Migrated block entity is saved under its canonical ID");
    require(saved.getString("Lock").equals(KEY), "Legacy lock still serializes");

    var provider = state.getMenuProvider(level, LOCKED);
    require(provider != null, "Migrated table has a menu provider");
    require(provider.getDisplayName().getString().equals("Legacy table"), "Custom name survived");
    useTable(level, player, LOCKED);
    require(player.containerMenu == player.inventoryMenu, "Keyless access denied");

    ItemStack key = new ItemStack(Items.STICK);
    key.set(DataComponents.CUSTOM_NAME, Component.literal(KEY));
    player.setItemInHand(InteractionHand.MAIN_HAND, key);
    useTable(level, player, LOCKED);
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    var menu = player.containerMenu;
    require(menu instanceof EnchantingTableMenu, "Named key opens Immersive Enchanting");

    var tool = menu.getSlot(0).getItem();
    var lapis = menu.getSlot(1).getItem();
    var catalyst = menu.getSlot(2).getItem();
    require(
        tool.is(Items.DIAMOND_PICKAXE)
            && tool.getCount() == 1
            && tool.getDamageValue() == 91
            && tool.getHoverName().getString().equals("Legacy pickaxe"),
        "Tool and its components survived");
    require(lapis.is(Items.LAPIS_LAZULI) && lapis.getCount() == 17, "Lapis stack survived");
    require(
        catalyst.is(Items.REDSTONE) && catalyst.getCount() == 4,
        "Optional legacy catalyst stack survived");
    player.closeContainer();
  }

  private static void verifyUnlockedTable(ServerLevel level, ServerPlayer player) {
    var state = level.getBlockState(UNLOCKED);
    require(state.is(Blocks.ENCHANTING_TABLE), "Empty legacy table block survived");
    useTable(level, player, UNLOCKED);
    var menu = player.containerMenu;
    require(menu instanceof EnchantingTableMenu, "Unlocked table opens without a key");
    require(
        menu.getSlot(0).getItem().isEmpty()
            && menu.getSlot(1).getItem().isEmpty()
            && menu.getSlot(2).getItem().isEmpty(),
        "Empty table stays empty");
    player.closeContainer();
  }

  private static void useTable(ServerLevel level, ServerPlayer player, BlockPos position) {
    var hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.NORTH, position, false);
    level.getBlockState(position).useWithoutItem(level, player, hit);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
