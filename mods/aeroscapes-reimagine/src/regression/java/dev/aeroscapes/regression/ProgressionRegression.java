package dev.aeroscapes.regression;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aeroscapes.equipment.armor.ArmorProficiency;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.progression.SpellPractice;
import harmonised.pmmo.api.APIUtils;
import harmonised.pmmo.config.Config;
import harmonised.pmmo.core.Core;
import harmonised.pmmo.storage.PmmoSavedData;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;

/** Each practice group funds its own finite talent budget. */
final class ProgressionRegression {
  private static final ResourceLocation WARRIOR =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final ResourceLocation RANGER =
      ResourceLocation.parse(ProgressionBridge.RANGER_CATEGORY);
  private static final ResourceLocation MAGE =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);

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
                    "ranger",
                    "archery",
                    "magic",
                    "mining",
                    "crafting",
                    "smithing",
                    "building",
                    "farming",
                    "cooking",
                    "enchanting",
                    "pickaxe_handling",
                    "axe_handling",
                    "shovel_handling",
                    "hoe_handling",
                    "shears_handling")),
        "Only the configured pack disciplines are defined");
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
    verifyActionRouting(level, position);
    verifyCombatPath(
        level, position, WARRIOR, List.of("shield_handling", "shield_shove", "wide_shield_shove"));
    verifyCombatPath(
        level, position, WARRIOR, List.of("melee_strike", "last_stand", "offensive_stance"));
    verifyCombatPath(level, position, RANGER, List.of("light_weapon_handling", "duelist_parry"));
    verifyCombatPath(level, position, RANGER, List.of("steady_aim", "triple_shot"));
  }

  private static void verifyCombatPath(
      ServerLevel level, Vec3 position, ResourceLocation category, List<String> path) {
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "CombatPath", position, null)) {
      var player = session.player;
      listPlayer(level, player, true);
      try {
        var provider = SkillsMod.getInstance();
        for (int index = 0; index < path.size(); index++) {
          int practiceLevel = 3 + index * 5;
          APIUtils.setLevel(category.getPath(), player, practiceLevel);
          ProgressionBridge.sync(player);
          String node = path.get(index);
          if (index + 1 < path.size()) {
            String next = path.get(index + 1);
            provider.tryUnlockSkill(player, category, next, false);
            require(
                provider.getSpentPoints(player, category).orElseThrow() == index,
                next + " requires " + node);
          }
          provider.tryUnlockSkill(player, category, node, false);
          require(
              provider.getSkillState(player, category, node).orElseThrow() == Skill.State.UNLOCKED
                  && provider.getPointsLeft(player, category).orElseThrow() == 0,
              category + " level " + practiceLevel + " funds " + node + " without class credit");
        }
      } finally {
        listPlayer(level, player, false);
      }
    }
  }

  private static void verifyActionRouting(ServerLevel level, Vec3 position) {
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "ClassPracticeActions", position, null)) {
      var player = session.player;
      listPlayer(level, player, true);
      try {
        verifyHit(player, Items.IRON_SWORD.getDefaultInstance(), WARRIOR);
        var knife =
            BuiltInRegistries.ITEM
                .get(ResourceLocation.parse("farmersdelight:iron_knife"))
                .getDefaultInstance();
        verifyHit(player, knife, RANGER);
        long warrior = APIUtils.getXp("combat", player);
        long ranger = APIUtils.getXp("ranger", player);
        long archery = APIUtils.getXp("archery", player);
        var arrow = new Arrow(EntityType.ARROW, level);
        arrow.setOwner(player);
        try {
          verifyDamage(player, player.damageSources().arrow(arrow, player));
          require(
              APIUtils.getXp("combat", player) == warrior
                  && APIUtils.getXp("ranger", player) > ranger
                  && APIUtils.getXp("archery", player) > archery,
              "An owned arrow funds Ranger and its native ranged proficiency only");
        } finally {
          arrow.discard();
        }
        verifyShieldPractice(player);
        verifyArmorTraining(player);
      } finally {
        listPlayer(level, player, false);
      }
    }
  }

  private static void verifyHit(ServerPlayer player, ItemStack weapon, ResourceLocation bank) {
    player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
    long warrior = APIUtils.getXp("combat", player);
    long ranger = APIUtils.getXp("ranger", player);
    long archery = APIUtils.getXp("archery", player);
    verifyDamage(player, player.damageSources().playerAttack(player));
    require(
        (APIUtils.getXp("combat", player) > warrior) == bank.equals(WARRIOR)
            && (APIUtils.getXp("ranger", player) > ranger) == bank.equals(RANGER)
            && APIUtils.getXp("archery", player) == archery,
        "Direct damage with " + weapon + " funds only " + bank);
  }

  private static void verifyDamage(ServerPlayer player, DamageSource source) {
    var level = player.serverLevel();
    var target = new Zombie(EntityType.ZOMBIE, level);
    target.moveTo(player.getX() + 2, player.getY(), player.getZ());
    target.setNoAi(true);
    require(level.addFreshEntity(target), "Spawn a practice target");
    try {
      require(target.hurt(source, 4), "Apply qualifying damage to the practice target");
    } finally {
      target.discard();
    }
  }

  private static void verifyShieldPractice(ServerPlayer player) {
    var enemy = new Zombie(EntityType.ZOMBIE, player.serverLevel());
    var source = player.damageSources().mobAttack(enemy);
    long warrior = APIUtils.getXp("combat", player);
    long ranger = APIUtils.getXp("ranger", player);
    NeoForge.EVENT_BUS.post(
        new LivingShieldBlockEvent(player, new DamageContainer(source, 4), true));
    require(
        APIUtils.getXp("combat", player) == warrior + 4
            && APIUtils.getXp("ranger", player) == ranger,
        "A successful shield block gives exactly one Warrior award");
  }

  private static void verifyArmorTraining(ServerPlayer player) {
    var provider = SkillsMod.getInstance();
    var leather = Items.LEATHER_CHESTPLATE.getDefaultInstance();
    var chainmail = Items.CHAINMAIL_CHESTPLATE.getDefaultInstance();
    var heavy = Items.IRON_CHESTPLATE.getDefaultInstance();
    var heavyCaster =
        BuiltInRegistries.ITEM
            .get(ResourceLocation.parse("irons_spellbooks:paladin_chestplate"))
            .getDefaultInstance();
    require(
        !ArmorProficiency.undertrained(player, leather)
            && ArmorProficiency.undertrained(player, chainmail)
            && ArmorProficiency.undertrained(player, heavy)
            && ArmorProficiency.undertrained(player, heavyCaster),
        "Starter armor needs no talent and other armor starts undertrained");
    APIUtils.setLevel("ranger", player, 3);
    ProgressionBridge.sync(player);
    provider.tryUnlockSkill(player, RANGER, "light_armor_handling", false);
    require(
        !ArmorProficiency.undertrained(player, chainmail)
            && ArmorProficiency.undertrained(player, heavyCaster),
        "One Ranger point fully trains chainmail and leaves caster training independent");
    APIUtils.setLevel("combat", player, 3);
    ProgressionBridge.sync(player);
    provider.tryUnlockSkill(player, WARRIOR, "heavy_armor_handling", false);
    require(
        !ArmorProficiency.undertrained(player, heavy)
            && ArmorProficiency.undertrained(player, heavyCaster),
        "One Warrior point trains heavy equipment and leaves heavy caster gear undertrained");
  }

  private static void verifyListed(ServerPlayer player, TestShipPlayerSession session) {
    ProgressionBridge.sync(player);
    requirePoints(player, 0, 1, 0, "The initial repertoire point can only fund magic");
    verifyAdminCommands(player, session);

    APIUtils.setLevel("archery", player, 8);
    ProgressionBridge.sync(player);
    requirePoints(player, 0, 1, 0, "Legacy ranged practice does not fund a second bank");
    requireRangerPoints(player, 0);
    APIUtils.setLevel("ranger", player, 3);
    ProgressionBridge.sync(player);
    requireRangerPoints(player, 1);
    APIUtils.setLevel("ranger", player, 8);
    ProgressionBridge.sync(player);
    requireRangerPoints(player, 2);

    APIUtils.setLevel("combat", player, 12);
    APIUtils.setLevel("magic", player, 5);
    APIUtils.setLevel("mining", player, 10);
    APIUtils.setLevel("cooking", player, 4);
    ProgressionBridge.sync(player);
    requirePoints(
        player, 2, 6, 3, "Combat, Magic, Mining and Cooking milestones remain independent");
    requireClientPoints(session, 2, 6, 3);
    ProgressionBridge.sync(player);
    requirePoints(player, 2, 6, 3, "Repeated synchronization does not duplicate points");

    APIUtils.setLevel("ranger", player, 12);
    for (int step = 0; step < 20 && APIUtils.getLevel("ranger", player) < 13; step++) {
      APIUtils.addXp("ranger", player, 50);
    }
    require(APIUtils.getLevel("ranger", player) == 13, "Practice reaches the next level");
    tick(player);
    requireRangerPoints(player, 3);
    requirePoints(player, 2, 6, 3, "Ranger practice leaves the other pools unchanged");
    // Project MMO's admin commands change levels without posting a level-up event.
    APIUtils.setLevel("farming", player, 10);
    tick(player);
    requirePoints(player, 2, 6, 5, "Two more farming milestones fund further profession purchases");

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
        player, 2, 6, 5, "Stored points reconcile even when earned levels have not changed");
    requireClientPoints(session, 2, 6, 5);
    verifyTalentInvestment(player, session);
    requireRangerPoints(player, 3);

    require(
        SpellPractice.experience(15) == 7 && SpellPractice.experience(0) == 1,
        "Each cast trains magic, even from a free scroll");
    for (String skill : Config.skills().skills().keySet()) APIUtils.setLevel(skill, player, 50);
    int magicCap = MagicTalents.pointCap();
    MagicPractice.award(player, MagicPracticeProgress.xpForPoints(magicCap));
    tick(player);
    requireRangerPoints(player, ProgressionBridge.RANGER_POINT_CAP);
    var rangerPacket = session.talentPoints(ProgressionBridge.RANGER_CATEGORY);
    require(
        rangerPacket != null && rangerPacket.earnedPoints() == ProgressionBridge.RANGER_POINT_CAP,
        "Native client packet carries Ranger points");
    requirePoints(
        player,
        ProgressionBridge.COMBAT_POINT_CAP,
        magicCap,
        Arrays.stream(ProfessionDiscipline.values()).mapToInt(ProfessionDiscipline::cap).sum(),
        "Each cap funds the maximum compatible purchases in its own catalogue");
    requireClientPoints(
        session,
        ProgressionBridge.COMBAT_POINT_CAP,
        magicCap,
        Arrays.stream(ProfessionDiscipline.values()).mapToInt(ProfessionDiscipline::cap).sum());
  }

  private static void verifyAdminCommands(ServerPlayer player, TestShipPlayerSession session) {
    command(player, "pmmo admin @s set combat level 7");
    require(
        APIUtils.getLevel("combat", player) == 7,
        "The real admin command stores the level for a player without an XP map");
    var storage = (PmmoSavedData) Core.get(LogicalSide.SERVER).getData();
    var restored =
        new PmmoSavedData(
            storage.save(new CompoundTag(), player.registryAccess()), player.registryAccess());
    require(
        restored.getLevel("combat", player.getUUID()) == 7,
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
    return ProgressionBridge.classPracticePoints(
        APIUtils.getLevel("combat", player), ProgressionBridge.COMBAT_POINT_CAP);
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

    provider.tryUnlockSkill(player, WARRIOR, "magic_mastery_1", false);
    provider.tryUnlockSkill(player, WARRIOR, "steady_aim", false);
    provider.tryUnlockSkill(player, RANGER, "melee_strike", false);
    provider.tryUnlockSkill(player, MAGE, "melee_strike", false);
    require(
        provider.getSpentPoints(player, WARRIOR).orElseThrow() == 0
            && provider.getSpentPoints(player, MAGE).orElseThrow() == 0
            && provider.getSpentPoints(player, RANGER).orElseThrow() == 0,
        "Each pool rejects purchases from the other catalogue");
    provider.tryUnlockSkill(player, WARRIOR, "melee_strike", false);
    var combatUpdate = session.talentPoints(ProgressionBridge.COMBAT_CATEGORY);
    require(
        combatUpdate != null
            && combatUpdate.earnedPoints() == 2
            && combatUpdate.spentPoints() == 1
            && provider.getSpentPoints(player, MAGE).orElseThrow() == 0,
        "Melee purchases spend combat points in the client and leave magic intact");
    provider.tryUnlockSkill(player, MAGE, "magic_mastery_1", false);
    var magicUpdate = session.talentPoints(ProgressionBridge.MAGIC_CATEGORY);
    require(
        magicUpdate != null
            && magicUpdate.earnedPoints() == 6
            && magicUpdate.spentPoints() == 1
            && provider.getSpentPoints(player, WARRIOR).orElseThrow() == 1,
        "Magic purchases spend magic points in the client and leave combat intact");
    provider.tryUnlockSkill(player, MAGE, "magic_recovery_1", false);
    provider.tryUnlockSkill(player, MAGE, "magic_rhythm_1", false);
    require(
        provider.getSkillState(player, MAGE, "magic_recovery_1").orElseThrow()
                == Skill.State.UNLOCKED
            && provider.getSkillState(player, MAGE, "magic_rhythm_1").orElseThrow()
                == Skill.State.UNLOCKED,
        "Resource recovery and casting rhythm are compatible separate investments");
    provider.tryUnlockSkill(player, MAGE, "caster_armor_handling", false);
    require(
        provider.getSkillState(player, MAGE, "caster_armor_handling").orElseThrow()
            == Skill.State.UNLOCKED,
        "Magic practice alone funds caster armor training");
    provider.tryUnlockSkill(player, RANGER, "steady_aim", false);
    provider.tryUnlockSkill(player, RANGER, "triple_shot", false);
    APIUtils.setLevel("ranger", player, 0);
    ProgressionBridge.sync(player);
    require(
        provider.getPointsLeft(player, RANGER).orElseThrow() == -2,
        "Existing practice-funded Ranger purchases expose their signed deficit");
    provider.tryUnlockSkill(player, RANGER, "light_armor_handling", false);
    require(
        provider.getSpentPoints(player, RANGER).orElseThrow() == 2,
        "Overdrawn Ranger points block further purchases");
    APIUtils.setLevel("ranger", player, 13);
    ProgressionBridge.sync(player);
    require(
        provider.getPointsLeft(player, RANGER).orElseThrow() == 1,
        "Matching practice recovers the Ranger balance");
    CompoundTag saved = new CompoundTag();
    provider.exportPlayerData(player, saved);
    provider.importPlayerData(player, saved);
    provider.onPlayerDeath(player);
    tick(player);
    require(
        provider.getSkillState(player, MAGE, "magic_recovery_1").orElseThrow()
                == Skill.State.UNLOCKED
            && provider.getSkillState(player, WARRIOR, "melee_strike").orElseThrow()
                == Skill.State.UNLOCKED,
        "Native save/import and death retain learned talents");
    require(
        provider.getSkillState(player, RANGER, "triple_shot").orElseThrow() == Skill.State.UNLOCKED,
        "Native save/import and death retain learned Ranger talents");
    requirePoints(player, 2, 6, 5, "Reload and death preserve the earned point sources");
  }

  private static void tick(ServerPlayer player) {
    player.tickCount = 20;
    NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
  }

  private static void requireClientPoints(
      TestShipPlayerSession session, int combat, int magic, int profession) {
    var combatPacket = session.talentPoints(ProgressionBridge.COMBAT_CATEGORY);
    var magicPacket = session.talentPoints(ProgressionBridge.MAGIC_CATEGORY);
    int professionPoints = 0;
    for (var tree : ProfessionDiscipline.values()) {
      var packet = session.talentPoints(tree.category().toString());
      if (packet != null) professionPoints += packet.earnedPoints();
    }
    require(
        combatPacket != null && combatPacket.earnedPoints() == combat,
        "Native client packet carries combat points");
    require(
        magicPacket != null && magicPacket.earnedPoints() == magic,
        "Native client packet carries magic points");
    require(professionPoints == profession, "Native client packet carries profession points");
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

  private static void requireRangerPoints(ServerPlayer player, int expected) {
    try {
      int points = ProgressionBridge.storedPoints(player, ProgressionBridge.RANGER_CATEGORY);
      require(points == expected, "Expected " + expected + " Ranger points, received " + points);
    } catch (CommandSyntaxException exception) {
      throw new IllegalStateException("The Ranger talent category is loaded", exception);
    }
  }

  private static void requirePoints(
      ServerPlayer player, int combat, int magic, int profession, String message) {
    int storedCombat;
    int storedMagic;
    int storedProfession = 0;
    try {
      storedCombat = ProgressionBridge.storedPoints(player, ProgressionBridge.COMBAT_CATEGORY);
      storedMagic = ProgressionBridge.storedPoints(player, ProgressionBridge.MAGIC_CATEGORY);
      for (var tree : ProfessionDiscipline.values())
        storedProfession += ProgressionBridge.storedPoints(player, tree.category().toString());
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
