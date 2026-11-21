package dev.aeroscapes.regression;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.progression.SpellPractice;
import harmonised.pmmo.api.APIUtils;
import harmonised.pmmo.config.Config;
import harmonised.pmmo.core.Core;
import harmonised.pmmo.storage.PmmoSavedData;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;

/** Each practice group funds its own finite talent budget. */
final class ProgressionRegression {
  private ProgressionRegression() {}

  static void verify(ServerLevel level) {
    var levels = Config.server().levels();
    require(levels.maxLevel() == 50, "Practice levels stop at 50");
    require(levels.lossOnDeath() == 0.0, "Death keeps practice progress");
    require(
        Config.skills()
            .skills()
            .keySet()
            .equals(
                Set.of(
                    "combat",
                    "archery",
                    "magic",
                    "mining",
                    "crafting",
                    "smithing",
                    "building",
                    "farming",
                    "cooking")),
        "Only the nine pack disciplines are defined");
    require(
        Config.server().requirements().enabled().values().stream().noneMatch(Boolean::booleanValue),
        "Practice levels never lock items, blocks or actions");

    var position = Vec3.atBottomCenterOf(level.getSharedSpawnPos());
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "ProgressionFixture", position, null)) {
      ServerPlayer player = session.player;
      // Project MMO commits experience only for players in the server list, as after a login.
      listPlayer(level, player, true);
      try {
        verifyListed(player, session);
      } finally {
        listPlayer(level, player, false);
      }
    }
    verifyLegacyAllocation(level, position);
  }

  private static void verifyLegacyAllocation(ServerLevel level, Vec3 position) {
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "LegacyTalentPool", position, null)) {
      var player = session.player;
      listPlayer(level, player, true);
      try {
        var provider = SkillsMod.getInstance();
        var combat = ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
        var magic = ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
        var originalCombat = Set.of("quick_block", "shield_march");
        var originalMagic = Set.of("magic_mastery_1", "magic_recovery_1");
        // The preceding release already stored magic practice, with both allocations in combat.
        MagicPractice.credit(player);
        APIUtils.setLevel("combat", player, 5);
        var legacy = new CompoundTag();
        provider.exportPlayerData(player, legacy);
        var categories = legacy.getCompound("categories");
        var combined = categories.getCompound(ProgressionBridge.COMBAT_CATEGORY);
        var unlocked = new ListTag();
        originalCombat.forEach(node -> unlocked.add(StringTag.valueOf(node)));
        originalMagic.forEach(node -> unlocked.add(StringTag.valueOf(node)));
        combined.putBoolean("unlocked", true);
        combined.put("unlocked_skills", unlocked);
        var sources = new CompoundTag();
        sources.putInt("aeroscapes_reimagine:skill_levels", 4);
        combined.put("points", sources);
        categories.put(ProgressionBridge.COMBAT_CATEGORY, combined);
        legacy.put("categories", categories);
        provider.importPlayerData(player, legacy);

        ProgressionBridge.sync(player);
        require(
            Set.copyOf(provider.getUnlockedSkills(player, combat).orElseThrow())
                    .equals(originalCombat)
                && Set.copyOf(provider.getUnlockedSkills(player, magic).orElseThrow())
                    .equals(originalMagic)
                && provider.getPointsLeft(player, combat).orElseThrow() == -1
                && provider.getPointsLeft(player, magic).orElseThrow() == -1,
            "The saved allocation splits without losing nodes or retaining the combined credit");
        provider.tryUnlockSkill(player, combat, "melee_strike", false);
        provider.tryUnlockSkill(player, magic, "magic_rhythm_1", false);
        require(
            provider.getSpentPoints(player, combat).orElseThrow() == 2
                && provider.getSpentPoints(player, magic).orElseThrow() == 2,
            "Overdrawn pools preserve existing nodes and block further purchases");
        MagicPractice.award(player, 200);
        ProgressionBridge.sync(player);
        provider.tryUnlockSkill(player, magic, "magic_rhythm_1", false);
        require(
            provider.getSkillState(player, magic, "magic_rhythm_1").orElseThrow()
                    == Skill.State.UNLOCKED
                && provider.getPointsLeft(player, combat).orElseThrow() == -1,
            "Magic practice only makes further magic purchases affordable");
        APIUtils.setLevel("combat", player, 15);
        ProgressionBridge.sync(player);
        provider.tryUnlockSkill(player, combat, "melee_strike", false);
        require(
            provider.getSkillState(player, combat, "melee_strike").orElseThrow()
                    == Skill.State.UNLOCKED
                && provider.getPointsLeft(player, magic).orElseThrow() == 0,
            "Combat practice only makes further combat purchases affordable");
      } finally {
        listPlayer(level, player, false);
      }
    }
  }

  private static void verifyListed(ServerPlayer player, TestShipPlayerSession session) {
    ProgressionBridge.sync(player);
    requirePoints(player, 0, 1, 0, "The initial repertoire point can only fund magic");
    verifyAdminCommands(player, session);

    APIUtils.setLevel("combat", player, 12);
    APIUtils.setLevel("magic", player, 5);
    APIUtils.setLevel("mining", player, 10);
    APIUtils.setLevel("cooking", player, 4);
    ProgressionBridge.sync(player);
    requirePoints(player, 2, 6, 2, "Practice rewards are independent");
    requireClientPoints(session, 2, 6, 2);
    ProgressionBridge.sync(player);
    requirePoints(player, 2, 6, 2, "Repeated synchronization does not duplicate points");

    APIUtils.setLevel("archery", player, 14);
    for (int step = 0; step < 20 && APIUtils.getLevel("archery", player) < 15; step++) {
      APIUtils.addXp("archery", player, 50);
    }
    require(APIUtils.getLevel("archery", player) == 15, "Practice reaches the next level");
    tick(player);
    requirePoints(player, 5, 6, 2, "A level-up through practice updates only its own pool");
    // Project MMO's admin commands change levels without posting a level-up event.
    APIUtils.setLevel("farming", player, 10);
    tick(player);
    requirePoints(player, 5, 6, 4, "Two more farming milestones fund further profession purchases");

    try {
      player
          .server
          .getCommands()
          .getDispatcher()
          .execute(
              "puffish_skills points set @s "
                  + ProgressionBridge.COMBAT_CATEGORY
                  + " 0 aeroscapes_reimagine:skill_levels",
              player.createCommandSourceStack().withPermission(2).withSuppressedOutput());
    } catch (CommandSyntaxException exception) {
      throw new IllegalStateException("Cannot reproduce replaced point-source data", exception);
    }
    tick(player);
    requirePoints(
        player, 5, 6, 4, "Stored points reconcile even when earned levels have not changed");
    requireClientPoints(session, 5, 6, 4);
    verifyTalentInvestment(player, session);

    require(
        SpellPractice.experience(15) == 7 && SpellPractice.experience(0) == 1,
        "Each cast trains magic, even from a free scroll");
    for (String skill : Config.skills().skills().keySet()) APIUtils.setLevel(skill, player, 50);
    int magicCap = MagicTalents.pointCap();
    MagicPractice.award(player, MagicPracticeProgress.xpForPoints(magicCap));
    tick(player);
    requirePoints(
        player,
        ProgressionBridge.COMBAT_POINT_CAP,
        magicCap,
        ProgressionBridge.PROFESSION_POINT_CAP,
        "Each cap funds the maximum compatible purchases in its own catalogue");
    requireClientPoints(
        session,
        ProgressionBridge.COMBAT_POINT_CAP,
        magicCap,
        ProgressionBridge.PROFESSION_POINT_CAP);
  }

  private static void verifyAdminCommands(ServerPlayer player, TestShipPlayerSession session) {
    command(player, "pmmo admin @s set combat level 9");
    require(
        APIUtils.getLevel("combat", player) == 9,
        "The real admin command stores the level for a player without an XP map");
    var storage = (PmmoSavedData) Core.get(LogicalSide.SERVER).getData();
    var restored =
        new PmmoSavedData(
            storage.save(new CompoundTag(), player.registryAccess()), player.registryAccess());
    require(
        restored.getLevel("combat", player.getUUID()) == 9,
        "The real native save/read codec preserves the commanded level");
    requirePoints(
        player, 1, 1, 0, "Admin commands update points before a paused inventory can stop ticks");
    command(player, "pmmo admin @s set magic level 9");
    require(
        MagicPractice.level(player) == 9,
        "A native administrative magic edit updates the owned readiness");
    int magicPoints =
        MagicPracticeProgress.points(MagicPractice.credit(player), MagicTalents.pointCap());
    requirePoints(player, 1, magicPoints, 0, "A magic command only changes its own talent pool");
    for (int amount : List.of(2000, 20000)) {
      command(player, "pmmo admin @s add combat xp " + amount);
      int expected = combatPoints(player);
      requirePoints(player, expected, magicPoints, 0, "The owner's XP command updates combat only");
      var packet = session.talentPoints(ProgressionBridge.COMBAT_CATEGORY);
      require(packet != null && packet.earnedPoints() == expected, "Admin points reach the client");
    }
    command(player, "pmmo admin @s add combat level 1");
    command(player, "pmmo admin @s set combat xp 123");
    require(APIUtils.getXp("combat", player) == 123, "The set-XP command stores its native value");
    command(player, "pmmo admin @s set combat level 9");
    command(player, "pmmo admin @s clear combat");
    requirePoints(player, 0, magicPoints, 0, "Clearing combat preserves magic practice");
    command(player, "pmmo admin @s clear");
    requirePoints(player, 0, 1, 0, "Clearing practice retains only the initial repertoire point");
    require(MagicPractice.credit(player) == 0, "Native clear also clears owned magic practice");
  }

  private static int combatPoints(ServerPlayer player) {
    int physical =
        (int) (APIUtils.getLevel("combat", player) / 5 + APIUtils.getLevel("archery", player) / 5);
    return Math.min(ProgressionBridge.COMBAT_POINT_CAP, physical);
  }

  private static void command(ServerPlayer player, String text) {
    var storage = (PmmoSavedData) Core.get(LogicalSide.SERVER).getData();
    storage.setDirty(false);
    try {
      player
          .server
          .getCommands()
          .getDispatcher()
          .execute(
              text, player.createCommandSourceStack().withPermission(2).withSuppressedOutput());
    } catch (CommandSyntaxException exception) {
      throw new IllegalStateException("Fixture command failed: " + text, exception);
    }
    require(storage.isDirty(), "Native admin edits must mark player data for saving: " + text);
  }

  private static void verifyTalentInvestment(ServerPlayer player, TestShipPlayerSession session) {
    var provider = SkillsMod.getInstance();
    var combat = ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
    var magic = ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
    provider.tryUnlockSkill(player, combat, "magic_mastery_1", false);
    provider.tryUnlockSkill(player, magic, "melee_strike", false);
    require(
        provider.getSpentPoints(player, combat).orElseThrow() == 0
            && provider.getSpentPoints(player, magic).orElseThrow() == 0,
        "Each pool rejects purchases from the other catalogue");
    provider.tryUnlockSkill(player, combat, "melee_strike", false);
    var combatUpdate = session.talentPoints(ProgressionBridge.COMBAT_CATEGORY);
    require(
        combatUpdate != null
            && combatUpdate.earnedPoints() == 5
            && combatUpdate.spentPoints() == 1
            && provider.getSpentPoints(player, magic).orElseThrow() == 0,
        "Melee purchases spend combat points in the client and leave magic intact");
    provider.tryUnlockSkill(player, magic, "magic_mastery_1", false);
    var magicUpdate = session.talentPoints(ProgressionBridge.MAGIC_CATEGORY);
    require(
        magicUpdate != null
            && magicUpdate.earnedPoints() == 6
            && magicUpdate.spentPoints() == 1
            && provider.getSpentPoints(player, combat).orElseThrow() == 1,
        "Magic purchases spend magic points in the client and leave combat intact");
    provider.tryUnlockSkill(player, magic, "magic_recovery_1", false);
    provider.tryUnlockSkill(player, magic, "magic_rhythm_1", false);
    require(
        provider.getSkillState(player, magic, "magic_recovery_1").orElseThrow()
                == Skill.State.UNLOCKED
            && provider.getSkillState(player, magic, "magic_rhythm_1").orElseThrow()
                == Skill.State.UNLOCKED,
        "Resource recovery and casting rhythm are compatible separate investments");
    CompoundTag saved = new CompoundTag();
    provider.exportPlayerData(player, saved);
    provider.importPlayerData(player, saved);
    provider.onPlayerDeath(player);
    tick(player);
    require(
        provider.getSkillState(player, magic, "magic_recovery_1").orElseThrow()
                == Skill.State.UNLOCKED
            && provider.getSkillState(player, combat, "melee_strike").orElseThrow()
                == Skill.State.UNLOCKED,
        "Native save/import and death retain learned talents");
    requirePoints(player, 5, 6, 4, "Reload and death preserve the earned point sources");
  }

  private static void tick(ServerPlayer player) {
    player.tickCount = 20;
    NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
  }

  private static void requireClientPoints(
      TestShipPlayerSession session, int combat, int magic, int profession) {
    var combatPacket = session.talentPoints(ProgressionBridge.COMBAT_CATEGORY);
    var magicPacket = session.talentPoints(ProgressionBridge.MAGIC_CATEGORY);
    var professionPacket = session.talentPoints(ProgressionBridge.PROFESSION_CATEGORY);
    require(
        combatPacket != null && combatPacket.earnedPoints() == combat,
        "Native client packet carries combat points");
    require(
        magicPacket != null && magicPacket.earnedPoints() == magic,
        "Native client packet carries magic points");
    require(
        professionPacket != null && professionPacket.earnedPoints() == profession,
        "Native client packet carries profession points");
  }

  @SuppressWarnings("unchecked")
  static void listPlayer(ServerLevel level, ServerPlayer player, boolean listed) {
    try {
      PlayerList list = level.getServer().getPlayerList();
      Field players = PlayerList.class.getDeclaredField("players");
      Field byUuid = PlayerList.class.getDeclaredField("playersByUUID");
      players.setAccessible(true);
      byUuid.setAccessible(true);
      var ordered = (List<ServerPlayer>) players.get(list);
      var indexed = (Map<UUID, ServerPlayer>) byUuid.get(list);
      if (listed) {
        ordered.add(player);
        indexed.put(player.getUUID(), player);
      } else {
        ordered.remove(player);
        indexed.remove(player.getUUID());
      }
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot list the fixture player", exception);
    }
  }

  private static void requirePoints(
      ServerPlayer player, int combat, int magic, int profession, String message) {
    int storedCombat;
    int storedMagic;
    int storedProfession;
    try {
      storedCombat = ProgressionBridge.storedPoints(player, ProgressionBridge.COMBAT_CATEGORY);
      storedMagic = ProgressionBridge.storedPoints(player, ProgressionBridge.MAGIC_CATEGORY);
      storedProfession =
          ProgressionBridge.storedPoints(player, ProgressionBridge.PROFESSION_CATEGORY);
    } catch (CommandSyntaxException exception) {
      throw new IllegalStateException("Talent categories are loaded", exception);
    }
    require(
        storedCombat == combat && storedMagic == magic && storedProfession == profession,
        message
            + " (combat "
            + storedCombat
            + ", magic "
            + storedMagic
            + ", profession "
            + storedProfession
            + ")");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
