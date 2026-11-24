package dev.aeroscapes.regression;

import dev.aeroscapes.enchanting.EnchantingMastery;
import dev.aeroscapes.enchanting.LibraryBooks;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import harmonised.pmmo.api.APIUtils;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import me.alfie.immersiveenchanting.gui.EnchantingTableMenu;
import me.alfie.immersiveenchanting.networking.EnchantPacket;
import me.alfie.immersiveenchanting.networking.ReplicatePacket;
import me.alfie.immersiveenchanting.util.EnchantmentUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;
import net.puffish.skillsmod.SkillsMod;

/** Exercises the live provider operation, persistence of knowledge levels and loot boundaries. */
final class LibraryEnchantingRegression {
  private static final ProfessionDiscipline DISCIPLINE = ProfessionDiscipline.ENCHANTING;
  private static final int EXPERIENCE_LEVELS = 24;
  private static final List<String> DISCOVERY_TABLES =
      List.of(
          "minecraft:chests/stronghold_library",
          "minecraft:chests/ancient_city",
          "minecraft:chests/trial_chambers/reward_ominous",
          "irons_spellbooks:chests/catacombs/dead_king_vault",
          "irons_spellbooks:chests/citadel/citadel_vault",
          "nova_structures:chests/lone_citadel/c_vault_boss",
          "nova_structures:chests/toxic_lair/toxic_ominous_vault");
  private static final List<String> SMALL_RUIN_TABLES =
      List.of(
          "nova_structures:pots/pot_wild_ruin",
          "nova_structures:chests/bunker_altar",
          "minecraft:chests/underwater_ruin_small");

  private LibraryEnchantingRegression() {}

