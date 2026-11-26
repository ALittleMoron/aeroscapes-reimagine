package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.progression.TalentRefunds;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;

final class TalentRefundRegression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final ResourceLocation MAGIC =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
  private static final ResourceLocation MINING = ProfessionDiscipline.MINING.category();

  private TalentRefundRegression() {}

  static void verify(ServerLevel level) {
    var position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    var uuid = UUID.randomUUID();
    CompoundTag saved;
    try (var session = TestShipPlayerSession.create(level, uuid, "TalentRefund", position, null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        MagicData.getPlayerMagicData(player).setServerPlayer(player);
        require(
            RaceIdentity.choose(
                player,
                Race.HUMAN,
                0,
                CharacterClass.WARRIOR,
                "quick_block",
                Profession.BLACKSMITH,
                "material_repair"),
            "Character selection");
        verifyRefund(player);
        saved = player.saveWithoutId(new CompoundTag());
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    try (var session = TestShipPlayerSession.create(level, uuid, "TalentRefund", position, saved)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        MagicData.getPlayerMagicData(player).setServerPlayer(player);
        verifyRecoveryAfterLogin(player);
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void verifyRefund(ServerPlayer player) {
    var skills = SkillsMod.getInstance();
    int magicCap = MagicTalents.pointCap();
    MagicPractice.award(player, MagicPracticeProgress.xpForPoints(magicCap));
    for (String skill : ProgressionBridge.COMBAT_SKILLS)
      APIUtils.setLevel(skill, player, skill.equals("archery") ? 40 : 50);
    APIUtils.setLevel("mining", player, 45);
    ProgressionBridge.sync(player);
    require(
        skills.getPointsTotal(player, COMBAT).orElseThrow() == ProgressionBridge.COMBAT_POINT_CAP
            && skills.getPointsTotal(player, MAGIC).orElseThrow() == magicCap
            && skills.getPointsTotal(player, MINING).orElseThrow()
                == ProfessionDiscipline.MINING.cap(),
        "All three budgets can reach their own caps");
    purchase(
        player,
        COMBAT,
        List.of(
            "guard_armor",
            "guard_vitality",
            "melee_strike",
            "melee_footing",
            "melee_veteran",
            "bow_stride",
            "steady_aim",
            "shield_march",
            "quick_recovery",
            "opening_strike"));
    purchase(
        player,
        MAGIC,
        List.of(
            "magic_mastery_1",
            "magic_recovery_1",
            "magic_rhythm_1",
            "magic_reserve_2",
            "magic_destruction_power",
            "magic_destruction_mastery_2",
            "magic_destruction_mana",
            "magic_reserve_1",
            "magic_destruction_mastery_3",
            "magic_restoration_power"));
    purchase(player, MINING, List.of("mining_efficiency", "mining_fortune"));
    require(
        TalentRefunds.preview(player, "combat", List.of("quick_block")).isEmpty()
            && TalentRefunds.preview(player, "smithing", List.of("material_repair")).isEmpty(),
        "Active starters cannot be sold");
    var guard = List.of("guard_armor");
    var first = TalentRefunds.preview(player, "combat", guard).orElseThrow();
    require(
        first.nodes().equals(List.of("guard_armor", "guard_vitality"))
            && first.spent() == 2
            && first.loss() == 0
            && first.remainder() == 2,
        "Dependent guard refund");
    require(
        TalentRefunds.apply(player, guard, first) && !TalentRefunds.apply(player, guard, first),
        "Apply once and reject a stale quote");

    var mining = List.of("mining_efficiency");
    var second = TalentRefunds.preview(player, "mining", mining).orElseThrow();
    require(
        second.nodes().equals(List.of("mining_efficiency", "mining_fortune"))
            && second.loss() == 0
            && second.remainder() == 2
            && TalentRefunds.apply(player, mining, second),
        "Separate profession dependency and fractional carry");

    var roots =
        List.of(
            "melee_strike",
            "bow_stride",
            "steady_aim",
            "shield_march",
            "quick_recovery",
            "opening_strike");
    var last = TalentRefunds.preview(player, "combat", roots).orElseThrow();
    require(
        last.spent() == 8
            && last.loss() == 1
            && last.remainder() == 0
            && TalentRefunds.apply(player, roots, last),
        "Combat refunds accumulate a ten-point deduction independently");
    var mastery = List.of("magic_mastery_1");
    var magic = TalentRefunds.preview(player, "magic", mastery).orElseThrow();
    require(
        magic.nodes().size() == 10
            && magic.spent() == 10
            && magic.loss() == 1
            && magic.remainder() == 0
            && TalentRefunds.apply(player, mastery, magic),
        "Refunding magic mastery removes dependent magic talents and charges its own fee");
    ProgressionBridge.sync(player);
    require(
        skills.getPointsTotal(player, COMBAT).orElseThrow()
                == ProgressionBridge.COMBAT_POINT_CAP - 1
            && skills.getPointsTotal(player, MAGIC).orElseThrow() == magicCap - 1,
        "Reconciliation preserves each penalty at its earned cap");
  }

  private static void verifyRecoveryAfterLogin(ServerPlayer player) {
    int magicCap = MagicTalents.pointCap();
    int combatCap = ProgressionBridge.COMBAT_POINT_CAP;
    var skills = SkillsMod.getInstance();
    ProgressionBridge.sync(player);
    require(
        skills.getPointsTotal(player, COMBAT).orElseThrow() == combatCap - 1
            && skills.getPointsTotal(player, MAGIC).orElseThrow() == magicCap - 1,
        "Loading the saved player retains both deductions");
    purchase(player, MINING, List.of("mining_efficiency"));
    require(
        TalentRefunds.preview(player, "mining", List.of("mining_efficiency"))
                .orElseThrow()
                .remainder()
            == 3,
        "Save retains the fractional profession fee");
    APIUtils.addXp("cooking", player, 3000);
    MagicPractice.award(player, 2999);
    ProgressionBridge.sync(player);
    require(
        skills.getPointsTotal(player, COMBAT).orElseThrow() == combatCap - 1
            && skills.getPointsTotal(player, MAGIC).orElseThrow() == magicCap - 1
            && ProgressionBridge.nextMagicPointXp(player, MagicPractice.credit(player))
                == MagicPractice.credit(player) + 1,
        "Profession practice repays neither debt and 2999 magic XP does not cover its fee");
    MagicPractice.award(player, 1);
    require(
        skills.getPointsTotal(player, COMBAT).orElseThrow() == combatCap - 1
            && skills.getPointsTotal(player, MAGIC).orElseThrow() == magicCap
            && ProgressionBridge.nextMagicPointXp(player, MagicPractice.credit(player)) == 0,
        "Capped magic practice restores only the magic point");
    APIUtils.addXp("archery", player, 2999);
    require(
        skills.getPointsTotal(player, COMBAT).orElseThrow() == combatCap - 1,
        "Combat debt survives until enough physical practice is credited");
    APIUtils.addXp("archery", player, 1);
    require(
        skills.getPointsTotal(player, COMBAT).orElseThrow() == combatCap
            && skills.getPointsTotal(player, MAGIC).orElseThrow() == magicCap,
        "Archery practice repays the combat debt without exceeding either budget");
  }

  private static void purchase(ServerPlayer player, ResourceLocation category, List<String> nodes) {
    var provider = SkillsMod.getInstance();
    for (String node : nodes) {
      provider.tryUnlockSkill(player, category, node, false);
      require(
          provider.getSkillState(player, category, node).orElseThrow() == Skill.State.UNLOCKED,
          "Purchase " + node);
    }
  }

  private static void require(boolean condition, String description) {
    if (!condition) throw new IllegalStateException(description);
  }
}
