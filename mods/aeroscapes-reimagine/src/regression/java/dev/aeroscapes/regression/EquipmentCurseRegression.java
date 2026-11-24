package dev.aeroscapes.regression;

import com.herobrot.tieredneo.api.TieredNeoAPI;
import com.herobrot.tieredneo.reforge.ReforgeMenu;
import dev.aeroscapes.enchanting.CurseCleansing;
import dev.aeroscapes.enchanting.EquipmentCurses;
import io.netty.buffer.Unpooled;
import java.util.List;
import me.alfie.immersiveenchanting.datapack.enchantment_cost.CostRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.connection.ConnectionType;

final class EquipmentCurseRegression {
  private static final List<String> VAULTS =
      List.of(
          "creeping_crypt/vault_creeping",
          "lone_citadel/c_vault",
          "lone_citadel/c_vault_boss",
          "toxic_lair/toxic_vault",
          "toxic_lair/toxic_ominous_vault");

  private EquipmentCurseRegression() {}

  static void verify(ServerLevel level, ServerPlayer player, BlockPos station) {
    verifyCostSync(level);
    verifyLoot(level);
    ItemStack crafted = ItemStack.EMPTY;
    for (var click : List.of(ClickType.PICKUP, ClickType.QUICK_MOVE)) {
      var cursed = craftSword(player, station, click, 0);
      require(curseCount(cursed) == 1, "Manufacturing applies one curse on a winning " + click);
      require(
          curseCount(craftSword(player, station, click, 1)) == 0,
          "Adjacent manufacturing draw stays clean with " + click);
      if (click == ClickType.PICKUP) crafted = cursed;
    }
    verifyRestoration(level, player, station, crafted);
  }

  private static void verifyCostSync(ServerLevel level) {
    var registries = level.registryAccess();
    var buffer =
        new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
    try {
      CostRegistry.STREAM_CODEC.encode(buffer, CostRegistry.server());
      var synced = CostRegistry.STREAM_CODEC.decode(buffer);
      synced.resolveEnchantmentHolders(registries);
      var enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
      var missing =
          enchantments
              .listElements()
              .filter(effect -> !synced.isRegistered(effect))
              .map(Holder::getRegisteredName)
              .sorted()
              .toList();
      require(
          missing.isEmpty(), "Missing Immersive Enchanting costs after registry sync: " + missing);
      var curses = enchantments.getOrThrow(EquipmentCurses.SOURCED);
      require(curses.size() > 0, "The equipment curse tag is loaded");
      for (var curse : curses) {
        require(
            !synced.get(curse).enabled(),
            "Equipment curse stays disabled at the table: " + curse.getRegisteredName());
      }
    } finally {
      buffer.release();
    }
  }

  private static ItemStack craftSword(
      ServerPlayer player, BlockPos station, ClickType click, int draw) {
    var level = player.serverLevel();
    var access = ContainerLevelAccess.create(level, station);
    var blade = BuiltInRegistries.ITEM.get(ResourceLocation.parse("overgeared:copper_sword_blade"));
    var sword = BuiltInRegistries.ITEM.get(ResourceLocation.parse("overgeared:copper_sword"));
    require(blade != Items.AIR && sword != Items.AIR, "The copper blade and sword are registered");
    level.setBlockAndUpdate(station, Blocks.CRAFTING_TABLE.defaultBlockState());
    var menu = new CraftingMenu(55, player.getInventory(), access);
    player.containerMenu = menu;
    player.getInventory().clearContent();
    try {
      menu.getSlot(1).set(new ItemStack(blade));
      menu.getSlot(2).set(new ItemStack(Items.STICK));
      ItemStack preview = menu.getSlot(0).getItem();
      require(preview.is(sword) && curseCount(preview) == 0, "Clean recipe preview");
      player.getRandom().setSeed(seedFor(draw));
      if (click == ClickType.QUICK_MOVE) {
        player.getInventory().items.replaceAll(ignored -> new ItemStack(Items.COBBLESTONE, 64));
        menu.clicked(0, 0, click, player);
        require(
            menu.getSlot(1).hasItem() && menu.getSlot(2).hasItem() && curseCount(preview) == 0,
            "Failed transfer leaves the recipe and the curse roll untouched");
        player.getInventory().clearContent();
      }
      menu.clicked(0, 0, click, player);
      ItemStack result =
          click == ClickType.PICKUP
              ? menu.getCarried()
              : player.getInventory().items.stream()
                  .filter(item -> item.is(sword))
                  .findFirst()
                  .orElse(ItemStack.EMPTY);
      require(
          result.is(sword) && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem(),
          "A completed craft delivers its sword and consumes inputs");
      return result.copy();
    } finally {
      menu.setCarried(ItemStack.EMPTY);
      player.containerMenu = player.inventoryMenu;
      player.getInventory().clearContent();
    }
  }

