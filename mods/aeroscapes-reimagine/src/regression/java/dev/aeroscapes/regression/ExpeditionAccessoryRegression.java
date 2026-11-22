package dev.aeroscapes.regression;

import com.herobrot.tieredneo.reforge.ReforgeMenu;
import dev.aeroscapes.equipment.expedition.ExceptionalAccessory;
import dev.aeroscapes.equipment.expedition.StoredBreath;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

@EventBusSubscriber(modid = "aeroscapes_regression")
public final class ExpeditionAccessoryRegression {
  private ExpeditionAccessoryRegression() {}

  @SubscribeEvent
  public static void onServerStarted(ServerStartedEvent event) {
    if (!Boolean.getBoolean("aeroscapes.regression.expedition_accessories")) return;
    verify(event.getServer().overworld());
    com.mojang.logging.LogUtils.getLogger().info("Expedition accessories regression passed");
  }

  private static void verify(ServerLevel level) {
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.randomUUID(),
            "PearlFixture",
            Vec3.atBottomCenterOf(level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 3)),
            null)) {
      var player = session.player;
      require(
          RaceIdentity.choose(
              player,
              Race.HUMAN,
              0,
              CharacterClass.WARRIOR,
              "quick_block",
              Profession.BLACKSMITH,
              "material_repair"),
          "Character is ready");
      APIUtils.setLevel("crafting", player, 0);
      craftNecklace(player, ClickType.PICKUP, true, 10);
      craftNecklace(player, ClickType.QUICK_MOVE, false, 9000);
      APIUtils.setLevel("crafting", player, 45);
      craftNecklace(player, ClickType.QUICK_MOVE, true, 700);
      craftNecklace(player, ClickType.SWAP, true, 700);
      verifyFrostwardCraft(level, player);
      verifyLoot(level, player);
      verifyBorrowedSpell(player, session, ExceptionalAccessory.INVISIBILITY);
      verifyBorrowedSpell(player, session, ExceptionalAccessory.FROSTWAVE);
      verifyBreath(level, player);
    }
  }

  private static void verifyFrostwardCraft(ServerLevel level, ServerPlayer player) {
    var shard = new ItemStack(item("irons_spellbooks:permafrost_shard"));
    var scrap = new ItemStack(item("irons_spellbooks:mithril_scrap"));
    var input = CraftingInput.of(2, 2, List.of(shard, ItemStack.EMPTY, ItemStack.EMPTY, scrap));
    var recipe =
        level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
    var automated = recipe.value().assemble(input, level.registryAccess());
    require(
        automated.is(ExceptionalAccessory.FROSTWAVE.item())
            && !ExceptionalAccessory.FROSTWAVE.matches(automated),
        "Ordinary machine recipe assembly returns the base Frostward Ring");
    var table =
        new CraftingMenu(
            2, player.getInventory(), ContainerLevelAccess.create(level, player.blockPosition()));
    try {
      player.containerMenu = table;
      table.getSlot(1).set(shard);
      table.getSlot(5).set(scrap);
      var preview = table.getSlot(0).getItem();
      require(
          preview.is(ExceptionalAccessory.FROSTWAVE.item()) && !marked(preview),
          "Frostward Ring still previews its ordinary result");
      player.getInventory().items.replaceAll(ignored -> new ItemStack(Items.COBBLESTONE, 64));
      player.getRandom().setSeed(seedFor(700));
      table.clicked(0, 0, ClickType.QUICK_MOVE, player);
      require(
          table.getSlot(1).hasItem() && table.getSlot(5).hasItem() && !marked(preview),
          "A full inventory never rolls or consumes a crafting result");
      player.getInventory().clearContent();
      table.clicked(0, 0, ClickType.QUICK_MOVE, player);
      require(
          player.getInventory().items.stream().anyMatch(ExceptionalAccessory.FROSTWAVE::matches),
          "The committed Frostward result transfers to the inventory");
    } finally {
      player.containerMenu = player.inventoryMenu;
      player.getInventory().clearContent();
    }
  }

  private static void verifyBorrowedSpell(
      ServerPlayer player, TestShipPlayerSession session, ExceptionalAccessory accessory) {
    var curios = CuriosApi.getCuriosInventory(player).orElseThrow();
    var ring = markedCopy(accessory);
    var spell = accessory.spell();
    var magic = MagicData.getPlayerMagicData(player);
    magic.setMana(1000);
    var ordinary = new ItemStack(accessory.item());
    curios.setEquippedCurio("ring", 0, ordinary);
    PreparedAbilities.edit(player, 0, accessory.id);
    require(
        PreparedAbilities.preparation(player).slots().getFirst().isEmpty(),
        "Ordinary ring cannot prepare the borrowed spell");
    curios.setEquippedCurio("ring", 0, ring);
    PreparedAbilities.edit(player, 0, accessory.id);
    PreparedAbilities.select(player, 0);
    require(!SpellKnowledge.hasFocus(player), "Test cast uses no spellbook");
    float mana = magic.getMana();
    PreparedAbilities.cast(player);
    require(
        magic.isCasting() && magic.getCastingSpellLevel() == 1,
        "Exceptional " + accessory + " initiates native rank 1");
    AbilitiesRegression.complete(player);
    require(magic.getMana() < mana, "Borrowed cast spends native mana");
    int cooldown = PreparedAbilities.cooldownTicks(player, spell);
    require(cooldown > 0, "Borrowed cast starts shared spell cooldown");
    if (accessory == ExceptionalAccessory.INVISIBILITY)
      require(player.hasEffect(MobEffectRegistry.TRUE_INVISIBILITY), "Native invisibility applies");
    curios.setEquippedCurio("ring", 0, ItemStack.EMPTY);
    PreparedAbilities.sync(player, false);
    NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    require(session.abilities().slots().getFirst().isEmpty(), "Removing the ring masks its slot");
    if (accessory == ExceptionalAccessory.INVISIBILITY)
      require(
          !player.hasEffect(MobEffectRegistry.TRUE_INVISIBILITY),
          "Removing the ring ends its effect");
    curios.setEquippedCurio("ring", 0, markedCopy(accessory));
    PreparedAbilities.cast(player);
    require(
        !magic.isCasting() && PreparedAbilities.cooldownTicks(player, spell) == cooldown,
        "Switching rings cannot bypass the shared cooldown");
    curios.setEquippedCurio("ring", 0, ItemStack.EMPTY);
    PreparedAbilities.edit(player, 0, "");
  }

  private static void verifyBreath(ServerLevel level, ServerPlayer player) {
    var necklace = markedCopy(ExceptionalAccessory.BREATH);
    var curios = CuriosApi.getCuriosInventory(player).orElseThrow();
    curios.setEquippedCurio("necklace", 0, necklace);
    PreparedAbilities.edit(player, 0, ExceptionalAccessory.BREATH.id);
    PreparedAbilities.select(player, 0);
    for (int tick = 0; tick < 40; tick++) StoredBreath.tick(player);
    require(StoredBreath.charged(player), "Breath recharges while wearing the necklace in air");
    var chargedCopy = necklace.copy();
    curios.setEquippedCurio("necklace", 0, markedCopy(ExceptionalAccessory.BREATH));
    require(!StoredBreath.charged(player), "An uncharged copy cannot inherit a reserve");
    curios.setEquippedCurio("necklace", 0, necklace);
    var water = BlockPos.containing(player.getEyePosition());
    var previous = level.getBlockState(water);
    try {
      level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
      player.baseTick();
      player.setAirSupply(50);
      PreparedAbilities.cast(player);
      require(player.getAirSupply() == 130, "Underwater release adds four seconds of air");
      require(!StoredBreath.charged(player), "Release spends the reserve");
      curios.setEquippedCurio("necklace", 0, chargedCopy);
      for (int tick = 0; tick < 60; tick++) StoredBreath.tick(player);
      require(
          !StoredBreath.charged(player),
          "Switching to a charged copy cannot bypass recovery below water");
    } finally {
      level.setBlockAndUpdate(water, previous);
      curios.setEquippedCurio("necklace", 0, ItemStack.EMPTY);
    }
    player.baseTick();
    verifyBreathRecipient(level, player, chargedCopy);
  }

  private static void verifyBreathRecipient(
      ServerLevel level, ServerPlayer player, ItemStack chargedCopy) {
    try (var recipient =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "BreathRecipient", player.position().add(0, 0, 2), null)) {
      var ally = recipient.player;
      var recipientCurios = CuriosApi.getCuriosInventory(ally).orElseThrow();
      recipientCurios.setEquippedCurio("necklace", 0, chargedCopy);
      require(StoredBreath.charged(ally), "A new wearer receives the physical item's charge");
      recipientCurios.setEquippedCurio("necklace", 0, ItemStack.EMPTY);
      var curios = CuriosApi.getCuriosInventory(player).orElseThrow();
      curios.setEquippedCurio("necklace", 0, chargedCopy);
      for (int tick = 0; tick < 40; tick++) StoredBreath.tick(player);
      require(StoredBreath.charged(player), "Returning to air permits the next breath");
      var water = BlockPos.containing(ally.getEyePosition());
      var previous = level.getBlockState(water);
      try {
        level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
        ally.baseTick();
        ally.setAirSupply(50);
        player.setYRot(0);
        player.setXRot(0);
        PreparedAbilities.cast(player);
        require(ally.getAirSupply() == 130, "Aiming at a submerged ally shares the breath");
        require(!StoredBreath.charged(player), "Sharing the breath spends its charge");
      } finally {
        level.setBlockAndUpdate(water, previous);
        curios.setEquippedCurio("necklace", 0, ItemStack.EMPTY);
      }
    }
  }

  private static void verifyLoot(ServerLevel level, ServerPlayer player) {
    List<String> locations =
        List.of(
            "irons_spellbooks:chests/evoker_fort",
            "irons_spellbooks:chests/mountain_tower/ice_barrel",
            "minecraft:chests/underwater_ruin_big");
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, player.position())
            .create(LootContextParamSets.CHEST);
    for (var accessory : ExceptionalAccessory.values()) {
      var location = ResourceLocation.parse(locations.get(accessory.ordinal()));
      var table =
          level
              .getServer()
              .reloadableRegistries()
              .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, location));
      ItemStack found = ItemStack.EMPTY;
      for (long seed = 1; seed <= 256 && found.isEmpty(); seed++) {
        found =
            table.getRandomItems(params, seed * 0x9e3779b97f4a7c15L).stream()
                .filter(accessory::matches)
                .findFirst()
                .orElse(ItemStack.EMPTY);
      }
      require(
          !found.isEmpty(), "Native chest loot supplies marked " + accessory + " from " + location);
      if (accessory != ExceptionalAccessory.BREATH) continue;
      var materials = com.herobrot.tieredneo.api.TieredNeoAPI.getReforgeBaseItems(found.getItem());
      require(!materials.isEmpty(), "Exceptional base item keeps its reforge material");
      var menu = new ReforgeMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
      menu.getSlot(0).set(new ItemStack(materials.getFirst(), 2));
      menu.getSlot(1).set(found);
      menu.getSlot(2).set(new ItemStack(Items.LAPIS_LAZULI, 2));
      menu.reforge();
      require(
          menu.getSlot(0).getItem().getCount() == 1 && accessory.matches(menu.getSlot(1).getItem()),
          "Paid reforge retains the variant's marker");
    }
  }

  private static ItemStack markedCopy(ExceptionalAccessory accessory) {
    var result = new ItemStack(accessory.item());
    result.set(DataComponents.CUSTOM_DATA, CustomData.of(ExceptionalAccessory.mark()));
    return result;
  }

  private static void craftNecklace(
      ServerPlayer player, ClickType click, boolean exceptional, int roll) {
    player.getInventory().clearContent();
    var menu = player.inventoryMenu;
    var necklace = item("crittersandcompanions:pearl_necklace_3");
    menu.getSlot(1).set(new ItemStack(item("crittersandcompanions:pearl")));
    menu.getSlot(2).set(new ItemStack(item("crittersandcompanions:pearl_necklace_2")));
    var preview = menu.getSlot(0).getItem();
    require(preview.is(necklace), "The native necklace recipe assembled");
    require(!marked(preview), "Crafting preview did not reveal a roll");
    player.getRandom().setSeed(seedFor(roll));
    menu.clicked(0, 0, click, player);
    ItemStack result =
        click == ClickType.PICKUP
            ? menu.getCarried()
            : player.getInventory().items.stream()
                .filter(stack -> stack.is(necklace))
                .findFirst()
                .orElse(ItemStack.EMPTY);
    require(result.is(necklace), "The result transfers with " + click);
    require(marked(result) == exceptional, "The received necklace rolls once with " + click);
    require(
        menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(),
        "The recipe consumes inputs with " + click);
    menu.setCarried(ItemStack.EMPTY);
  }

  private static boolean marked(ItemStack stack) {
    var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
    return data != null && data.copyTag().getBoolean("aeroscapes_reimagine:expedition_accessory");
  }

  private static long seedFor(int roll) {
    var random = RandomSource.create();
    for (long seed = 0; seed < 1_000_000; seed++) {
      random.setSeed(seed);
      if (random.nextInt(10000) == roll) return seed;
    }
    throw new IllegalStateException("No seed with first craft roll " + roll);
  }

  private static Item item(String id) {
    return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
