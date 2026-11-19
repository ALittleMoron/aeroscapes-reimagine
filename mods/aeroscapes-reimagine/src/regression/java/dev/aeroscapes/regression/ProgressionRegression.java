package dev.aeroscapes.regression;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.progression.SpellPractice;
import harmonised.pmmo.api.APIUtils;
import harmonised.pmmo.api.enums.EventType;
import harmonised.pmmo.config.Config;
import harmonised.pmmo.core.Core;
import harmonised.pmmo.storage.PmmoSavedData;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;

/** Practice levels feed both finite talent budgets through the owned point bridge. */
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
  }

  private static void verifyListed(ServerPlayer player, TestShipPlayerSession session) {
    ProgressionBridge.sync(player);
    requirePoints(
        player, 1, 0, "The initial magic repertoire is affordable without unrelated practice");
    verifyAdminCommands(player, session);

    APIUtils.setLevel("combat", player, 12);
    APIUtils.setLevel("magic", player, 5);
    APIUtils.setLevel("mining", player, 10);
    APIUtils.setLevel("cooking", player, 4);
    ProgressionBridge.sync(player);
    requirePoints(player, 8, 2, "Magic practice has its own finite reward milestones");
    requireClientPoints(session, 8, 2);
    ProgressionBridge.sync(player);
    requirePoints(player, 8, 2, "Repeated synchronization does not duplicate points");

    APIUtils.setLevel("archery", player, 14);
    for (int step = 0; step < 20 && APIUtils.getLevel("archery", player) < 15; step++) {
      APIUtils.addXp("archery", player, 50);
    }
    require(APIUtils.getLevel("archery", player) == 15, "Practice reaches the next level");
    tick(player);
    requirePoints(player, 11, 2, "A level-up through practice refreshes points by itself");
    // Project MMO's admin commands change levels without posting a level-up event.
    APIUtils.setLevel("farming", player, 10);
    tick(player);
    requirePoints(
        player, 11, 2, "Profession practice above the catalogue maximum grants no excess points");

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
        player, 11, 2, "Stored points reconcile even when earned levels have not changed");
    requireClientPoints(session, 11, 2);
    verifyTalentInvestment(player, session);

    long smithing = APIUtils.getXp("smithing", player);
    NeoForge.EVENT_BUS.post(
        new PlayerEvent.ItemSmeltedEvent(player, new ItemStack(Items.IRON_INGOT, 3)));
    require(
        APIUtils.getXp("smithing", player) == smithing + 6, "Taking smelted metal trains smithing");
    long cooking = APIUtils.getXp("cooking", player);
    NeoForge.EVENT_BUS.post(
        new PlayerEvent.ItemSmeltedEvent(player, new ItemStack(Items.COOKED_BEEF, 2)));
    require(APIUtils.getXp("cooking", player) == cooking + 4, "Taking cooked food trains cooking");

    var breadCraft =
        APIUtils.getXpAwardMap(
            new ItemStack(Items.BREAD), EventType.CRAFT, LogicalSide.SERVER, player);
    require(
        breadCraft.getOrDefault("cooking", 0L) > 0 && !breadCraft.containsKey("crafting"),
        "Crafting food trains cooking, not general crafting: " + breadCraft);
    var plankCraft =
        APIUtils.getXpAwardMap(
            new ItemStack(Items.OAK_PLANKS), EventType.CRAFT, LogicalSide.SERVER, player);
    require(
        plankCraft.getOrDefault("crafting", 0L) > 0,
        "Ordinary crafting keeps positive crafting XP: " + plankCraft);
    require(
        SpellPractice.experience(15) == 7 && SpellPractice.experience(0) == 1,
        "Each cast trains magic, even from a free scroll");
    for (String skill : Config.skills().skills().keySet()) APIUtils.setLevel(skill, player, 50);
    tick(player);
    requirePoints(
        player,
        62,
        2,
        "Mastering all six practices funds exactly the compatible profession purchases");
    requireClientPoints(session, 62, 2);
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
        player, 2, 0, "Admin commands update points before a paused inventory can stop ticks");
    command(player, "pmmo admin @s set magic level 9");
    require(
        MagicPractice.level(player) == 9,
        "A native administrative magic edit updates the owned readiness");
    requirePoints(player, combatPoints(player), 0, "Magic shares the same combat source");
    for (int amount : List.of(2000, 20000)) {
      command(player, "pmmo admin @s add combat xp " + amount);
      int expected = combatPoints(player);
      requirePoints(player, expected, 0, "The owner's XP command updates the server and talents");
      var packet = session.talentPoints(ProgressionBridge.COMBAT_CATEGORY);
      require(packet != null && packet.earnedPoints() == expected, "Admin points reach the client");
    }
    command(player, "pmmo admin @s add combat level 1");
    command(player, "pmmo admin @s set combat xp 123");
    require(APIUtils.getXp("combat", player) == 123, "The set-XP command stores its native value");
    command(player, "pmmo admin @s set combat level 9");
    command(player, "pmmo admin @s clear combat");
    requirePoints(player, combatPoints(player), 0, "Clearing one skill preserves magic practice");
    command(player, "pmmo admin @s clear");
    requirePoints(player, 1, 0, "Clearing practice retains only the initial repertoire point");
    require(MagicPractice.credit(player) == 0, "Native clear also clears owned magic practice");
  }

  private static int combatPoints(ServerPlayer player) {
    int physical =
        (int) (APIUtils.getLevel("combat", player) / 5 + APIUtils.getLevel("archery", player) / 5);
    return Math.min(
        MagicTalents.pointCap(),
        MagicPracticeProgress.points(MagicPractice.credit(player), MagicTalents.pointCap())
            + physical);
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
    provider.tryUnlockSkill(player, combat, "melee_strike", false);
    provider.tryUnlockSkill(player, combat, "magic_mastery_1", false);
    var update = session.talentPoints(ProgressionBridge.COMBAT_CATEGORY);
    require(
        update != null && update.earnedPoints() == 11 && update.spentPoints() == 2,
        "Melee and magic purchases spend the same pool and update the client");
    provider.tryUnlockSkill(player, combat, "magic_recovery_1", false);
    provider.tryUnlockSkill(player, combat, "magic_rhythm_1", false);
    require(
        provider.getSkillState(player, combat, "magic_recovery_1").orElseThrow()
                == Skill.State.UNLOCKED
            && provider.getSkillState(player, combat, "magic_rhythm_1").orElseThrow()
                == Skill.State.UNLOCKED,
        "Resource recovery and casting rhythm are compatible separate investments");
    CompoundTag saved = new CompoundTag();
    provider.exportPlayerData(player, saved);
    provider.importPlayerData(player, saved);
    provider.onPlayerDeath(player);
    tick(player);
    require(
        provider.getSkillState(player, combat, "magic_recovery_1").orElseThrow()
            == Skill.State.UNLOCKED,
        "Native save/import and death retain learned talents");
    requirePoints(player, 11, 2, "Reload and death preserve the earned point sources");
  }

  private static void tick(ServerPlayer player) {
    player.tickCount = 20;
    NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
  }

  private static void requireClientPoints(
      TestShipPlayerSession session, int combat, int profession) {
    var combatPacket = session.talentPoints(ProgressionBridge.COMBAT_CATEGORY);
    var professionPacket = session.talentPoints(ProgressionBridge.PROFESSION_CATEGORY);
    require(
        combatPacket != null && combatPacket.earnedPoints() == combat,
        "Native client packet carries combat points");
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
      ServerPlayer player, int combat, int profession, String message) {
    int storedCombat;
    int storedProfession;
    try {
      storedCombat = ProgressionBridge.storedPoints(player, ProgressionBridge.COMBAT_CATEGORY);
      storedProfession =
          ProgressionBridge.storedPoints(player, ProgressionBridge.PROFESSION_CATEGORY);
    } catch (CommandSyntaxException exception) {
      throw new IllegalStateException("Talent categories are loaded", exception);
    }
    require(
        storedCombat == combat && storedProfession == profession,
        message + " (combat " + storedCombat + ", profession " + storedProfession + ")");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