  static void verify(ServerLevel level) {
    BlockPos tablePos = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 4);
    BlockPos shelfPos = tablePos.east(2);
    require(level.isEmptyBlock(tablePos) && level.isEmptyBlock(shelfPos), "Empty fixture space");
    level.setBlockAndUpdate(tablePos, Blocks.ENCHANTING_TABLE.defaultBlockState());
    level.setBlockAndUpdate(shelfPos, Blocks.CHISELED_BOOKSHELF.defaultBlockState());
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.randomUUID(),
            "LibraryFixture",
            Vec3.atBottomCenterOf(tablePos.north()),
            null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        require(!player.isCreative() && !player.isSpectator(), "Use a personal survival player");
        var provider =
            Objects.requireNonNull(level.getBlockState(tablePos).getMenuProvider(level, tablePos));
        require(player.openMenu(provider).isPresent(), "The ordinary table opens");
        require(player.containerMenu instanceof EnchantingTableMenu, "Provider menu is active");
        var menu = (EnchantingTableMenu) player.containerMenu;
        var shelf = (ChiseledBookShelfBlockEntity) level.getBlockEntity(shelfPos);
        var sharpness =
            level
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        verifyApplication(player, menu, Objects.requireNonNull(shelf), sharpness);
        verifyCopy(player, menu, sharpness);
        EquipmentCurseRegression.verify(level, player, tablePos);
      } finally {
        player.closeContainer();
        ProgressionRegression.listPlayer(level, player, false);
      }
    } finally {
      level.removeBlock(tablePos, false);
      level.removeBlock(shelfPos, false);
    }
    verifyLoot(level);
  }

  private static void verifyApplication(
      ServerPlayer player,
      EnchantingTableMenu menu,
      ChiseledBookShelfBlockEntity shelf,
      Holder<Enchantment> sharpness) {
    require(EnchantingMastery.rank(player) == 0, "A fresh player has no enchanting talents");
    long magicBefore = MagicPractice.credit(player);
    var sword = new ItemStack(Items.IRON_SWORD);
    menu.getToolSlot().set(sword);
    sharpen(player, menu, sharpness, 1, Items.COPPER_INGOT, 0);
    shelf.setItem(0, LibraryBooks.ancient(sharpness, 3));
    sharpen(player, menu, sharpness, 1, Items.COPPER_INGOT, 2);
    require(
        MagicPractice.credit(player) == magicBefore + 4, "Only the paid application trains Magic");
    sharpen(player, menu, sharpness, 2, Items.AMETHYST_SHARD, 0);
    for (int attempt = 0; attempt < 5; attempt++) {
      menu.getToolSlot().set(new ItemStack(Items.IRON_SWORD));
      sharpen(player, menu, sharpness, 1, Items.COPPER_INGOT, 2);
    }
    require(
        APIUtils.getAllLevels(player).getOrDefault(DISCIPLINE.skill(), 0L) == 5L,
        "Six paid applications at level I train the first enchanting point");
    ProgressionBridge.sync(player);
    learn(player, "enchanting_mastery_1");
    require(EnchantingMastery.rank(player) == 1, "Practice funds the first rank");
    menu.getToolSlot().set(sword);
    sharpen(player, menu, sharpness, 2, Items.COPPER_INGOT, 0);
    sharpen(player, menu, sharpness, 2, Items.GLOWSTONE_DUST, 4);

    APIUtils.setLevel(DISCIPLINE.skill(), player, 40);
    ProgressionBridge.sync(player);
    learn(player, "enchanting_mastery_2");
    sharpen(player, menu, sharpness, 3, Items.GOLD_INGOT, 7);
    learn(player, "enchanting_mastery_3");
    sharpen(player, menu, sharpness, 4, Items.ENDER_PEARL, 0);
    shelf.removeItem(0, 1);
    shelf.setItem(0, LibraryBooks.ancient(sharpness, 5));
    sharpen(player, menu, sharpness, 4, Items.ENDER_PEARL, 10);
    sharpen(player, menu, sharpness, 5, Items.DIAMOND, 0);
    learn(player, "enchanting_mastery_4");
    learn(player, "enchanting_efficiency_1");
    learn(player, "enchanting_efficiency_2");
    require(EnchantingMastery.rank(player) == 4, "Final mastery rank is learned");
    sharpen(player, menu, sharpness, 5, Items.DIAMOND, 12);
    sharpen(player, menu, sharpness, 6, Items.DIAMOND, 0);
  }

  private static void learn(ServerPlayer player, String talent) {
    var skills = SkillsMod.getInstance();
    skills.tryUnlockSkill(player, DISCIPLINE.category(), talent, false);
    require(
        skills.getSkillState(player, DISCIPLINE.category(), talent).orElseThrow()
            == net.puffish.skillsmod.api.Skill.State.UNLOCKED,
        "Learned " + talent);
  }

  private static void sharpen(
      ServerPlayer player,
      EnchantingTableMenu menu,
      Holder<Enchantment> sharpness,
      int desired,
      Item material,
      int xpCost) {
    var target = menu.getToolSlot().getItem();
    int previous = appliedLevel(target, sharpness);
    var catalyst = new ItemStack(material, 2);
    var lapis = new ItemStack(Items.LAPIS_LAZULI, 6);
    player.giveExperienceLevels(EXPERIENCE_LEVELS - player.experienceLevel);
    menu.getCostSlot().set(catalyst);
    menu.getFuelSlot().set(lapis);
    var packet = new EnchantPacket(Enchantments.SHARPNESS, desired);
    packet.exec(new ServerPayloadContext(player.connection, packet.type().id()));
    boolean applied = xpCost > 0;
    require(
        appliedLevel(menu.getToolSlot().getItem(), sharpness) == (applied ? desired : previous),
        "Expected application result at level " + desired);
    require(
        player.experienceLevel == EXPERIENCE_LEVELS - xpCost
            && catalyst.getCount() == (applied ? 1 : 2)
            && lapis.getCount() == (applied ? 6 - desired : 6),
        "Exact application price at level " + desired);
  }

  private static int appliedLevel(ItemStack item, Holder<Enchantment> effect) {
    return item.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(effect);
  }

  private static void verifyCopy(
      ServerPlayer player, EnchantingTableMenu menu, Holder<Enchantment> sharpness) {
    var packet = new ReplicatePacket();
    var context = new ServerPayloadContext(player.connection, packet.type().id());
    var cost = new ItemStack(Items.WRITABLE_BOOK);
    var lapis = new ItemStack(Items.LAPIS_LAZULI, 2);
    var source = LibraryBooks.ancient(sharpness, 5);
    var area = new AABB(menu.getBlockPos()).inflate(2);
    player.giveExperienceLevels(EXPERIENCE_LEVELS - player.experienceLevel);
    menu.getCostSlot().set(cost);
    menu.getFuelSlot().set(lapis);
    var sword = menu.getToolSlot().getItem();
    packet.exec(context);
    require(
        menu.getToolSlot().getItem() == sword
            && player.experienceLevel == EXPERIENCE_LEVELS
            && cost.getCount() == 1
            && lapis.getCount() == 2,
        "Copy rejects a validly priced non-book target without charging");

    long magicBefore = MagicPractice.credit(player);
    menu.getToolSlot().set(source);
    packet.exec(context);
    require(
        player.experienceLevel == EXPERIENCE_LEVELS - 8 && cost.isEmpty() && lapis.getCount() == 1,
        "Copy consumes one writable book, one lapis and eight levels despite enchanting"
            + " efficiency");
    require(
        menu.getToolSlot().getItem().isEmpty() && player.containerMenu == player.inventoryMenu,
        "Copy removes the input and closes the table");
    require(MagicPractice.credit(player) == magicBefore, "Copy does not train Magic");
    var drops = player.serverLevel().getEntitiesOfClass(ItemEntity.class, area);
    require(drops.size() == 2, "Copy returns exactly the original and one copy");
    int replicated = 0;
    for (var drop : drops) {
      var book = drop.getItem();
      require(
          LibraryBooks.isKnowledgeBook(book)
              && book.getCount() == 1
              && book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
                      .getLevel(sharpness)
                  == 5,
          "Both books are Sharpness V knowledge");
      if (EnchantmentUtil.isReplicated(book)) replicated++;
      drop.discard();
    }
    require(replicated == 1, "Exactly one book was marked as a copy");
  }

  private static void verifyLoot(ServerLevel level) {
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(level.getSharedSpawnPos()))
            .create(LootContextParamSets.CHEST);
    for (String id : DISCOVERY_TABLES) {
      var loot = lootTable(level, id);
      require(
          rollForDiscovery(loot, params, LibraryEnchantingRegression::higherLevelBook),
          "Expedition table supplies discoveries above level II: " + id);
    }
    for (String id : SMALL_RUIN_TABLES) {
      var loot = lootTable(level, id);
      require(
          !rollForDiscovery(loot, params, LibraryBooks::isAncient),
          "Small ruin has no library discovery: " + id);
    }
  }

  private static LootTable lootTable(ServerLevel level, String id) {
    var table =
        level
            .getServer()
            .reloadableRegistries()
            .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(id)));
    require(table != LootTable.EMPTY, "Live loot table exists: " + id);
    return table;
  }

  private static boolean higherLevelBook(ItemStack stack) {
    return LibraryBooks.isKnowledgeBook(stack)
        && stack
            .getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
            .entrySet()
            .stream()
            .anyMatch(effect -> effect.getIntValue() > 2);
  }

  private static boolean rollForDiscovery(
      LootTable table, LootParams params, Predicate<ItemStack> discovery) {
    for (long seed = 1; seed <= 64; seed++) {
      if (table.getRandomItems(params, seed * 0x9e3779b97f4a7c15L).stream().anyMatch(discovery))
        return true;
    }
    return false;
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
