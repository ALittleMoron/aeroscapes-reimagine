package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
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
  private static final ResourceLocation PROFESSION =
      ResourceLocation.parse(ProgressionBridge.PROFESSION_CATEGORY);

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
    int cap = MagicTalents.pointCap();
    MagicPractice.award(player, MagicPracticeProgress.xpForPoints(cap));
    APIUtils.setLevel("mining", player, 45);
    ProgressionBridge.sync(player);
    require(skills.getPointsTotal(player, COMBAT).orElseThrow() == cap, "Full combat budget");
    purchase(
        player,
        COMBAT,
        List.of(
            "guard_armor",
            "guard_vitality",
            "magic_mastery_1",
            "magic_destruction_power",
            "magic_destruction_mastery_2",
            "melee_strike",
            "melee_footing",
            "melee_veteran",
            "bow_stride",
            "steady_aim"));
    purchase(player, PROFESSION, List.of("mining_efficiency", "mining_fortune"));
    require(
        TalentRefunds.preview(player, "combat", List.of("quick_block")).isEmpty()
            && TalentRefunds.preview(player, "profession", List.of("material_repair")).isEmpty(),
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
    var second = TalentRefunds.preview(player, "profession", mining).orElseThrow();
    require(
        second.nodes().equals(List.of("mining_efficiency", "mining_fortune"))
            && second.loss() == 0
            && second.remainder() == 2
            && TalentRefunds.apply(player, mining, second),
        "Separate profession dependency and fractional carry");

    var roots = List.of("magic_mastery_1", "melee_strike", "bow_stride", "steady_aim");
    var last = TalentRefunds.preview(player, "combat", roots).orElseThrow();
    require(
        last.spent() == 8
            && last.loss() == 1
            && last.remainder() == 0
            && TalentRefunds.apply(player, roots, last),
        "Combined ten-point deduction");
    ProgressionBridge.sync(player);
    require(
        skills.getPointsTotal(player, COMBAT).orElseThrow() == cap - 1,
        "Reconciliation preserves the penalty at the earned cap");
  }

  private static void verifyRecoveryAfterLogin(ServerPlayer player) {
    int cap = MagicTalents.pointCap();
    var skills = SkillsMod.getInstance();
    ProgressionBridge.sync(player);
    require(
        skills.getPointsTotal(player, COMBAT).orElseThrow() == cap - 1,
        "Loading the saved player retains the deduction");
    purchase(player, PROFESSION, List.of("mining_efficiency"));
    require(
        TalentRefunds.preview(player, "profession", List.of("mining_efficiency"))
                .orElseThrow()
                .remainder()
            == 3,
        "Save retains the fractional profession fee");
    APIUtils.addXp("cooking", player, 3000);
    MagicPractice.award(player, 2999);
    ProgressionBridge.sync(player);
    require(
        skills.getPointsTotal(player, COMBAT).orElseThrow() == cap - 1
            && ProgressionBridge.nextCombatPointXp(player, MagicPractice.credit(player))
                == MagicPractice.credit(player) + 1,
        "Only new practice in this pool restores the point");
    MagicPractice.award(player, 1);
    require(
        skills.getPointsTotal(player, COMBAT).orElseThrow() == cap
            && ProgressionBridge.nextCombatPointXp(player, MagicPractice.credit(player)) == 0,
        "Capped practice immediately restores the missing point without exceeding the catalogue");
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
