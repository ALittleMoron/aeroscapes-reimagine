package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.magic.Preparation;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.magic.travel.TravelMagic;
import io.redspace.ironsspellbooks.IronsSpellbooks;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.block.portal_frame.PortalFrameBlock;
import io.redspace.ironsspellbooks.block.portal_frame.PortalFrameBlockEntity;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.capabilities.magic.PocketDimensionManager;
import io.redspace.ironsspellbooks.capabilities.magic.PortalManager;
import io.redspace.ironsspellbooks.compat.Curios;
import io.redspace.ironsspellbooks.entity.spells.portal.PortalData;
import io.redspace.ironsspellbooks.entity.spells.portal.PortalEntity;
import io.redspace.ironsspellbooks.entity.spells.portal.PortalPos;
import io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu;
import io.redspace.ironsspellbooks.registries.BlockRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.spells.ender.PortalSpell;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;

/** Real server players exercise native skills, Curios, inscription and cast completion. */
public final class AbilitiesRegression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse("aeroscapes_reimagine:combat");
  private static final String ABILITIES = "aeroscapes_reimagine_abilities";
  private static final String PORTAL_EXPIRES = "aeroscapes_reimagine:portal_expires_at";

  private AbilitiesRegression() {}

  public static void start(ServerLevel level) {
    var listener =
        new Consumer<ServerTickEvent.Post>() {
          @Override
          public void accept(ServerTickEvent.Post event) {
            if (event.getServer() != level.getServer()) return;
            NeoForge.EVENT_BUS.unregister(this);
            verify(level);
          }
        };
    NeoForge.EVENT_BUS.addListener(listener);
  }

  public static void verify(ServerLevel level) {
    require(level == level.getServer().overworld(), "Ability fixture runs in the overworld");
    require(
        IronsSpellbooks.MCS == level.getServer() && IronsSpellbooks.OVERWORLD == level,
        "Native Iron's server-started initialization must finish before the ability fixture");
    // Native spell settings are applied at datapack sync, normally during login.
    level.getServer().getPlayerList().reloadResources();
    verifyCatalogue();
    Vec3 origin = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 48, 0);
    UUID identity = UUID.randomUUID();
    CompoundTag saved;
    CompoundTag talents = new CompoundTag();
    Preparation expected;
    int cooldown;
    try (var session =
        TestShipPlayerSession.create(level, identity, "AbilitiesFixture", origin, null)) {
      ServerPlayer player = session.player;
      initialize(player);
      verifyUnlockAndStudy(player);
      verifyAssignments(player);
      verifyNativeCasting(player);
      verifyEldritch(player);
      verifyIndependentScrolls(level, origin);
      AbstractSpell firebolt = spell("firebolt");
      PreparedAbilities.edit(player, 0, firebolt.getSpellId());
      PreparedAbilities.select(player, 0);
      magic(player).getPlayerCooldowns().addCooldown(firebolt, 600);
      postTick(player);
      expected = PreparedAbilities.preparation(player);
      cooldown = PreparedAbilities.cooldownTicks(player, firebolt);
      verifyDeathClone(player, expected, cooldown);
      SkillsMod.getInstance().exportPlayerData(player, talents);
      NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
      saved = player.saveWithoutId(new CompoundTag());
    }
    try (var session =
        TestShipPlayerSession.create(level, identity, "AbilitiesFixture", origin, saved)) {
      ServerPlayer restored = session.player;
      initialize(restored);
      SkillsMod.getInstance().importPlayerData(restored, talents);
      require(
          PreparedAbilities.preparation(restored).equals(expected),
          "Native player save/load retains all prepared slots and selection");
      require(
          rank(restored, spell("firebolt")) == 3,
          "Personal studied rank survives native player save/load");
      require(
          PreparedAbilities.cooldownTicks(restored, spell("firebolt")) >= cooldown,
          "Relog retains the ordinary native cooldown deadline");
    }
    verifyWings(level, origin);
    verifyRecallScroll(level, origin);
    verifyPortalScrollSource(level, origin);
    verifyPortals(level, origin);
    verifyPocketDimension(level, origin);
    LogUtils.getLogger().info("Prepared abilities regression passed");
  }

  private static void verifyDeathClone(ServerPlayer original, Preparation expected, int cooldown) {
    verifyDeathClone(
        original,
        replacement -> {
          require(
              PreparedAbilities.preparation(replacement).equals(expected)
                  && rank(replacement, spell("firebolt")) == 3,
              "Death clone retains personal ranks and prepared slots");
          require(
              PreparedAbilities.cooldownTicks(replacement, spell("firebolt")) >= cooldown,
              "Death clone restores the ordinary cooldown instead of resetting it");
        });
  }

  private static void verifyDeathClone(ServerPlayer original, Consumer<ServerPlayer> verification) {
    // Use the native respawn copy operation while the original login state is still live.
    var replacement =
        new ServerPlayer(
            original.server,
            original.serverLevel(),
            original.getGameProfile(),
            original.clientInformation());
    replacement.connection = original.connection;
    try {
      replacement.restoreFrom(original, false);
      verification.accept(replacement);
    } finally {
      replacement.getTextFilter().leave();
      replacement.getAdvancements().stopListening();
    }
  }

  private static void verifyCatalogue() {
    var provider = SkillsMod.getInstance();
    List<AbstractSpell> spells = SpellKnowledge.spells();
    require(spells.size() == 111, "Exact pinned registry has 111 spell routes: " + spells.size());
    Set<String> expected = new HashSet<>();
    Set<String> schools = new HashSet<>();
    for (AbstractSpell spell : spells) {
      String node = node(spell);
      require(provider.hasSkill(COMBAT, node), "Registered spell lacks native talent: " + node);
      require(
          expected.add(spell.getSpellId()), "Registered spell collision: " + spell.getSpellId());
      schools.add(spell.getSchoolType().getId().getPath());
    }
    Set<String> actual = new HashSet<>(provider.getSkills(COMBAT).orElseThrow());
    actual.removeIf(id -> !id.startsWith("magic_"));
    require(
        actual.equals(MagicTalents.nodes()), "Native talents cover the complete functional tree");
    require(schools.size() == 9, "The native registry has nine schools: " + schools);
    require(
        MagicTalents.totalCost() == MagicTalents.nodes().size(),
        "The complete magical catalogue has its costed budget");
  }

  private static void verifyUnlockAndStudy(ServerPlayer player) {
    AbstractSpell firebolt = spell("firebolt");
    PreparedAbilities.edit(player, 0, firebolt.getSpellId());
    require(
        PreparedAbilities.preparation(player).equals(Preparation.empty()),
        "A valid registered ID cannot prepare an unpurchased ability");
    var provider = SkillsMod.getInstance();
    provider.addPoints(
        player,
        COMBAT,
        ResourceLocation.parse("aeroscapes_reimagine:abilities_fixture"),
        1000,
        false);
    provider.tryUnlockSkill(player, COMBAT, "magic_destruction_mastery_2", false);
    require(
        provider.getSkillState(player, COMBAT, "magic_destruction_mastery_2").orElseThrow()
            != Skill.State.UNLOCKED,
        "A forged purchase cannot skip relevant practice and the introductory path");
    provider.tryUnlockSkill(player, COMBAT, node(firebolt), false);
    require(
        rank(player, firebolt) == 1,
        "The common introductory talent grants immediate rank-one Firebolt");
    long starters =
        SpellKnowledge.spells().stream().filter(spell -> rank(player, spell) == 1).count();
    require(starters == 8, "The first purchased talent grants eight basic spells together");
    require(
        !player
            .getPersistentData()
            .getCompound(ABILITIES)
            .getCompound("ranks")
            .contains(firebolt.getSpellId()),
        "Talent rank one does not manufacture a studied rank");
    InscriptionTableMenu menu = table(player);
    studyRejected(player, menu, firebolt, 3, "Above-mastery scroll remains intact");
    studyRejected(player, menu, firebolt, 1, "Non-improving scroll remains intact");
    unlockMastery(player, firebolt, 2);
    studyAccepted(player, menu, firebolt, 2);
    studyRejected(player, menu, firebolt, 2, "Repeated rank does not consume another scroll");
    require(rank(player, firebolt) == 2, "Rejected future scroll never records a future rank");
    studyAccepted(player, menu, firebolt, 3);
    studyRejected(player, menu, firebolt, 4, "Study retains the personal rank ceiling");
    player.containerMenu = player.inventoryMenu;
  }

  private static void verifyAssignments(ServerPlayer player) {
    List<AbstractSpell> usable =
        SpellKnowledge.spells().stream()
            .filter(
                spell -> MagicTalents.spell(spell.getSpellId()).node().equals("magic_mastery_1"))
            .toList();
    require(usable.size() == 8, "Eight ordinary enabled spells are available");
    for (int index = 0; index < usable.size(); index++) {
      AbstractSpell spell = usable.get(index);
      unlock(player, node(spell));
      PreparedAbilities.edit(player, index, spell.getSpellId());
    }
    Preparation assigned = PreparedAbilities.preparation(player);
    require(
        assigned.slots().equals(usable.stream().map(AbstractSpell::getSpellId).toList()),
        "All eight authoritative slots accept known abilities");
    for (int index : List.of(-1, 8, Integer.MAX_VALUE)) {
      PreparedAbilities.edit(player, index, "");
      PreparedAbilities.select(player, index);
    }
    for (String id : List.of("invalid id", "irons_spellbooks:missing", "x".repeat(129)))
      PreparedAbilities.edit(player, 0, id);
    require(
        PreparedAbilities.preparation(player).equals(assigned),
        "Malformed IDs and spoofed slots cannot change the prepared set");
    PreparedAbilities.edit(player, 7, usable.getFirst().getSpellId());
    var swapped = PreparedAbilities.preparation(player);
    require(
        swapped.slots().get(0).equals(usable.getLast().getSpellId())
            && swapped.slots().get(7).equals(usable.getFirst().getSpellId())
            && new HashSet<>(swapped.slots()).size() == 8,
        "Moving an assigned ability swaps slots without duplicating it");
    PreparedAbilities.edit(player, 7, "");
    require(
        PreparedAbilities.preparation(player).slots().get(7).isEmpty(),
        "Clearing a prepared slot is authoritative");
    PreparedAbilities.select(player, 6);
    require(PreparedAbilities.preparation(player).selected() == 6, "Selection is stored");
    ItemStack focus = focus(player);
    PreparedAbilities.sync(player, true);
    require(
        ISpellContainer.get(focus).getMaxSpellCount() == 8,
        "The native Curios focus projects exactly eight personal slots");
    InscriptionTableMenu menu = table(player);
    for (int index = 0; index < 8; index++) {
      menu.clickMenuButton(player, index);
      require(
          menu.getResultSlot().getItem().isEmpty(),
          "Personal focus projection cannot produce an extracted scroll: " + index);
    }
    player.containerMenu = player.inventoryMenu;
  }

  private static void verifyNativeCasting(ServerPlayer player) {
    AbstractSpell firebolt = spell("firebolt");
    PreparedAbilities.edit(player, 0, firebolt.getSpellId());
    PreparedAbilities.select(player, 0);
    MagicData magic = magic(player);
    magic.getPlayerCooldowns().clearCooldowns();
    magic.setMana(0);
    PreparedAbilities.cast(player);
    require(
        !magic.isCasting() && magic.getMana() == 0, "Prepared cast retains the native mana check");
    magic.setMana(1000);
    float mana = magic.getMana();
    firebolt.castSpell(player.level(), 3, player, CastSource.SPELLBOOK, true);
    require(
        magic.getMana() == mana && !magic.isCasting(),
        "Direct castSpell cannot bypass native checked casting state");
    PreparedAbilities.cast(player);
    require(
        magic.isCasting() && magic.getCastingSpellId().equals(firebolt.getSpellId()),
        "Prepared cast enters the actual native attemptInitiateCast state");
    complete(player);
    require(
        magic.getMana() < mana && PreparedAbilities.cooldownTicks(player, firebolt) > 0,
        "Native MagicManager completion spends mana and starts ordinary cooldown");
    int cooldown = PreparedAbilities.cooldownTicks(player, firebolt);
    PreparedAbilities.edit(player, 7, firebolt.getSpellId());
    require(
        PreparedAbilities.cooldownTicks(player, firebolt) == cooldown,
        "Swapping slots does not reset native cooldown");
    PreparedAbilities.edit(player, 7, "");
    require(
        PreparedAbilities.cooldownTicks(player, firebolt) == cooldown,
        "Unpreparing does not reset native cooldown");
    PreparedAbilities.edit(player, 0, firebolt.getSpellId());
    PreparedAbilities.select(player, 0);
    magic.getPlayerCooldowns().clearCooldowns();
    mana = magic.getMana();
    PreparedAbilities.cast(player);
    require(magic.isCasting(), "Native delayed cast starts before unpreparing");
    PreparedAbilities.edit(player, 0, "");
    require(!magic.isCasting(), "Unpreparing cancels an active native cast immediately");
    complete(player);
    require(magic.getMana() == mana, "Canceled unprepared cast never executes or spends mana");
    PreparedAbilities.edit(player, 0, firebolt.getSpellId());
    magic.getPlayerCooldowns().clearCooldowns();
    PreparedAbilities.cast(player);
    require(magic.isCasting(), "Native delayed cast starts before focus removal");
    ItemStack oldFocus = Utils.getPlayerSpellbookStack(player);
    Utils.setPlayerSpellbookStack(player, ItemStack.EMPTY);
    postTick(player);
    require(!magic.isCasting(), "Removing the native Curios focus cancels an active cast");
    complete(player);
    require(magic.getMana() == mana, "Removed-focus cast cannot finish for free");
    PreparedAbilities.edit(player, 0, "");
    Utils.setPlayerSpellbookStack(player, oldFocus);
    require(
        !firebolt.attemptInitiateCast(
            oldFocus, 3, player.level(), player, CastSource.SPELLBOOK, true, Curios.SPELLBOOK_SLOT),
        "An old inscribed focus cannot cast an ability after it is unprepared");
  }

  private static void verifyEldritch(ServerPlayer player) {
    List<AbstractSpell> discoveries =
        SpellKnowledge.spells().stream().filter(AbstractSpell::requiresLearning).toList();
    require(discoveries.size() == 7, "Exact native registry has seven discovery spells");
    InscriptionTableMenu menu = table(player);
    for (AbstractSpell spell : discoveries) {
      unlockMastery(player, spell, 5);
      unlock(player, node(spell));
      require(
          !spell.isLearned(player),
          "A talent must not grant native discovery: " + spell.getSpellId());
      PreparedAbilities.edit(player, 0, spell.getSpellId());
      require(
          !PreparedAbilities.preparation(player).slots().contains(spell.getSpellId()),
          "Native discovery remains required for preparation: " + spell.getSpellId());
      ItemStack scroll = scroll(spell, 1, 2);
      player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
      CompoundTag before = magicState(player);
      require(
          SpellKnowledge.canStart(spell, player, scroll, 1, CastSource.SCROLL, false),
          "A one-use scroll does not need permanent discovery: " + spell.getSpellId());
      require(
          scroll.getCount() == 2 && magicState(player).equals(before),
          "Checking one-use eligibility preserves stack and native state: " + spell.getSpellId());
      if (spell.getMaxLevel() > 1)
        studyRejected(player, menu, spell, 2, "Discovery remains required for study");
      magic(player).getSyncedData().learnSpell(spell, false);
      require(
          SpellKnowledge.knowledgeReason(player, spell, SpellKnowledge.unlocked(player)).isEmpty(),
          "Native discovery unlocks the purchased spell: " + spell.getSpellId());
    }
    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    player.containerMenu = player.inventoryMenu;
  }

  private static void verifyIndependentScrolls(ServerLevel level, Vec3 origin) {
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "ScrollFixture", origin.add(8, 0, 0), null)) {
      ServerPlayer player = session.player;
      initialize(player);
      AbstractSpell firebolt = spell("firebolt");
      require(
          rank(player, firebolt) == 0
              && PreparedAbilities.preparation(player).equals(Preparation.empty()),
          "A second player's knowledge and preparation are independent");
      require(MagicPractice.level(player) == 0, "The scroll user has no magical practice");
      int scrollRank = firebolt.getMaxLevel();
      ItemStack usable = scroll(firebolt, scrollRank, 3);
      player.setItemInHand(InteractionHand.MAIN_HAND, usable);
      float mana = magic(player).getMana();
      require(
          !firebolt.attemptInitiateCast(
              usable.copy(), scrollRank, level, player, CastSource.SCROLL, false, "mainhand"),
          "A copied scroll request cannot substitute for the actual held stack");
      require(
          firebolt.attemptInitiateCast(
              usable, scrollRank, level, player, CastSource.SCROLL, false, "mainhand"),
          "The highest native scroll rank works without knowledge or mastery");
      complete(player);
      require(
          usable.getCount() == 2
              && rank(player, firebolt) == 0
              && ISpellContainer.get(usable).getSpellAtIndex(0).getLevel() == scrollRank,
          "Actual native scroll completion consumes exactly one and grants no personal rank");
      require(
          magic(player).getMana() == mana,
          "One-use scroll retains its native mana-free cast source");
      require(
          firebolt.attemptInitiateCast(
              usable, scrollRank, level, player, CastSource.SCROLL, false, "mainhand"),
          "Another actual scroll cast begins before its held item is removed");
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      complete(player);
      require(
          usable.getCount() == 2 && magic(player).getMana() == mana,
          "Rejected native scroll completion does not charge the removed stack");
      verifyContinuousScroll(player);
      verifyDiscoveryScroll(player);
      MagicPractice.award(player, MagicPracticeProgress.xpForPoints(MagicTalents.pointCap()));
      for (String node : MagicTalents.nodes()) unlock(player, node);
      for (AbstractSpell spell : SpellKnowledge.spells()) {
        require(
            rank(player, spell) == 1
                && MagicTalents.learned(spell.getSpellId(), SpellKnowledge.unlocked(player))
                && SpellKnowledge.cap(spell, SpellKnowledge.unlocked(player))
                    == spell.getMaxLevel(),
            "The complete functional tree reaches every personal spell and native rank ceiling: "
                + spell.getSpellId());
      }
    }
  }

  private static void verifyDiscoveryScroll(ServerPlayer player) {
    AbstractSpell eldritch = spell("sonic_boom");
    require(!eldritch.isLearned(player), "The scroll user has not read an Eldritch Manuscript");
    ItemStack scroll = scroll(eldritch, eldritch.getMaxLevel(), 2);
    player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
    float mana = magic(player).getMana();
    require(
        eldritch.attemptInitiateCast(
            scroll,
            eldritch.getMaxLevel(),
            player.level(),
            player,
            CastSource.SCROLL,
            false,
            "mainhand"),
        "The actual highest-rank Eldritch scroll works without permanent discovery");
    complete(player);
    require(
        scroll.getCount() == 1
            && !eldritch.isLearned(player)
            && rank(player, eldritch) == 0
            && magic(player).getMana() == mana,
        "One-use Eldritch casting pays one scroll and grants no Manuscript or permanent knowledge");
  }

  private static void verifyRecallScroll(ServerLevel level, Vec3 origin) {
    ServerLevel nether = level.getServer().getLevel(Level.NETHER);
    require(nether != null, "The Nether exists for an actual Recall dimension transition");
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "RecallScrollFixture", origin.add(24, 0, 0), null)) {
      ServerPlayer player = session.player;
      initialize(player);
      Utils.setPlayerSpellbookStack(player, ItemStack.EMPTY);
      player.teleportTo(nether, origin.x, origin.y, origin.z, 0, 0);
      AbstractSpell recall = SpellRegistry.RECALL_SPELL.get();
      ItemStack scroll = scroll(recall, 1, 2);
      player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
      float mana = magic(player).getMana();
      require(
          !SpellKnowledge.hasFocus(player)
              && rank(player, recall) == 0
              && MagicPractice.level(player) == 0,
          "Recall starts with no focus, permanent spell or mastery");
      require(
          scroll.use(nether, player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
          "The actual Recall scroll starts its native cast");
      complete(player);
      require(
          player.serverLevel() == level
              && scroll.getCount() == 1
              && magic(player).getMana() == mana,
          "Successful cross-dimension Recall consumes exactly one scroll after native state resets");
      complete(player);
      require(scroll.getCount() == 1, "Native post-charge cannot consume the Recall scroll twice");
    }
  }

  private static void verifyPortalScrollSource(ServerLevel level, Vec3 origin) {
    Vec3 position = origin.add(80, 0, 0);
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "PortalScroll", position, null)) {
      ServerPlayer player = session.player;
      initialize(player);
      Utils.setPlayerSpellbookStack(player, ItemStack.EMPTY);
      AbstractSpell portal = SpellRegistry.PORTAL_SPELL.get();
      ItemStack scroll = scroll(portal, 1, 3);
      player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
      aimDown(player, position);
      require(
          SpellKnowledge.canStart(portal, player, scroll, 1, CastSource.SCROLL, false),
          "Owned progression checks do not require Portal knowledge, mastery or focus for a scroll");
      float mana = magic(player).getMana();
      scroll.use(level, player, InteractionHand.MAIN_HAND);
      postTick(player);
      require(
          !SpellKnowledge.hasFocus(player)
              && PreparedAbilities.preparation(player).equals(Preparation.empty())
              && !magic(player).isCasting()
              && scroll.getCount() == 3
              && magic(player).getMana() == mana
              && !magic(player).getPlayerRecasts().hasRecastForSpell(portal)
              && TravelMagic.cooldownTicks(player, portal) == 0,
          "The native prohibition on recast spells from scrolls remains intact and consumes nothing");
    }
  }

  private static void verifyWings(ServerLevel level, Vec3 origin) {
    long previousTime = level.getGameTime();
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "WingsFixture", origin.add(16, 0, 0), null)) {
      ServerPlayer player = session.player;
      initialize(player);
      AbstractSpell wings = SpellRegistry.ANGEL_WINGS_SPELL.get();
      unlockMastery(player, wings, 5);
      unlock(player, node(wings));
      InscriptionTableMenu menu = table(player);
      studyAccepted(player, menu, wings, 5);
      player.containerMenu = player.inventoryMenu;
      player.getAttribute(AttributeRegistry.SPELL_POWER).setBaseValue(3);
      PreparedAbilities.edit(player, 0, wings.getSpellId());
      PreparedAbilities.select(player, 0);
      PreparedAbilities.cast(player);
      require(magic(player).isCasting(), "Actual prepared Wings enters native cast state");
      complete(player);
      MobEffectInstance effect = player.getEffect(MobEffectRegistry.ANGEL_WINGS);
      require(
          effect != null && effect.getDuration() == 1000,
          "Actual Angel Wings duration stays at 50 seconds after power bonuses");
      require(
          TravelMagic.cooldownTicks(player, wings) == 2400,
          "Actual Wings activation starts the 120-second personal deadline");
      float mana = magic(player).getMana();
      player.addEffect(new MobEffectInstance(MobEffectRegistry.ANGEL_WINGS, 6000));
      require(
          player.getEffect(MobEffectRegistry.ANGEL_WINGS).getDuration() <= 1000,
          "Effect refresh cannot extend Wings during its personal cooldown");
      magic(player).getPlayerCooldowns().clearCooldowns();
      PreparedAbilities.cast(player);
      require(
          !magic(player).isCasting() && magic(player).getMana() == mana,
          "Removing native cooldown does not bypass Wings' activation deadline");
      verifyTravelPersistence(session, wings, 2400);
    } finally {
      level.getServer().getWorldData().overworldData().setGameTime(previousTime);
    }
  }

  private static void verifyContinuousScroll(ServerPlayer player) {
    AbstractSpell breath = spell("fire_breath");
    int[] pulses = {0};
    Consumer<SpellOnCastEvent> listener =
        event -> {
          if (event.getEntity() == player && event.getSpellId().equals(breath.getSpellId()))
            pulses[0]++;
        };
    NeoForge.EVENT_BUS.addListener(listener);
    try {
      ItemStack scroll = scroll(breath, 1, 2);
      player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
      float mana = magic(player).getMana();
      require(
          scroll
              .use(player.level(), player, InteractionHand.MAIN_HAND)
              .getResult()
              .consumesAction(),
          "Actual continuous scroll use enters native casting");
      var manager = new MagicManager();
      manager.tick(player.level());
      require(
          pulses[0] == 1 && magic(player).isCasting() && scroll.getCount() == 2,
          "The first native continuous pulse retains its scroll until native channel completion");
      complete(player);
      require(
          scroll.getCount() == 1 && pulses[0] > 0 && magic(player).getMana() == mana,
          "Native continuous completion consumes exactly one stacked scroll without mana");
      int completedPulses = pulses[0];
      for (int tick = 0; tick < 20; tick++) manager.tick(player.level());
      require(
          pulses[0] == completedPulses && scroll.getCount() == 1 && !magic(player).isCasting(),
          "A completed continuous scroll use cannot execute further native pulses");
      ItemStack cancelled = scroll(breath, 1, 2);
      player.setItemInHand(InteractionHand.MAIN_HAND, cancelled);
      require(
          cancelled
              .use(player.level(), player, InteractionHand.MAIN_HAND)
              .getResult()
              .consumesAction(),
          "A second continuous scroll use starts before its held context is removed");
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      complete(player);
      require(
          cancelled.getCount() == 2
              && pulses[0] == completedPulses
              && magic(player).getMana() == mana,
          "Removed continuous-scroll context cannot execute a pulse or consume a scroll");
      ItemStack interrupted = scroll(breath, 1, 2);
      player.setItemInHand(InteractionHand.MAIN_HAND, interrupted);
      require(
          interrupted
              .use(player.level(), player, InteractionHand.MAIN_HAND)
              .getResult()
              .consumesAction(),
          "A channel starts before its first paid effect");
      manager.tick(player.level());
      require(
          pulses[0] == completedPulses + 1 && interrupted.getCount() == 2,
          "A successful native pulse precedes a held-item interruption");
      player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
      complete(player);
      require(
          interrupted.getCount() == 1
              && pulses[0] == completedPulses + 1
              && magic(player).getMana() == mana,
          "Interrupting an already effective channel consumes exactly one scroll and no extra pulse");
    } finally {
      NeoForge.EVENT_BUS.unregister(listener);
    }
  }

  private static void verifyPortals(ServerLevel level, Vec3 origin) {
    for (int rank = 1; rank <= 3; rank++) verifyPortalPair(level, origin, rank);
  }

  private static void verifyPortalPair(ServerLevel level, Vec3 origin, int rank) {
    long previousTime = level.getGameTime();
    Vec3 position = origin.add(32, 0, rank * 12);
    Set<UUID> portals = new HashSet<>();
    try (var cleanup = new PortalCleanup(level, previousTime, portals);
        var session =
            TestShipPlayerSession.create(
                level, UUID.randomUUID(), "PortalFixture" + rank, position, null)) {
      ServerPlayer player = session.player;
      initialize(player);
      PortalSpell portal = (PortalSpell) SpellRegistry.PORTAL_SPELL.get();
      unlockMastery(player, portal, 5);
      unlock(player, node(portal));
      if (rank > 1) {
        studyAccepted(player, table(player), portal, rank);
        player.containerMenu = player.inventoryMenu;
      }
      PreparedAbilities.edit(player, 0, portal.getSpellId());
      PreparedAbilities.select(player, 0);
      player.getAttribute(AttributeRegistry.SPELL_POWER).setBaseValue(3);
      require(
          portal.getPortalDuration(rank, player) == rank * 400,
          "Native Portal rank " + rank + " has its own duration ceiling after bonuses");
      aimDown(player, position);
      float mana = magic(player).getMana();
      PreparedAbilities.cast(player);
      require(magic(player).isCasting(), "First native Portal stage starts");
      complete(player);
      var recast = magic(player).getPlayerRecasts().getRecastInstance(portal.getSpellId());
      require(
          recast != null && recast.getCastData() instanceof PortalData,
          "First native Portal placement preserves its actual recast context");
      PortalData data = (PortalData) recast.getCastData();
      require(
          recast.getRemainingRecasts() == 1
              && recast.getTotalRecasts() == 2
              && magic(player).getPlayerRecasts().hasRecastForSpell(portal)
              && !magic(player).getPlayerCooldowns().isOnCooldown(portal),
          "First native Portal endpoint keeps one active pending endpoint and no native cooldown: "
              + recast.serializeNBT(player.registryAccess())
              + ", nativeCooldown="
              + PreparedAbilities.cooldownTicks(player, portal));
      cleanup.portals().add(data.portalEntityId1);
      PortalEntity first = portalEntity(level, data.portalEntityId1);
      require(
          !first.getPortalConnected()
              && data.globalPos2 == null
              && TravelMagic.cooldownTicks(player, portal) == 0,
          "A partial Portal does not start the successful-pair deadline");
      require(
          magic(player).getMana() < mana, "First actual Portal endpoint pays the native mana cost");
      float afterFirst = magic(player).getMana();
      aimDown(player, position.add(200, 0, 0));
      PreparedAbilities.cast(player);
      require(
          !magic(player).isCasting()
              && magic(player).getMana() == afterFirst
              && magic(player).getPlayerRecasts().getRecastInstance(portal.getSpellId()) == recast
              && data.globalPos2 == null
              && !first.isRemoved(),
          "An endpoint beyond 128 blocks preserves the partial native Portal and costs");
      PortalPos initial = data.globalPos1;
      data.globalPos1 = PortalPos.of(Level.NETHER, initial.pos(), 0);
      aimDown(player, position.add(20, 0, 0));
      PreparedAbilities.cast(player);
      require(
          !magic(player).isCasting()
              && data.globalPos2 == null
              && magic(player).getMana() == afterFirst,
          "A second endpoint in a different dimension is rejected intact");
      data.globalPos1 = initial;
      PortalManager.INSTANCE.removePortalData(data.portalEntityId1);
      PreparedAbilities.cast(player);
      require(
          !magic(player).isCasting()
              && data.globalPos2 == null
              && magic(player).getMana() == afterFirst
              && magic(player).getPlayerRecasts().getRecastInstance(portal.getSpellId()) == recast,
          "Missing first-endpoint registry data cannot consume or replace partial Portal context");
      PortalManager.INSTANCE.addPortalData(data.portalEntityId1, data);
      PreparedAbilities.cast(player);
      if (!magic(player).isCasting()) {
        var result = portal.canBeCastedBy(rank, CastSource.SPELLBOOK, magic(player), player);
        var target = PortalSpell.getTargetBlock(level, player, ClipContext.Fluid.NONE, 48);
        throw new IllegalStateException(
            "Valid second native Portal stage starts: nativeResult="
                + result.type
                + ", nativeMessage="
                + (result.message == null ? "" : result.message.getString())
                + ", knowledge="
                + SpellKnowledge.knowledgeReason(player, portal, SpellKnowledge.unlocked(player))
                + ", focus="
                + SpellKnowledge.hasFocus(player)
                + ", mana="
                + magic(player).getMana()
                + ", nativeCooldown="
                + magic(player).getPlayerCooldowns().isOnCooldown(portal)
                + ", travelAllowed="
                + TravelMagic.canCast(portal, player, magic(player), false)
                + ", firstExpired="
                + TravelMagic.portalExpired(first)
                + ", recast="
                + recast.serializeNBT(player.registryAccess())
                + ", player="
                + player.position()
                + ", look="
                + player.getLookAngle()
                + ", first="
                + data.globalPos1
                + ", target="
                + target.getType()
                + " "
                + target.getLocation());
      }
      complete(player);
      require(
          data.globalPos2 != null && data.portalEntityId2 != null,
          "Second native cast creates the actual Portal pair");
      cleanup.portals().add(data.portalEntityId2);
      PortalEntity second = portalEntity(level, data.portalEntityId2);
      require(
          first.getPortalConnected()
              && second.getPortalConnected()
              && data.globalPos1.dimension().equals(data.globalPos2.dimension())
              && data.globalPos1.pos().distanceToSqr(data.globalPos2.pos()) <= 128 * 128,
          "Actual native pair remains local and within 128 blocks");
      require(
          data.ticksToLive == rank * 400
              && first.getPersistentData().getLong(PORTAL_EXPIRES) == previousTime + rank * 400
              && second.getPersistentData().getLong(PORTAL_EXPIRES) == previousTime + rank * 400,
          "Actual rank " + rank + " Portal pair keeps its 20/40/60-second absolute lifetime");
      require(
          TravelMagic.cooldownTicks(player, portal) == 3600
              && !magic(player).getPlayerRecasts().hasRecastForSpell(portal),
          "Successful native pair starts 180 seconds and finishes its single recast");
      require(
          magic(player).getMana() == afterFirst,
          "Second endpoint preserves the provider's already-paid native recast cost");
      require(
          PortalManager.INSTANCE.canUsePortal(first.getUUID(), player),
          "A valid actual Portal pair remains traversable through the native manager");
      PortalPos secondPosition = data.globalPos2;
      data.globalPos2 = PortalPos.of(Level.NETHER, secondPosition.pos(), 0);
      require(
          !PortalManager.INSTANCE.canUsePortal(first.getUUID(), player),
          "The native manager rejects a persisted cross-dimension pair");
      data.globalPos2 = PortalPos.of(level.dimension(), initial.pos().add(129, 0, 0), 0);
      require(
          !PortalManager.INSTANCE.canUsePortal(first.getUUID(), player),
          "The native manager rejects a persisted over-distance pair");
      data.globalPos2 = secondPosition;
      magic(player).getPlayerCooldowns().clearCooldowns();
      PreparedAbilities.cast(player);
      require(
          !magic(player).isCasting() && magic(player).getMana() == afterFirst,
          "Native cooldown removal cannot create a new pair before the personal deadline");
      if (rank == 3) verifyTravelPersistence(session, portal, 3600);
      level.getServer().getWorldData().overworldData().setGameTime(previousTime + rank * 400);
      require(
          !PortalManager.INSTANCE.canUsePortal(first.getUUID(), player),
          "Portal traversal stops at the absolute lifetime before entity ticks resume");
      first.tick();
      second.tick();
      require(
          first.isRemoved() && second.isRemoved(),
          "Expired actual Portal entities disappear on their next native tick");
      level.getServer().getWorldData().overworldData().setGameTime(previousTime + 3599);
      require(
          TravelMagic.cooldownTicks(player, portal) == 1,
          "Pair creation remains blocked for the full 180 seconds");
      level.getServer().getWorldData().overworldData().setGameTime(previousTime + 3600);
      require(
          TravelMagic.cooldownTicks(player, portal) == 0,
          "Portal becomes ready at its success deadline");
    }
    verifyPortalFrame(level, position.add(0, 0, 5));
  }

  private record PortalCleanup(ServerLevel level, long previousTime, Set<UUID> portals)
      implements AutoCloseable {
    @Override
    public void close() {
      try {
        for (UUID id : portals) {
          Entity portal = level.getEntity(id);
          if (portal != null && !portal.isRemoved()) portal.discard();
          PortalManager.INSTANCE.removePortalData(id);
        }
      } finally {
        level.getServer().getWorldData().overworldData().setGameTime(previousTime);
      }
    }
  }

  private static void verifyTravelPersistence(
      TestShipPlayerSession originalSession, AbstractSpell spell, int initialCooldown) {
    ServerPlayer original = originalSession.player;
    ServerLevel level = original.serverLevel();
    long previousTime = level.getGameTime();
    Vec3 position = original.position();
    Preparation prepared = PreparedAbilities.preparation(original);
    UUID identity = original.getUUID();
    String name = original.getGameProfile().getName();
    try {
      level.getServer().getWorldData().overworldData().setGameTime(previousTime + 100);
      int remaining = initialCooldown - 100;
      require(
          TravelMagic.cooldownTicks(original, spell) == remaining,
          "Travel deadline advances independently of native cooldown ticks: " + spell.getSpellId());
      ServerLevel nether = level.getServer().getLevel(Level.NETHER);
      require(nether != null, "The native Nether exists for dimension-transition regression");
      original.teleportTo(nether, position.x, position.y, position.z, 0, 80);
      require(
          original.serverLevel() == nether
              && TravelMagic.cooldownTicks(original, spell) == remaining,
          "Actual native dimension transfer retains the personal travel deadline: "
              + spell.getSpellId());
      original.teleportTo(level, position.x, position.y, position.z, 0, 80);
      require(
          original.serverLevel() == level
              && TravelMagic.cooldownTicks(original, spell) == remaining,
          "Returning through native dimension transfer retains the travel deadline: "
              + spell.getSpellId());
      verifyDeathClone(
          original,
          replacement -> {
            require(
                TravelMagic.cooldownTicks(replacement, spell) == remaining
                    && PreparedAbilities.preparation(replacement).equals(prepared),
                "Native death clone retains the personal travel deadline: " + spell.getSpellId());
          });
      NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(original));
      CompoundTag saved = original.saveWithoutId(new CompoundTag());
      originalSession.close();
      try (var restoredSession =
          TestShipPlayerSession.create(level, identity, name, position, saved)) {
        ServerPlayer restored = restoredSession.player;
        initialize(restored);
        require(
            TravelMagic.cooldownTicks(restored, spell) == remaining
                && PreparedAbilities.preparation(restored).equals(prepared),
            "Native save/load and login preserve the elapsed travel deadline: "
                + spell.getSpellId());
        if (spell == SpellRegistry.ANGEL_WINGS_SPELL.get()) {
          MobEffectInstance effect = restored.getEffect(MobEffectRegistry.ANGEL_WINGS);
          require(
              effect != null && effect.getDuration() <= 1000,
              "Native save/load retains the actual capped Wings effect");
          level.getServer().getWorldData().overworldData().setGameTime(previousTime + 1000);
          NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(restored));
          require(
              !restored.hasEffect(MobEffectRegistry.ANGEL_WINGS),
              "Absolute Wings deadline removes the reloaded actual effect at 50 seconds");
        }
        level
            .getServer()
            .getWorldData()
            .overworldData()
            .setGameTime(previousTime + initialCooldown - 1);
        require(
            TravelMagic.cooldownTicks(restored, spell) == 1,
            "Reloaded travel ability remains blocked until its full activation deadline: "
                + spell.getSpellId());
        level
            .getServer()
            .getWorldData()
            .overworldData()
            .setGameTime(previousTime + initialCooldown);
        require(
            TravelMagic.cooldownTicks(restored, spell) == 0,
            "Reloaded travel ability becomes ready at its original deadline: "
                + spell.getSpellId());
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(restored));
      }
    } finally {
      level.getServer().getWorldData().overworldData().setGameTime(previousTime);
    }
  }

  private static void verifyPortalFrame(ServerLevel level, Vec3 position) {
    var block =
        BuiltInRegistries.BLOCK.get(ResourceLocation.parse("irons_spellbooks:portal_frame"));
    require(block != Blocks.AIR, "Pinned native Portal frame block is registered");
    BlockPos target = BlockPos.containing(position).offset(0, 1, 4);
    var previous = level.getBlockState(target);
    var previousAbove = level.getBlockState(target.above());
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "PortalFrameFixture", position, null)) {
      ServerPlayer player = session.player;
      initialize(player);
      AbstractSpell portal = SpellRegistry.PORTAL_SPELL.get();
      unlockMastery(player, portal, 5);
      unlock(player, node(portal));
      var lower = block.defaultBlockState().setValue(PortalFrameBlock.HALF, DoubleBlockHalf.LOWER);
      level.setBlockAndUpdate(target, lower);
      block.setPlacedBy(level, target, lower, player, new ItemStack(block));
      require(
          level.getBlockEntity(target)
              instanceof io.redspace.ironsspellbooks.block.portal_frame.PortalFrameBlockEntity,
          "Actual native frame block entity exists");
      player.absMoveTo(position.x, position.y, position.z, 0, 0);
      player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
      require(
          PortalSpell.getTargetBlock(level, player, ClipContext.Fluid.NONE, 48)
              .getBlockPos()
              .equals(target),
          "Fixture aims at the actual native Portal frame");
      PreparedAbilities.edit(player, 0, portal.getSpellId());
      PreparedAbilities.select(player, 0);
      float mana = magic(player).getMana();
      PreparedAbilities.cast(player);
      require(
          !magic(player).isCasting()
              && magic(player).getMana() == mana
              && !magic(player).getPlayerRecasts().hasRecastForSpell(portal),
          "A native Portal frame cannot open a persistent-frame route or charge mana");
    } finally {
      level.setBlockAndUpdate(target, previous);
      level.setBlockAndUpdate(target.above(), previousAbove);
    }
  }

  private static void verifyPocketDimension(ServerLevel level, Vec3 origin) {
    long previousTime = level.getGameTime();
    UUID identity = UUID.randomUUID();
    UUID exitId = null;
    Vec3 entry = origin.add(64, 0, 0);
    try (var session =
        TestShipPlayerSession.create(level, identity, "PocketFixture", entry, null)) {
      ServerPlayer player = session.player;
      initialize(player);
      AbstractSpell pocket = SpellRegistry.POCKET_DIMENSION_SPELL.get();
      unlockMastery(player, pocket, 5);
      unlock(player, node(pocket));
      require(!pocket.isLearned(player), "The Pocket Dimension talent does not grant discovery");
      PreparedAbilities.edit(player, 0, pocket.getSpellId());
      require(
          !PreparedAbilities.preparation(player).slots().contains(pocket.getSpellId()),
          "Pocket Dimension preparation retains its native discovery requirement");
      magic(player).getSyncedData().learnSpell(pocket, false);
      PreparedAbilities.edit(player, 0, pocket.getSpellId());
      PreparedAbilities.select(player, 0);
      float mana = magic(player).getMana();
      PreparedAbilities.cast(player);
      require(magic(player).isCasting(), "Prepared Pocket Dimension enters native casting");
      complete(player);
      ServerLevel roomLevel = level.getServer().getLevel(PocketDimensionManager.POCKET_DIMENSION);
      require(
          roomLevel != null && player.serverLevel() == roomLevel,
          "Actual Pocket Dimension completion moves the owner into the native dimension");
      require(
          magic(player).getMana() == mana - pocket.getManaCost(1),
          "Native Pocket Dimension casting pays its actual mana cost");
      BlockPos room = PocketDimensionManager.INSTANCE.structurePosForPlayer(player);
      require(
          !roomLevel.getBlockState(room).isAir(),
          "The actual spell generates the owner's native personal room");
      BlockPos exit = PocketDimensionManager.INSTANCE.findPortalForStructure(roomLevel, room);
      require(
          roomLevel.getBlockState(exit).is(BlockRegistry.POCKET_PORTAL_FRAME),
          "The generated personal room contains its native return frame");
      require(
          roomLevel.getBlockEntity(exit) instanceof PortalFrameBlockEntity,
          "The native personal return frame has its actual block entity");
      var frame = (PortalFrameBlockEntity) roomLevel.getBlockEntity(exit);
      exitId = frame.getUUID();
      PortalData data = PortalManager.INSTANCE.getPortalData(exitId);
      require(
          data != null
              && identity.equals(data.portalEntityId1)
              && exitId.equals(data.portalEntityId2)
              && data.globalPos1.dimension().equals(level.dimension())
              && data.globalPos1.pos().distanceToSqr(entry) < 0.0001
              && data.globalPos2.dimension().equals(roomLevel.dimension()),
          "The actual spell records its personal return route to the real entry position");
      require(
          !PortalManager.INSTANCE.canUsePortal(exitId, player),
          "Native arrival cooldown initially prevents immediately re-entering the return frame");
      // Native personal exits outlive the temporary Portal spell's lifetime and data duration.
      for (int tick = 1; tick <= 10; tick++) {
        level.getServer().getWorldData().overworldData().setGameTime(previousTime + 1300 + tick);
        PortalFrameBlockEntity.serverTick(roomLevel, exit, roomLevel.getBlockState(exit), frame);
      }
      PreparedAbilities.edit(player, 0, "");
      Utils.setPlayerSpellbookStack(player, ItemStack.EMPTY);
      magic(player).setMana(0);
      postTick(player);
      require(
          !SpellKnowledge.hasFocus(player)
              && PreparedAbilities.preparation(player).slots().getFirst().isEmpty()
              && magic(player).getMana() == 0,
          "The personal-room owner can lose preparation, focus and mana");
      require(
          PortalManager.INSTANCE.canUsePortal(exitId, player),
          "The actual personal return frame remains usable without preparation, focus or mana after"
              + " native arrival cooldown and 60 seconds");
      try (var guestSession =
          TestShipPlayerSession.create(
              roomLevel,
              UUID.randomUUID(),
              "PocketGuest",
              frame.getPortalLocation().add(1, 0, 0),
              null)) {
        ServerPlayer guest = guestSession.player;
        initialize(guest);
        CompoundTag owners = PocketDimensionManager.INSTANCE.serializeNBT(level.registryAccess());
        Vec3 guestPosition = guest.position();
        require(
            !PortalManager.INSTANCE.canUsePortal(exitId, guest),
            "Another player cannot claim the native personal return route");
        frame.teleport(guest);
        require(
            guest.serverLevel() == roomLevel
                && guest.position().equals(guestPosition)
                && PocketDimensionManager.INSTANCE
                    .serializeNBT(level.registryAccess())
                    .equals(owners),
            "Native frame traversal rejects the wrong player without allocating personal-room"
                + " ownership");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(guest));
      }
      frame.teleport(player);
      require(
          player.serverLevel() == level
              && player.position().distanceToSqr(entry) < 0.0001
              && magic(player).getMana() == 0,
          "Actual native personal-frame traversal returns its owner to the original dimension and"
              + " position");
      verifyLegacyFrameLinks(level, player, entry.add(12, 0, 0));
      NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
    } finally {
      if (exitId != null) PortalManager.INSTANCE.removePortalData(exitId);
      PortalManager.INSTANCE.removePortalData(identity);
      PocketDimensionManager.INSTANCE.remove(identity);
      level.getServer().getWorldData().overworldData().setGameTime(previousTime);
    }
  }

  private static void verifyLegacyFrameLinks(
      ServerLevel level, ServerPlayer player, Vec3 position) {
    BlockPos first = BlockPos.containing(position);
    BlockPos second = first.east(4);
    List<BlockPos> positions = List.of(first, first.above(), second, second.above());
    var previous = positions.stream().map(level::getBlockState).toList();
    UUID firstId = null;
    UUID secondId = null;
    try {
      var block = BlockRegistry.PORTAL_FRAME.get();
      var lower = block.defaultBlockState().setValue(PortalFrameBlock.HALF, DoubleBlockHalf.LOWER);
      for (BlockPos bottom : List.of(first, second)) {
        level.setBlockAndUpdate(bottom, lower);
        block.setPlacedBy(level, bottom, lower, player, new ItemStack(block));
      }
      require(
          level.getBlockEntity(first) instanceof PortalFrameBlockEntity
              && level.getBlockEntity(second) instanceof PortalFrameBlockEntity,
          "Controlled legacy-save input has two actual standard native frame blocks");
      var firstFrame = (PortalFrameBlockEntity) level.getBlockEntity(first);
      var secondFrame = (PortalFrameBlockEntity) level.getBlockEntity(second);
      firstId = firstFrame.getUUID();
      secondId = secondFrame.getUUID();
      // This is the provider's public saved-data shape for a pre-existing permanent frame link.
      var legacy = new PortalData();
      legacy.isBlock = true;
      legacy.firstPortal(
          firstId, PortalPos.of(level.dimension(), firstFrame.getPortalLocation(), 0));
      legacy.secondPortal(
          secondId, PortalPos.of(level.dimension(), secondFrame.getPortalLocation(), 0));
      PortalManager.INSTANCE.addPortalData(firstId, legacy);
      PortalManager.INSTANCE.addPortalData(secondId, legacy);
      require(
          !PortalManager.INSTANCE.canUsePortal(firstId, player)
              && !PortalManager.INSTANCE.canUsePortal(secondId, player),
          "The personal-room exception cannot reopen ordinary permanent frame links from old"
              + " saves");
      Vec3 before = player.position();
      firstFrame.teleport(player);
      require(
          player.serverLevel() == level && player.position().equals(before),
          "Actual standard-frame traversal remains blocked for a controlled legacy link");
    } finally {
      if (firstId != null) PortalManager.INSTANCE.removePortalData(firstId);
      if (secondId != null) PortalManager.INSTANCE.removePortalData(secondId);
      for (int index = 0; index < positions.size(); index++)
        level.setBlockAndUpdate(positions.get(index), previous.get(index));
    }
  }

  private static void aimDown(ServerPlayer player, Vec3 position) {
    player.absMoveTo(position.x, position.y, position.z, 0, 80);
  }

  private static PortalEntity portalEntity(ServerLevel level, UUID id) {
    Entity entity = level.getEntity(id);
    require(entity instanceof PortalEntity, "Native Portal entity was registered: " + id);
    return (PortalEntity) entity;
  }

  static void initialize(ServerPlayer player) {
    magic(player).setServerPlayer(player);
    magic(player).getSyncedData();
    player.getAttribute(AttributeRegistry.MAX_MANA).setBaseValue(5000);
    player.getAttribute(AttributeRegistry.MANA_REGEN).setBaseValue(0);
    magic(player).setMana(1000);
    focus(player);
    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
  }

  private static ItemStack focus(ServerPlayer player) {
    ItemStack focus = Utils.getPlayerSpellbookStack(player);
    if (focus == null || focus.isEmpty()) {
      focus = new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get());
      Utils.setPlayerSpellbookStack(player, focus);
    }
    require(SpellKnowledge.hasFocus(player), "Native Curios spellbook slot accepts the focus");
    return focus;
  }

  private static InscriptionTableMenu table(ServerPlayer player) {
    var menu = new InscriptionTableMenu(37, player.getInventory(), ContainerLevelAccess.NULL);
    player.containerMenu = menu;
    require(
        menu.getSpellBookSlot().getItem() == Utils.getPlayerSpellbookStack(player),
        "Native inscription menu reads the equipped Curios focus");
    return menu;
  }

  static ItemStack scroll(AbstractSpell spell, int rank, int count) {
    var stack = new ItemStack(ItemRegistry.SCROLL.get(), count);
    ISpellContainer.createScrollContainer(spell, rank, stack);
    return stack;
  }

  private static void studyAccepted(
      ServerPlayer player, InscriptionTableMenu menu, AbstractSpell spell, int rank) {
    ItemStack scroll = scroll(spell, rank, 3);
    menu.getScrollSlot().set(scroll);
    var components = scroll.copy();
    require(
        menu.clickMenuButton(player, -1),
        "Real study button accepts improved rank: " + spell.getSpellId());
    ItemStack remainder = menu.getScrollSlot().getItem();
    require(
        remainder.getCount() == 2
            && ItemStack.isSameItemSameComponents(remainder, components)
            && rank(player, spell) == rank,
        "Study consumes exactly one actual scroll and preserves its component-identical remainder");
  }

  private static void studyRejected(
      ServerPlayer player,
      InscriptionTableMenu menu,
      AbstractSpell spell,
      int rank,
      String message) {
    ItemStack scroll = scroll(spell, rank, 3);
    menu.getScrollSlot().set(scroll);
    ItemStack before = scroll.copy();
    int known = rank(player, spell);
    require(!menu.clickMenuButton(player, -1), message + ": button result");
    require(
        ItemStack.matches(menu.getScrollSlot().getItem(), before) && rank(player, spell) == known,
        message + ": rank and actual stack");
  }

  static void unlockMastery(ServerPlayer player, AbstractSpell spell, int depth) {
    long target = MagicPracticeProgress.minimumXp(45);
    MagicPractice.award(player, Math.max(0, target - MagicPractice.credit(player)));
    unlock(player, "magic_mastery_1");
    String direction = MagicTalents.spell(spell.getSpellId()).direction();
    for (int tier = 2; tier <= depth; tier++)
      unlock(player, "magic_" + direction + "_mastery_" + tier);
  }

  static void unlock(ServerPlayer player, String node) {
    SkillsMod.getInstance().unlockSkill(player, COMBAT, node);
    require(
        SkillsMod.getInstance().getSkillState(player, COMBAT, node).orElseThrow()
            == Skill.State.UNLOCKED,
        "Native talent unlock was applied: " + node);
  }

  private static int rank(ServerPlayer player, AbstractSpell spell) {
    return SpellKnowledge.rank(player, spell, SpellKnowledge.unlocked(player));
  }

  private static String node(AbstractSpell spell) {
    return MagicTalents.spell(spell.getSpellId()).node();
  }

  private static AbstractSpell spell(String path) {
    AbstractSpell spell =
        SpellRegistry.getSpell(ResourceLocation.parse("irons_spellbooks:" + path));
    require(
        spell != SpellRegistry.none() && spell.isEnabled(), "Enabled native spell exists: " + path);
    return spell;
  }

  private static MagicData magic(ServerPlayer player) {
    return MagicData.getPlayerMagicData(player);
  }

  private static CompoundTag magicState(ServerPlayer player) {
    CompoundTag state = new CompoundTag();
    magic(player).saveNBTData(state, player.registryAccess());
    state.putInt("duration", magic(player).getCastDuration());
    state.putInt("remaining", magic(player).getCastDurationRemaining());
    state.putString("source", magic(player).getCastSource().name());
    return state;
  }

  static void complete(ServerPlayer player) {
    var manager = new MagicManager();
    for (int tick = 0; tick < 400 && magic(player).isCasting(); tick++)
      manager.tick(player.level());
    require(!magic(player).isCasting(), "Native cast finishes within the bounded fixture ticks");
  }

  private static void postTick(ServerPlayer player) {
    player.tickCount = 20;
    NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
