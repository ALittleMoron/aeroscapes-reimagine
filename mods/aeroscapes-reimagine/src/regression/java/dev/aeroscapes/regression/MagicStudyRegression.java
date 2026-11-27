package dev.aeroscapes.regression;

import dev.aeroscapes.magic.AbilityNetwork;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.magic.SpellProgression;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.registries.BlockRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

final class MagicStudyRegression {
  private static final ResourceLocation MAGIC =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
  private static final String ACCESS = "magic_mastery_1";
  private static final String DESTRUCTION = "magic_destruction_power";
  private static final String DESTRUCTION_II = "magic_destruction_mastery_2";
  private static final String MISSILE_NODE = "magic_spell_magic_missile";

  private MagicStudyRegression() {}

  static void verify(ServerLevel level) {
    var tablePos = level.getSharedSpawnPos().above(32).east(2);
    Vec3 position = Vec3.atBottomCenterOf(tablePos.west(2));
    var original = level.getBlockState(tablePos);
    level.setBlockAndUpdate(
        tablePos, BlockRegistry.INSCRIPTION_TABLE_BLOCK.get().defaultBlockState());
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "FirstStudy", position, null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        initialize(player);
        var menu =
            new InscriptionTableMenu(
                39, player.getInventory(), ContainerLevelAccess.create(level, tablePos));
        player.containerMenu = menu;
        require(menu.stillValid(player), "The fixture uses a real inscription table in range");
        AbstractSpell missile = SpellKnowledge.spell("irons_spellbooks:magic_missile");
        AbstractSpell fireball = SpellKnowledge.spell("irons_spellbooks:fireball");
        verifyScrollCredit(session, missile, fireball);
        ItemStack book = verifyFirstStudy(session, menu, missile);
        buy(player, ACCESS);
        verifyAdvancedStudy(session, menu, missile, fireball);
        verifyReusableMissile(player, missile, book);
      } finally {
        level
            .getEntitiesOfClass(
                Projectile.class,
                player.getBoundingBox().inflate(16),
                projectile -> projectile.getOwner() == player)
            .forEach(Projectile::discard);
        ProgressionRegression.listPlayer(level, player, false);
      }
    } finally {
      level.setBlockAndUpdate(tablePos, original);
    }
  }

  private static void initialize(ServerPlayer player) {
    var magic = MagicData.getPlayerMagicData(player);
    magic.setServerPlayer(player);
    magic.getSyncedData();
    magic.setMana(1000);
    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
    require(
        RaceIdentity.choose(
            player,
            Race.HUMAN,
            0,
            CharacterClass.WARRIOR,
            CharacterClass.WARRIOR.starters().getFirst(),
            Profession.BLACKSMITH,
            "material_repair"),
        "Only character creation is needed to begin with single-use scrolls");
    player.setXRot(-60);
  }

  private static void verifyScrollCredit(
      TestShipPlayerSession session, AbstractSpell missile, AbstractSpell fireball) {
    var player = session.player;
    var example = offer(session, missile);
    require(
        example.casts() == 0 && example.requiredCasts() == 3 && example.requiredMastery() == 0,
        "Magic Missile shows three uses and no mastery requirement from the start");
    require(
        session.abilities().studyEntries().stream()
            .noneMatch(entry -> entry.spellId().equals(fireball.getSpellId())),
        "Unseen advanced scroll requirements remain undiscovered");
    ItemStack unspent = AbilitiesRegression.scroll(fireball, 2, 1);
    player.setItemInHand(InteractionHand.MAIN_HAND, unspent);
    PreparedAbilities.sync(player, false);
    var advanced = offer(session, fireball);
    require(
        advanced.casts() == 0 && advanced.requiredCasts() == 6 && advanced.requiredMastery() == 2,
        "Possession reveals advanced requirements without counting as a consumed cast");
    long credit = MagicPractice.credit(player);
    startScroll(player, fireball, unspent);
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    AbilitiesRegression.complete(player);
    PreparedAbilities.sync(player, false);
    require(
        unspent.getCount() == 1
            && offer(session, fireball).casts() == 0
            && session.takeStudyProgress() == null
            && MagicPractice.credit(player) == credit,
        "A canceled delayed physical cast neither consumes a scroll nor credits study or practice");

    ItemStack scrolls = AbilitiesRegression.scroll(missile, 2, 3);
    for (int cast = 1; cast <= 3; cast++) {
      castScroll(player, missile, scrolls);
      PreparedAbilities.sync(player, false);
      require(
          offer(session, missile).casts() == cast && rank(player, missile) == 0,
          "Each physical Magic Missile cast adds one use without automatically teaching it");
      var progress = session.takeStudyProgress();
      require(
          progress != null
              && java.util.Arrays.equals(
                  progress.getArgs(), new Object[] {missile.getDisplayName(player), cast, 3})
              && session.takeStudyCompleted() == null,
          "Each consumed scroll reports the spell and its cast count, including the final use");
    }
    require(
        scrolls.isEmpty()
            && MagicPractice.credit(player) > credit
            && !SpellKnowledge.unlocked(player).contains(ACCESS),
        "The three previously consumed scrolls are the complete material cost");
  }

  private static ItemStack verifyFirstStudy(
      TestShipPlayerSession session, InscriptionTableMenu menu, AbstractSpell missile) {
    var player = session.player;
    int id = menu.containerId;
    request(session, id, missile);
    require(rank(player, missile) == 0, "First study requires a book in the table input");
    var book = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());
    menu.getSpellBookSlot().set(book);
    request(session, id + 1, missile);
    Vec3 position = player.position();
    player.setPos(position.add(16, 0, 0));
    request(session, id, missile);
    player.setPos(position);
    var unbound = new InscriptionTableMenu(id, player.getInventory(), ContainerLevelAccess.NULL);
    unbound.getSpellBookSlot().set(book.copy());
    player.containerMenu = unbound;
    request(session, id, missile);
    player.containerMenu = menu;
    require(
        rank(player, missile) == 0, "Wrong container, out-of-range or unbound table cannot teach");
    var provider = SkillsMod.getInstance();
    int remaining = provider.getPointsLeft(player, MAGIC).orElseThrow();
    request(session, id, missile);
    require(
        rank(player, missile) == 1
            && offer(session, missile).reason().equals("study_complete")
            && SpellProgression.studyGrants(player).contains(MISSILE_NODE)
            && SpellKnowledge.unlocked(player).contains(MISSILE_NODE)
            && provider.getPointsLeft(player, MAGIC).orElseThrow() == remaining,
        "First-study packet grants rank 1, the free dedicated node and a completed snapshot");
    require(
        menu.getSpellBookSlot().getItem().getCount() == 1
            && menu.getScrollSlot().getItem().isEmpty(),
        "First study keeps the book and needs no fourth physical copy");
    require(
        new AbilityNetwork.StudyCompleted(missile.getSpellId(), 1)
            .equals(session.takeStudyCompleted()),
        "Completing first study sends the studied spell and rank for the client notification");
    require(
        SpellKnowledge.knowledgeReason(player, missile, SpellKnowledge.unlocked(player))
            .equals("magic_access"),
        "Learning before Magic Access does not yet allow repeatable use");
    request(session, id, missile);
    require(
        rank(player, missile) == 1
            && provider.getPointsLeft(player, MAGIC).orElseThrow() == remaining
            && offer(session, missile).casts() == 3
            && session.takeStudyCompleted() == null,
        "Duplicated first study cannot raise rank, grant points, or duplicate practice");
    return book;
  }

  private static void verifyAdvancedStudy(
      TestShipPlayerSession session,
      InscriptionTableMenu menu,
      AbstractSpell missile,
      AbstractSpell fireball) {
    var player = session.player;
    ItemStack scrolls = AbilitiesRegression.scroll(fireball, 2, 6);
    for (int cast = 0; cast < 6; cast++) castScroll(player, fireball, scrolls);
    request(session, menu.containerId, fireball);
    require(
        offer(session, fireball).casts() == 6
            && offer(session, fireball).reason().equals("study_mastery")
            && rank(player, fireball) == 0,
        "Even all advanced physical uses still require matching mastery");
    session.takeStudyProgress();
    castScroll(player, fireball, AbilitiesRegression.scroll(fireball, 2, 1));
    require(
        session.takeStudyProgress() == null,
        "Additional casts after reaching the requirement do not repeat the progress notice");
    var betterMissile = AbilitiesRegression.scroll(missile, 2, 2);
    menu.getScrollSlot().set(betterMissile);
    require(
        !menu.clickMenuButton(player, -1) && betterMissile.getCount() == 2,
        "The original rank-up button preserves a stronger scroll above mastery");

    MagicPractice.award(player, 501);
    ProgressionBridge.sync(player);
    buy(player, DESTRUCTION);
    buy(player, DESTRUCTION_II);
    request(session, menu.containerId, fireball);
    require(
        rank(player, fireball) == 1 && betterMissile.getCount() == 2,
        "Advanced first study also begins at rank 1 and leaves the physical rank-up input intact");
    require(
        menu.clickMenuButton(player, -1)
            && betterMissile.getCount() == 1
            && rank(player, missile) == 2,
        "The original rank-up button consumes one stronger scroll after mastery");
    require(
        new AbilityNetwork.StudyCompleted(missile.getSpellId(), 2)
            .equals(session.takeStudyCompleted()),
        "Studying a stronger scroll confirms its new rank");

    SkillsMod.getInstance().lockSkill(player, MAGIC, DESTRUCTION_II);
    require(
        rank(player, missile) == 2
            && SpellKnowledge.knowledgeReason(player, missile, SpellKnowledge.unlocked(player))
                .equals("mastery"),
        "Losing mastery retains a studied rank and blocks casting above its current limit");
    buy(player, DESTRUCTION_II);
    require(
        rank(player, missile) == 2, "Restoring mastery makes the existing studied rank available");
  }

  private static void verifyReusableMissile(
      ServerPlayer player, AbstractSpell spell, ItemStack book) {
    player.containerMenu = player.inventoryMenu;
    Utils.setPlayerSpellbookStack(player, book.copy());
    PreparedAbilities.edit(player, 0, spell.getSpellId());
    PreparedAbilities.select(player, 0);
    var magic = MagicData.getPlayerMagicData(player);
    magic.getPlayerCooldowns().clearCooldowns();
    float mana = magic.getMana();
    PreparedAbilities.cast(player);
    AbilitiesRegression.complete(player);
    require(
        magic.getMana() < mana && PreparedAbilities.cooldownTicks(player, spell) > 0,
        "The studied spell becomes a repeatable cast with access, mastery, preparation and focus");
  }

  private static void buy(ServerPlayer player, String node) {
    SkillsMod.getInstance().tryUnlockSkill(player, MAGIC, node, false);
    require(
        SpellKnowledge.unlocked(player).contains(node),
        "Buy " + node + " in the native Magic bank");
  }

  private static void castScroll(ServerPlayer player, AbstractSpell spell, ItemStack scrolls) {
    int before = scrolls.getCount();
    startScroll(player, spell, scrolls);
    AbilitiesRegression.complete(player);
    require(
        scrolls.getCount() == before - 1,
        "Each completed native cast consumes one physical scroll");
  }

  private static void startScroll(ServerPlayer player, AbstractSpell spell, ItemStack scrolls) {
    player.setItemInHand(InteractionHand.MAIN_HAND, scrolls);
    require(
        spell.attemptInitiateCast(
            scrolls, 2, player.level(), player, CastSource.SCROLL, false, "mainhand"),
        "Begin native physical scroll cast " + spell.getSpellId());
  }

  private static void request(TestShipPlayerSession session, int containerId, AbstractSpell spell) {
    session.receivePlayPayload(new AbilityNetwork.FirstStudy(containerId, spell.getSpellId()), "6");
  }

  private static AbilityNetwork.StudyEntry offer(
      TestShipPlayerSession session, AbstractSpell spell) {
    return session.abilities().studyEntries().stream()
        .filter(entry -> entry.spellId().equals(spell.getSpellId()))
        .findFirst()
        .orElseThrow();
  }

  private static int rank(ServerPlayer player, AbstractSpell spell) {
    return SpellKnowledge.rank(player, spell, SpellKnowledge.unlocked(player));
  }

  private static void require(boolean condition, String description) {
    if (!condition) throw new IllegalStateException(description);
  }
}
