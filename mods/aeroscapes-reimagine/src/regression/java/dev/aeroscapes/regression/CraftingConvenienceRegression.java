package dev.aeroscapes.regression;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Verifies authoritative grid operations through the installed provider's packet handlers. */
public final class CraftingConvenienceRegression {
  private CraftingConvenienceRegression() {}

  public static void verify(ServerLevel level) {
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("d0d6d136-27c3-43d1-b181-80d16743cde5"),
            "CraftingRegression",
            Vec3.atCenterOf(level.getSharedSpawnPos()).add(64, 32, 64),
            null)) {
      ServerPlayer player = session.player;
      var originalMenu = player.containerMenu;
      try {
        verifyMenu(player, new CraftingMenu(40, player.getInventory()), 9);
        verifyMenu(player, player.inventoryMenu, 4);
        Object terminal =
            Class.forName("com.tom.storagemod.menu.CraftingTerminalMenu")
                .getConstructor(int.class, Inventory.class)
                .newInstance(41, player.getInventory());
        require(terminal instanceof AbstractContainerMenu, "Unexpected terminal menu type");
        verifyMenu(player, (AbstractContainerMenu) terminal, 9);
        verifyCropHarvest(level, player);
      } finally {
        player.containerMenu = originalMenu;
        player.getInventory().clearContent();
      }
    } catch (ReflectiveOperationException failure) {
      Throwable cause =
          failure instanceof InvocationTargetException invocation ? invocation.getCause() : failure;
      throw new IllegalStateException("Pinned crafting integration failed", cause);
    }
  }

  private static void verifyMenu(ServerPlayer player, AbstractContainerMenu menu, int size)
      throws ReflectiveOperationException {
    player.getInventory().clearContent();
    player.containerMenu = menu;
    Object resolved =
        Class.forName("net.blay09.mods.craftingtweaks.CraftingTweaksProviderManager")
            .getMethod("getCraftingGrids", AbstractContainerMenu.class)
            .invoke(null, menu);
    require(resolved instanceof List<?> grids && grids.size() == 1, "Expected one grid provider");
    Object grid = ((List<?>) resolved).getFirst();
    Object identifier = grid.getClass().getMethod("getId").invoke(grid);
    require(identifier instanceof ResourceLocation, "Grid identifier is not a resource location");
    ResourceLocation id = (ResourceLocation) identifier;
    for (int index = 1; index <= size; index++) {
      menu.getSlot(index).set(new ItemStack(Items.STICK, index));
    }
    packet(player, "RotateMessage", id, false);
    require(menu.getSlot(2).getItem().getCount() == 1, "Rotation did not move the top-left slot");
    packet(player, "RotateMessage", id, true);
    for (int index = 1; index <= size; index++) {
      require(menu.getSlot(index).getItem().getCount() == index, "Reverse rotation lost items");
      menu.getSlot(index).set(ItemStack.EMPTY);
    }
    menu.getSlot(1).set(new ItemStack(Items.STICK, size * 3));
    packet(player, "BalanceMessage", id, true);
    for (int index = 1; index <= size; index++) {
      require(
          menu.getSlot(index).getItem().getCount() == 3, "Spread did not preserve equal shares");
    }
    packet(player, "ClearMessage", id, false);
    for (int index = 1; index <= size; index++) {
      require(!menu.getSlot(index).hasItem(), "Clear left an ingredient behind");
    }
    require(
        player.getInventory().countItem(Items.STICK) == size * 3,
        "Clearing must return every ingredient exactly once");
  }

  private static void packet(
      ServerPlayer player, String type, ResourceLocation id, boolean modifier)
      throws ReflectiveOperationException {
    Class<?> message = Class.forName("net.blay09.mods.craftingtweaks.network." + type);
    Object payload =
        message.getConstructor(ResourceLocation.class, boolean.class).newInstance(id, modifier);
    message.getMethod("handle", ServerPlayer.class, message).invoke(null, player, payload);
  }

  private static void verifyCropHarvest(ServerLevel level, ServerPlayer player)
      throws ReflectiveOperationException {
    var harvest =
        Class.forName("org.violetmoon.quark.content.tweaks.module.SimpleHarvestModule")
            .getMethod(
                "tryHarvestOrClickCrop",
                Level.class,
                BlockPos.class,
                LivingEntity.class,
                InteractionHand.class,
                boolean.class);
    BlockPos position = player.blockPosition().offset(3, 0, 0);
    level.getChunkAt(position);
    var original = level.getBlockState(position);
    var originalSupport = level.getBlockState(position.below());
    AABB dropArea = AABB.ofSize(Vec3.atCenterOf(position), 8, 8, 8);
    Set<UUID> previousDrops =
        level.getEntitiesOfClass(ItemEntity.class, dropArea).stream()
            .map(ItemEntity::getUUID)
            .collect(Collectors.toUnmodifiableSet());
    try {
      level.setBlockAndUpdate(position.below(), Blocks.FARMLAND.defaultBlockState());
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      for (String identifier :
          List.of(
              "minecraft:wheat",
              "minecraft:carrots",
              "minecraft:potatoes",
              "minecraft:beetroots",
              "farmersdelight:cabbages",
              "farmersdelight:onions")) {
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(identifier));
        require(block instanceof CropBlock, "Missing crop fixture " + identifier);
        CropBlock crop = (CropBlock) block;
        level.setBlockAndUpdate(position, crop.getStateForAge(crop.getMaxAge()));
        Object result =
            harvest.invoke(null, level, position, player, InteractionHand.MAIN_HAND, false);
        require(Boolean.TRUE.equals(result), "Mature crop was not harvested: " + identifier);
        require(
            level.getBlockState(position).equals(crop.getStateForAge(0)),
            "Harvest did not replant crop: " + identifier);
        result = harvest.invoke(null, level, position, player, InteractionHand.MAIN_HAND, false);
        require(Boolean.FALSE.equals(result), "Immature crop was harvested: " + identifier);
      }
    } finally {
      level.setBlockAndUpdate(position, original);
      level.setBlockAndUpdate(position.below(), originalSupport);
      level.getEntitiesOfClass(ItemEntity.class, dropArea).stream()
          .filter(item -> !previousDrops.contains(item.getUUID()))
          .forEach(ItemEntity::discard);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
