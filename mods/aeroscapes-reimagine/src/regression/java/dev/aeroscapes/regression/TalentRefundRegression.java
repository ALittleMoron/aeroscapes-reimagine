package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.progression.TalentRefunds;
import dev.aeroscapes.races.CharacterChanges;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.api.Skill;

final class TalentRefundRegression {
  private static final ResourceLocation WARRIOR =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final ResourceLocation RANGER =
      ResourceLocation.parse(ProgressionBridge.RANGER_CATEGORY);
  private static final ResourceLocation MAGE =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
  private static final ResourceLocation MINING = ProfessionDiscipline.MINING.category();
  private static final ResourceLocation PICKAXE = ProfessionDiscipline.PICKAXE_HANDLING.category();

  private TalentRefundRegression() {}

  static void verify(ServerLevel level) {
    var position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    var uuid = UUID.randomUUID();
    var providerSave = new CompoundTag();
    CompoundTag savedPlayer;
    try (var session = TestShipPlayerSession.create(level, uuid, "TalentRefund", position, null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        login(player);
        verifyRefund(player);
        SkillsMod.getInstance().exportPlayerData(player, providerSave);
        savedPlayer = player.saveWithoutId(new CompoundTag());
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    try (var session =
        TestShipPlayerSession.create(level, uuid, "TalentRefund", position, savedPlayer)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        SkillsMod.getInstance().importPlayerData(player, providerSave);
        login(player);
        verifyRecoveryAfterLogin(player);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    LogUtils.getLogger()
        .info("All-bank talent reset, overlap, carry and repayment regression passed");
  }

  private static void login(ServerPlayer player) {
    var magic = MagicData.getPlayerMagicData(player);
    magic.setServerPlayer(player);
    magic.getSyncedData();
    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
  }

  private static void verifyRefund(ServerPlayer player) {
    var provider = SkillsMod.getInstance();
    APIUtils.setLevel(ProgressionBridge.WARRIOR_PRACTICE, player, 80);
    APIUtils.setLevel(ProgressionBridge.RANGER_PRACTICE, player, 80);
    APIUtils.setLevel("mining", player, 45);
    APIUtils.setLevel("pickaxe_handling", player, 10);
    MagicPractice.award(player, MagicPracticeProgress.xpForPoints(MagicTalents.pointCap()));
    ProgressionBridge.sync(player);
    buy(player, WARRIOR, "quick_block");
    require(
        RaceIdentity.choose(
            player,
            Race.HUMAN,
            0,
            CharacterClass.WARRIOR,
            "quick_block",
            Profession.BLACKSMITH,
            "material_repair"),
        "A character chooses an already bought Warrior starter");
    require(
        RaceIdentity.identityGrantOverlaps(player)
            .contains(new TalentRefunds.Node("combat", "quick_block")),
        "An identity grant that overlaps a prior purchase retains its paid provenance");

    unlock(
        player,
        WARRIOR,
        List.of(
            "shield_march",
            "melee_strike",
            "melee_onslaught",
            "melee_footing",
            "guard_armor",
            "guard_vitality"));
    unlock(
        player,
        RANGER,
        List.of(
            "bow_stride",
            "steady_aim",
            "quick_recovery",
            "opening_strike",
            "crossbow_handling",
            "starlight_bow_handling",
            "autoloader_handling",
            "light_weapon_handling",
            "light_armor_handling",
            "slowing_shot",
            "marking_shot"));
    buy(player, MAGE, "magic_mastery_1");
    buy(player, MINING, "mining_efficiency");
    buy(player, PICKAXE, "copper");

    long physical = APIUtils.getXp(ProgressionBridge.WARRIOR_PRACTICE, player);
    long magical = MagicPractice.credit(player);
    var quote = CharacterChanges.previewReset(player);
    require(quote != null && quote.talents().pools().size() == 14, "Forecast all fourteen banks");
    var warrior = bank(quote, WARRIOR);
    var ranger = bank(quote, RANGER);
    require(
        warrior.paid() == 7
            && warrior.loss() == 0
            && warrior.remainder() == 7
            && warrior.paidNodes().contains("quick_block"),
        "The overlapping Warrior starter is part of the seven paid costs");
    require(
        ranger.paid() == 11 && ranger.loss() == 1 && ranger.remainder() == 1,
        "Ranger's first whole deduction and fractional carry are independent");
    require(
        bank(quote, MAGE).paid() == 1
            && bank(quote, MINING).paid() == 1
            && bank(quote, PICKAXE).paid() == 1
            && bank(quote, ProfessionDiscipline.SMITHING.category()).paid() == 0,
        "Magic, profession and tool purchases count; free profession starter does not");
    require(
        CharacterChanges.reset(player, quote)
            && !CharacterChanges.reset(player, quote)
            && CharacterChanges.previewReset(player) == null,
        "Reset applies once, rejects the old quote, and offers no empty refund");
    for (var row : quote.talents().pools()) {
      var category = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", row.pool());
      require(
          points(player, category) == row.after(),
          "Quoted and native balances agree in " + row.pool());
    }
    require(
        learned(player, WARRIOR).equals(Set.of("quick_block"))
            && learned(player, ProfessionDiscipline.SMITHING.category())
                .equals(Set.of("material_repair"))
            && learned(player, RANGER).isEmpty()
            && learned(player, MAGE).isEmpty()
            && learned(player, MINING).isEmpty()
            && learned(player, PICKAXE).isEmpty()
            && RaceIdentity.identityGrantOverlaps(player).isEmpty(),
        "Keep applicable grants and remove paid nodes from every bank");
    require(
        APIUtils.getXp(ProgressionBridge.WARRIOR_PRACTICE, player) == physical
            && MagicPractice.credit(player) == magical
            && provider.getSkillState(player, WARRIOR, "quick_block").orElseThrow()
                == Skill.State.UNLOCKED,
        "The reset keeps practice and converts the paid overlap into a free-only grant");
  }

  private static void verifyRecoveryAfterLogin(ServerPlayer player) {
    ProgressionBridge.sync(player);
    unlock(
        player,
        WARRIOR,
        List.of("shield_march", "melee_strike", "melee_onslaught", "melee_footing"));
    buy(player, MINING, "mining_efficiency");
    var quote = CharacterChanges.previewReset(player);
    require(quote != null, "A second purchase requires a new quote");
    var warrior = bank(quote, WARRIOR);
    var ranger = bank(quote, RANGER);
    require(
        warrior.paid() == 4
            && warrior.loss() == 1
            && warrior.remainder() == 1
            && !warrior.paidNodes().contains("quick_block")
            && ranger.paid() == 0
            && ranger.previousDue() == 3000
            && bank(quote, MINING).remainder() == 2,
        "Reload preserved both debt and per-bank fractional carry");
    require(CharacterChanges.reset(player, quote), "Apply the second reset");
    int warriorAfter = points(player, WARRIOR);
    int rangerAfter = points(player, RANGER);
    APIUtils.addXp("cooking", player, 3000);
    APIUtils.addXp(ProgressionBridge.WARRIOR_PRACTICE, player, 2499);
    ProgressionBridge.sync(player);
    require(
        points(player, WARRIOR) == warriorAfter && points(player, RANGER) == rangerAfter,
        "Unrelated practice and 2,998 Warrior practice after affinity leave both deductions");
    APIUtils.addXp(ProgressionBridge.WARRIOR_PRACTICE, player, 1);
    require(
        points(player, WARRIOR) == warriorAfter + 1 && points(player, RANGER) == rangerAfter,
        "Warrior practice cannot settle the Ranger deduction");
    APIUtils.addXp(ProgressionBridge.RANGER_PRACTICE, player, 2999);
    require(
        points(player, RANGER) == rangerAfter,
        "Ranger deduction also needs the full 3,000 practice");
    APIUtils.addXp(ProgressionBridge.RANGER_PRACTICE, player, 1);
    require(
        points(player, RANGER) == rangerAfter + 1, "Fresh Ranger practice restores its own point");
  }

  private static TalentRefunds.PoolQuote bank(
      CharacterChanges.Preview quote, ResourceLocation category) {
    return quote.talents().pools().stream()
        .filter(row -> row.pool().equals(category.getPath()))
        .findFirst()
        .orElseThrow();
  }

  private static Set<String> learned(ServerPlayer player, ResourceLocation category) {
    return Set.copyOf(SkillsMod.getInstance().getUnlockedSkills(player, category).orElseThrow());
  }

  private static int points(ServerPlayer player, ResourceLocation category) {
    return SkillsMod.getInstance().getPointsLeft(player, category).orElseThrow();
  }

  private static void buy(ServerPlayer player, ResourceLocation category, String node) {
    var provider = SkillsMod.getInstance();
    provider.tryUnlockSkill(player, category, node, false);
    require(
        provider.getSkillState(player, category, node).orElseThrow() == Skill.State.UNLOCKED,
        "Buy " + node);
  }

  private static void unlock(ServerPlayer player, ResourceLocation category, List<String> nodes) {
    var provider = SkillsMod.getInstance();
    for (String node : nodes) {
      provider.unlockSkill(player, category, node);
      require(
          provider.getSkillState(player, category, node).orElseThrow() == Skill.State.UNLOCKED,
          "Seed purchased allocation " + node);
    }
  }

  private static void require(boolean condition, String description) {
    if (!condition) throw new IllegalStateException(description);
  }
}