  private static void verifyRestoration(
      ServerLevel level, ServerPlayer player, BlockPos station, ItemStack sword) {
    var sharpness =
        level
            .registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.SHARPNESS);
    sword.enchant(sharpness, 2);
    sword.set(DataComponents.CUSTOM_NAME, Component.literal("Cursed witness"));
    var materials = TieredNeoAPI.getReforgeBaseItems(sword.getItem());
    require(!materials.isEmpty(), "The sword is reforgeable");
    var reforge = new ReforgeMenu(56, player.getInventory(), ContainerLevelAccess.NULL);
    reforge.getSlot(0).set(new ItemStack(materials.getFirst(), 2));
    reforge.getSlot(1).set(sword);
    reforge.getSlot(2).set(new ItemStack(Items.LAPIS_LAZULI, 2));
    reforge.reforge();
    require(
        reforge.getSlot(0).getItem().getCount() == 1
            && reforge.getSlot(2).getItem().getCount() == 1,
        "Paid reforging consumes both materials for an undamaged weapon");
    require(
        curseCount(sword) == 1
            && EnchantmentHelper.getEnchantmentsForCrafting(sword).getLevel(sharpness) == 2,
        "Paid reforging preserves the actual curse and positive enchantment");
    var speed = new double[1];
    sword.forEachModifier(
        EquipmentSlot.MAINHAND,
        (attribute, modifier) -> {
          if (attribute.equals(Attributes.ATTACK_SPEED)
              && modifier.id().getNamespace().equals("aeroscapes_reimagine")
              && modifier.id().getPath().equals("slow_recovery/mainhand"))
            speed[0] = modifier.amount();
        });
    require(Math.abs(speed[0] + 0.15) < 0.00001, "The weapon applies its attack speed penalty");
    sword.setDamageValue(20);
    var expected = sword.copy();
    EnchantmentHelper.updateEnchantments(
        expected, effects -> effects.removeIf(effect -> effect.is(EnchantmentTags.CURSE)));
    level.setBlockAndUpdate(station, Blocks.GRINDSTONE.defaultBlockState());
    var grindstone =
        new GrindstoneMenu(57, player.getInventory(), ContainerLevelAccess.create(level, station));
    player.containerMenu = grindstone;
    try {
      grindstone.getSlot(0).set(sword);
      require(
          curseCount(grindstone.getSlot(2).getItem()) == 1,
          "The ordinary grindstone cannot remove the new curse");
      grindstone
          .getSlot(1)
          .set(new ItemStack(BuiltInRegistries.ITEM.get(CurseCleansing.HOLY_RUNE), 2));
      grindstone.clicked(2, 0, ClickType.PICKUP, player);
      require(
          grindstone.getSlot(1).getItem().getCount() == 1
              && ItemStack.matches(expected, grindstone.getCarried()),
          "Cleansing consumes one rune and preserves all non-curse components");
    } finally {
      grindstone.setCarried(ItemStack.EMPTY);
      player.containerMenu = player.inventoryMenu;
    }
  }

  private static void verifyLoot(ServerLevel level) {
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(level.getSharedSpawnPos()))
            .create(LootContextParamSets.CHEST);
    for (String vault : VAULTS) {
      var key =
          ResourceKey.create(
              Registries.LOOT_TABLE, ResourceLocation.parse("nova_structures:chests/" + vault));
      var table = level.getServer().reloadableRegistries().getLootTable(key);
      int cursedRolls = 0;
      for (long seed = 1; seed <= 256; seed++) {
        var curses =
            table.getRandomItems(params, seed * 0x9e3779b97f4a7c15L).stream()
                .filter(item -> curseCount(item) > 0)
                .toList();
        require(curses.size() <= 1, "At most one item is cursed in " + vault);
        for (var item : curses) {
          var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(item);
          require(
              curseCount(item) == 1
                  && enchantments.keySet().stream()
                      .anyMatch(effect -> !effect.is(EnchantmentTags.CURSE)),
              "The cursed find retains a beneficial enchantment in " + vault);
          cursedRolls++;
        }
      }
      require(
          cursedRolls > 0 && cursedRolls < 256,
          "The selected vault offers occasional cursed finds: " + vault);
    }
    verifyUrns(level);
    var random = EnchantRandomlyFunction.randomEnchantment().build();
    var levelled = new EnchantWithLevelsFunction.Builder(ConstantValue.exactly(30)).build();
    for (long seed = 1; seed <= 256; seed++) {
      var context =
          new LootContext.Builder(params)
              .withOptionalRandomSeed(seed * 0x9e3779b97f4a7c15L)
              .create(java.util.Optional.empty());
      require(
          curseCount(random.apply(new ItemStack(Items.BOOK), context)) == 0
              && curseCount(levelled.apply(new ItemStack(Items.DIAMOND_SWORD), context)) == 0,
          "Unqualified native loot functions exclude the owned curse pool");
    }
  }

  private static void verifyUrns(ServerLevel level) {
    var urn = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("supplementaries:urn"));
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.BLOCK_STATE, urn.defaultBlockState())
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(level.getSharedSpawnPos()))
            .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
            .create(LootContextParamSets.BLOCK);
    for (String rarity : List.of("rare", "epic")) {
      var key =
          ResourceKey.create(
              Registries.LOOT_TABLE,
              ResourceLocation.parse("supplementaries:loot/urn_loot/" + rarity));
      var table = level.getServer().reloadableRegistries().getLootTable(key);
      int nativeCurses = 0;
      for (long seed = 1; seed <= 128; seed++) {
        for (var item : table.getRandomItems(params, seed * 0x9e3779b97f4a7c15L)) {
          require(curseCount(item) == 0, "Urn loot keeps its original curse pool");
          if (EnchantmentHelper.getEnchantmentsForCrafting(item).keySet().stream()
              .anyMatch(effect -> effect.is(EnchantmentTags.CURSE))) nativeCurses++;
        }
      }
      require(nativeCurses > 0, "The " + rarity + " urn sample reached the vanilla curse pool");
    }
  }

  private static long seedFor(int draw) {
    var random = RandomSource.create();
    for (long seed = 0; seed < 10000; seed++) {
      random.setSeed(seed);
      if (random.nextInt(200) == draw) return seed;
    }
    throw new IllegalStateException("No manufacturing seed for " + draw);
  }

  private static long curseCount(ItemStack item) {
    return EnchantmentHelper.getEnchantmentsForCrafting(item).keySet().stream()
        .filter(effect -> effect.is(EquipmentCurses.SOURCED))
        .count();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
