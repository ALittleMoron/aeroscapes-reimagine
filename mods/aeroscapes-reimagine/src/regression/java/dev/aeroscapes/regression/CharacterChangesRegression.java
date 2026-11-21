package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.magic.InnateAbility;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.RacialAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.CharacterChanges;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.ProfessionProgression;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

final class CharacterChangesRegression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final ResourceLocation MAGIC =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
  private static final ResourceLocation PROFESSION =
      ResourceLocation.parse(ProgressionBridge.PROFESSION_CATEGORY);

  private CharacterChangesRegression() {}

  static void verify(ServerLevel level) {
    var spot = level.getSharedSpawnPos().atY(level.getMaxBuildHeight() - 16);
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "IdentityChange", Vec3.atBottomCenterOf(spot), null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        require(
            RaceIdentity.choose(
                player,
                Race.GOBLIN,
                1,
                CharacterClass.MAGE,
                "first_mastery",
                Profession.BLACKSMITH,
                "material_repair"),
            "Create character");
        // Copied 0.6.5 identities have no marker distinguishing a previously purchased starter.
        player
            .getPersistentData()
            .getCompound("aeroscapes_reimagine_racial_traits")
            .remove("class_starter_personally_learned");
        verifyClasses(player);
        verifyProfessions(player);
        verifyRaces(player);
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    verifyBorrowedMastery(level, Vec3.atBottomCenterOf(spot));
    LogUtils.getLogger().info("Character change regression passed");
  }

  private static void verifyBorrowedMastery(ServerLevel level, Vec3 position) {
    var uuid = UUID.randomUUID();
    var skills = SkillsMod.getInstance();
    CompoundTag playerSave;
    var talents = new CompoundTag();
    var mage = new CharacterChanges.Selection("class", "mage", 0, "first_mastery", false);
    try (var session =
        TestShipPlayerSession.create(level, uuid, "BorrowedMastery", position, null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        APIUtils.setLevel("combat", player, 5);
        ProgressionBridge.sync(player);
        skills.tryUnlockSkill(player, COMBAT, "quick_block", false);
        require(
            RaceIdentity.choose(
                player,
                Race.HUMAN,
                0,
                CharacterClass.WARRIOR,
                "quick_block",
                Profession.BLACKSMITH,
                "material_repair"),
            "Choose a personally learned starter");
        skills.tryUnlockSkill(player, COMBAT, "shield_march", false);
        change(player, mage);
        skills.tryUnlockSkill(player, COMBAT, "melee_strike", false);
        require(
            skills.getPointsLeft(player, COMBAT).orElseThrow() == -1
                && skills.getPointsLeft(player, MAGIC).orElseThrow() == 1
                && !skills.getUnlockedSkills(player, COMBAT).orElseThrow().contains("melee_strike")
                && !SpellKnowledge.hasFocus(player),
            "The transferred starter funds mastery while combat debt blocks melee purchases");
        skills.exportPlayerData(player, talents);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        playerSave = player.saveWithoutId(new CompoundTag());
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    try (var session =
        TestShipPlayerSession.create(level, uuid, "BorrowedMastery", position, playerSave)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        skills.importPlayerData(player, talents);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        require(
            skills.getPointsLeft(player, COMBAT).orElseThrow() == -1
                && skills.getPointsLeft(player, MAGIC).orElseThrow() == 1
                && SpellKnowledge.unlocked(player).equals(Set.of("magic_mastery_1"))
                && Set.copyOf(skills.getUnlockedSkills(player, COMBAT).orElseThrow())
                    .equals(Set.of("quick_block", "shield_march"))
                && !SpellKnowledge.hasFocus(player),
            "Reload both identity and the independent balances");
        var rogue = new CharacterChanges.Selection("class", "rogue", 0, "quick_recovery", false);
        var advance = CharacterChanges.preview(player, rogue);
        require(
            advance != null && advance.advance() && advance.spent() == 1,
            "An occupied combat pool can advance its next point for the new starter");
        change(player, rogue);
        skills.tryUnlockSkill(player, COMBAT, "melee_strike", false);
        require(
            skills.getPointsLeft(player, COMBAT).orElseThrow() == -1
                && !skills.getUnlockedSkills(player, COMBAT).orElseThrow().contains("melee_strike")
                && !SpellKnowledge.unlocked(player).contains("magic_mastery_1"),
            "The advance preserves paid combat talents and releases the borrowed mastery");
        change(player, new CharacterChanges.Selection("class", "warrior", 0, "quick_block", false));
        require(
            skills.getPointsLeft(player, COMBAT).orElseThrow() == 0
                && skills.getPointsLeft(player, MAGIC).orElseThrow() == 1
                && SpellKnowledge.unlocked(player).isEmpty()
                && Set.copyOf(skills.getUnlockedSkills(player, COMBAT).orElseThrow())
                    .equals(Set.of("quick_block", "shield_march")),
            "Returning to the previously learned starter releases the combat advance");
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void verifyClasses(ServerPlayer player) {
    var skills = SkillsMod.getInstance();
    var spell = SpellRegistry.FIREBOLT_SPELL.get();
    var magic = MagicData.getPlayerMagicData(player);
    magic.setServerPlayer(player);
    PreparedAbilities.edit(player, 4, spell.getSpellId());
    PreparedAbilities.select(player, 4);
    var prepared = PreparedAbilities.preparation(player);
    var ranks = new CompoundTag();
    ranks.putInt(spell.getSpellId(), 3);
    player
        .getPersistentData()
        .getCompound("aeroscapes_reimagine_abilities")
        .put("ranks", ranks.copy());
    magic.getPlayerCooldowns().addCooldown(spell, 400);
    Utils.setPlayerSpellbookStack(player, ItemStack.EMPTY);
    skills.tryUnlockSkill(player, MAGIC, "magic_recovery_1", false);
    var warrior = new CharacterChanges.Selection("class", "warrior", 0, "quick_block", false);
    var known = SpellKnowledge.unlocked(player);
    var advance = CharacterChanges.preview(player, warrior);
    require(
        advance != null && !advance.advance() && advance.spent() == 1,
        "Moving the class starter funds the first combat node");
    change(player, warrior);
    skills.tryUnlockSkill(player, COMBAT, "melee_strike", false);
    require(
        skills.getPointsLeft(player, COMBAT).orElseThrow() == 0
            && skills.getPointsLeft(player, MAGIC).orElseThrow() == -1
            && !skills.getUnlockedSkills(player, COMBAT).orElseThrow().contains("melee_strike"),
        "Leaving Mage keeps paid magic nodes and moves the granted credit to combat");
    var rogue = new CharacterChanges.Selection("class", "rogue", 0, "quick_recovery", false);
    change(player, rogue);
    require(
        skills.getPointsLeft(player, COMBAT).orElseThrow() == 0
            && skills.getPointsLeft(player, MAGIC).orElseThrow() == -1
            && !skills.getUnlockedSkills(player, COMBAT).orElseThrow().contains("quick_block"),
        "Switching between combat classes only replaces the granted combat node");
    var mage = new CharacterChanges.Selection("class", "mage", 0, "first_mastery", false);
    change(player, mage);
    require(
        SpellKnowledge.unlocked(player).equals(known)
            && skills.getPointsLeft(player, COMBAT).orElseThrow() == 0
            && skills.getPointsLeft(player, MAGIC).orElseThrow() == 0,
        "Returning to Mage restores its class-funded point");
    change(player, warrior);
    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
    require(skills.getPointsLeft(player, MAGIC).orElseThrow() == -1, "Magic debt survives login");
    APIUtils.setLevel("combat", player, 5);
    ProgressionBridge.sync(player);
    require(
        known.contains("magic_recovery_1")
            && skills.getPointsLeft(player, COMBAT).orElseThrow() == 1
            && skills.getPointsLeft(player, MAGIC).orElseThrow() == -1,
        "Combat practice cannot pay off retained magic");
    MagicPractice.award(player, 100);
    ProgressionBridge.sync(player);
    require(
        skills.getPointsLeft(player, MAGIC).orElseThrow() == 0
            && skills.getPointsLeft(player, COMBAT).orElseThrow() == 1,
        "New magic practice settles only its own debt");
    require(
        SpellKnowledge.unlocked(player).containsAll(known)
            && RaceIdentity.profession(player) == Profession.BLACKSMITH
            && RaceIdentity.style(player) == 1,
        "Default change preserves learned magic, profession and appearance");
    skills.tryUnlockSkill(player, COMBAT, "melee_strike", false);
    change(player, mage);
    require(
        skills.getPointsLeft(player, COMBAT).orElseThrow() == 0
            && skills.getPointsLeft(player, MAGIC).orElseThrow() == 1,
        "Purchased melee survives the change and available points stay with their own pool");
    var refund = new CharacterChanges.Selection("class", "warrior", 0, "quick_block", true);
    var quote = CharacterChanges.preview(player, refund);
    require(
        quote != null
            && quote.returned() == 2
            && quote.spent() == 1
            && quote.removed().containsAll(List.of("magic_mastery_1", "magic_recovery_1")),
        "Full old mage branch preview");
    require(
        CharacterChanges.change(player, refund, quote)
            && !CharacterChanges.change(player, refund, quote),
        "One confirmed change");
    require(
        SpellKnowledge.unlocked(player).isEmpty()
            && Set.copyOf(skills.getUnlockedSkills(player, COMBAT).orElseThrow())
                .equals(Set.of("quick_block", "melee_strike"))
            && skills.getPointsLeft(player, COMBAT).orElseThrow() == 0
            && skills.getPointsLeft(player, MAGIC).orElseThrow() == 2
            && PreparedAbilities.preparation(player).equals(prepared)
            && player
                .getPersistentData()
                .getCompound("aeroscapes_reimagine_abilities")
                .getCompound("ranks")
                .equals(ranks)
            && PreparedAbilities.cooldownTicks(player, spell) == 400
            && !SpellKnowledge.knowledgeReason(player, spell, SpellKnowledge.unlocked(player))
                .isEmpty(),
        "Refund without XP, prepared slot, rank, cooldown or point duplication");
    change(player, mage);
    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
    require(
        !SpellKnowledge.hasFocus(player)
            && player.getInventory().countItem(ItemRegistry.COPPER_SPELL_BOOK.get()) == 0
            && APIUtils.getLevel("combat", player) == 5
            && skills.getPointsLeft(player, COMBAT).orElseThrow() == 0
            && skills.getPointsLeft(player, MAGIC).orElseThrow() == 2,
        "Cycling classes and reconnecting do not reissue points, XP or starter book");
  }

  private static void verifyProfessions(ServerPlayer player) {
    var builder = new CharacterChanges.Selection("profession", "builder", 0, "long_reach", false);
    var initial = CharacterChanges.preview(player, builder);
    require(
        initial != null && initial.returned() == 1 && initial.spent() == 1,
        "Fund new profession ability");
    APIUtils.setLevel("mining", player, 10);
    ProgressionBridge.sync(player);
    var skills = SkillsMod.getInstance();
    skills.tryUnlockSkill(player, PROFESSION, "mining_efficiency", false);
    skills.tryUnlockSkill(player, PROFESSION, "frugal_repair", false);
    require(
        !CharacterChanges.change(player, builder, initial)
            && RaceIdentity.profession(player) == Profession.BLACKSMITH,
        "Changing the old allocation invalidates its preview before mutation");
    var quote = CharacterChanges.preview(player, builder);
    require(
        quote != null
            && quote.removed().equals(List.of("frugal_repair", "material_repair"))
            && quote.returned() == 2,
        "Both old profession nodes, excluding the common mining tree");
    change(player, builder);
    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
    require(
        ProfessionProgression.learned(player, "mining_efficiency")
            && ProfessionProgression.learned(player, "long_reach")
            && !ProfessionProgression.learned(player, "frugal_repair")
            && !ProfessionProgression.learned(player, "material_repair")
            && skills.getPointsLeft(player, PROFESSION).orElseThrow() == 1
            && player.getInventory().countItem(Items.STONE_AXE) == 0
            && APIUtils.getLevel("mining", player) == 10
            && RaceIdentity.characterClass(player) == CharacterClass.MAGE
            && RaceIdentity.style(player) == 1,
        "Preserve mining, old appearance, class and kit delivery boundary");
  }

  private static void verifyRaces(ServerPlayer player) {
    var elf = new CharacterChanges.Selection("race", "elf", 2, "", false);
    player.setHealth(4);
    player.setPose(Pose.CROUCHING);
    player.refreshDimensions();
    var quote = CharacterChanges.preview(player, elf);
    require(quote != null, "Clear space for a larger race");
    var roof = player.blockPosition().above();
    var level = player.serverLevel();
    var prior = level.getBlockState(roof);
    level.setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());
    try {
      require(
          CharacterChanges.problem(player, elf).equals("space")
              && !CharacterChanges.change(player, elf, quote)
              && RaceIdentity.race(player) == Race.GOBLIN
              && RaceIdentity.style(player) == 1,
          "Recheck clearance on confirmation while in a low goblin passage");
    } finally {
      level.setBlockAndUpdate(roof, prior);
    }
    change(player, elf);
    require(
        RaceIdentity.race(player) == Race.ELF
            && RaceIdentity.style(player) == 2
            && RaceIdentity.characterClass(player) == CharacterClass.MAGE
            && RaceIdentity.profession(player) == Profession.BUILDER
            && player.getHealth() <= 4
            && RaceIdentity.starter(player).equals("first_mastery"),
        "Only race and visible style changed");
    require(
        CharacterChanges.preview(
                player, new CharacterChanges.Selection("race", "elf", 0, "", false))
            == null,
        "A fixed appearance cannot be changed without an actual race change");
    PreparedAbilities.edit(player, 6, InnateAbility.LIGHT.id);
    PreparedAbilities.select(player, 6);
    PreparedAbilities.cast(player);
    require(
        player.hasEffect(RacialAbilities.ELF_LIGHT),
        "Elf can use the newly prepared racial ability");
    change(player, new CharacterChanges.Selection("race", "human", 0, "", false));
    PreparedAbilities.sync(player, true);
    PreparedAbilities.select(player, 4);
    PreparedAbilities.select(player, 6);
    PreparedAbilities.cast(player);
    require(
        !player.hasEffect(RacialAbilities.ELF_LIGHT)
            && PreparedAbilities.preparation(player).slots().get(6).equals(InnateAbility.LIGHT.id),
        "Former racial ability keeps its slot but has no effect");
    change(player, elf);
    PreparedAbilities.cast(player);
    require(
        player.hasEffect(RacialAbilities.ELF_LIGHT),
        "Restored race can use the retained assigned ability");
  }

  private static void change(ServerPlayer player, CharacterChanges.Selection selection) {
    var preview = CharacterChanges.preview(player, selection);
    require(
        preview != null && CharacterChanges.change(player, selection, preview),
        "Confirmed " + selection);
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
