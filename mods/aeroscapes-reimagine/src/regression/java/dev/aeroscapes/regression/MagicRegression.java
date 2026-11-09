package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.MagicHelper;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.registries.EntityRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

final class MagicRegression {
  private MagicRegression() {}

  static void verify(ServerLevel level) {
    // The provider applies staged datapack settings at sync, normally on player login.
    level.getServer().getPlayerList().reloadResources();
    var wings = SpellRegistry.ANGEL_WINGS_SPELL.get();
    var portal = SpellRegistry.PORTAL_SPELL.get();
    var teleport = SpellRegistry.TELEPORT_SPELL.get();
    var missile = SpellRegistry.MAGIC_MISSILE_SPELL.get();
    require(!wings.isEnabled() && !portal.isEnabled(), "Travel spells are disabled");
    require(teleport.isEnabled() && teleport.getSpellCooldown() == 400, "Short teleport cooldown");
    require(teleport.getMaxLevel() == 1, "Only level one teleport is generated");

    var position = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
    var chest =
        level
            .getServer()
            .reloadableRegistries()
            .getLootTable(
                ResourceKey.create(
                    Registries.LOOT_TABLE,
                    ResourceLocation.parse("irons_spellbooks:chests/priest_house")));
    var params =
        new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, position)
            .create(LootContextParamSets.CHEST);
    Set<String> starterSpells = new HashSet<>();
    ItemStack starterScroll = ItemStack.EMPTY;
    for (int roll = 1; roll <= 32; roll++) {
      for (var stack : chest.getRandomItems(params, roll * 0x9e3779b97f4a7c15L)) {
        if (!stack.is(ItemRegistry.SCROLL.get())) continue;
        var spell = ISpellContainer.getOrCreate(stack).getSpellAtIndex(0);
        require(spell.getSpell().isEnabled(), "Disabled spell in priest house loot");
        if (spell.getLevel() == 1) {
          starterSpells.add(spell.getSpell().getSpellId());
          if (spell.getSpell() == missile) starterScroll = stack;
        }
      }
    }
    require(
        starterSpells.containsAll(
            Set.of("irons_spellbooks:magic_missile", "irons_spellbooks:healing_circle")),
        "Both starter spells drop at level one");

    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "MagicFixture", position, null)) {
      var player = session.player;
      var oldScroll = new ItemStack(ItemRegistry.SCROLL.get());
      ISpellContainer.createScrollContainer(wings, 5, oldScroll);
      player.setItemInHand(InteractionHand.MAIN_HAND, oldScroll);
      require(
          !oldScroll.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction()
              && oldScroll.getCount() == 1,
          "Disabled wings cannot be cast or consume a saved scroll");
      var recallScroll = new ItemStack(ItemRegistry.SCROLL.get());
      ISpellContainer.createScrollContainer(SpellRegistry.RECALL_SPELL.get(), 1, recallScroll);
      player.setItemInHand(InteractionHand.MAIN_HAND, recallScroll);
      require(
          !recallScroll.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction()
              && recallScroll.getCount() == 1,
          "Recall scroll is preserved for inscription");
      require(teleport.getLevelFor(5, player) == 1, "A saved high-level teleport is capped");
      ItemStack copper = Items.COPPER_INGOT.getDefaultInstance();
      ItemStack paper = Items.PAPER.getDefaultInstance();
      var input =
          CraftingInput.of(
              3,
              3,
              List.of(
                  copper.copy(),
                  paper.copy(),
                  paper.copy(),
                  Items.STRING.getDefaultInstance(),
                  paper.copy(),
                  paper.copy(),
                  copper.copy(),
                  paper.copy(),
                  paper.copy()));
      var recipe =
          level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
      ItemStack book = recipe.value().assemble(input, level.registryAccess());
      require(
          book.is(ItemRegistry.COPPER_SPELL_BOOK.get()) && book.getCount() == 1,
          "Native copper spell book recipe");
      require(
          recipe.value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
          "Copper spell book recipe has no remainders");

      var inscription =
          new InscriptionTableMenu(1, player.getInventory(), ContainerLevelAccess.NULL);
      inscription.getSpellBookSlot().set(book);
      inscription.getScrollSlot().set(starterScroll);
      inscription.clickMenuButton(player, 0);
      inscription.clickMenuButton(player, -1);
      require(
          inscription.getScrollSlot().getItem().isEmpty(), "Inscription consumes the found scroll");
      var inscribed = ISpellContainer.getOrCreate(book).getSpellAtIndex(0);
      require(
          inscribed.getSpell() == missile && inscribed.getLevel() == 1,
          "Book contains the found spell");

      var magic = MagicData.getPlayerMagicData(player);
      magic.setMana(100);
      float before = magic.getMana();
      require(
          missile.attemptInitiateCast(
              book, inscribed.getLevel(), level, player, CastSource.SPELLBOOK, true, "spellbook"),
          "Starter spell still starts a cast");
      ((MagicManager) MagicHelper.MAGIC_MANAGER).tick(level);
      require(
          magic.getMana() < before && magic.getPlayerCooldowns().isOnCooldown(missile),
          "Completed book cast spends mana and starts cooldown");
      require(!magic.isCasting(), "Cast state returns to idle");
      var missiles =
          level.getEntities(
              EntityRegistry.MAGIC_MISSILE_PROJECTILE.get(),
              player.getBoundingBox().inflate(2),
              projectile -> projectile.getOwner() == player);
      require(missiles.size() == 1, "Completed cast spawns the native projectile");
      missiles.forEach(Entity::discard);
    }
    LogUtils.getLogger().info("Magic regression passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
