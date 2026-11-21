package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.magic.Preparation;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.ProgressionBridge;
import harmonised.pmmo.api.APIUtils;
import harmonised.pmmo.core.Core;
import harmonised.pmmo.storage.PmmoSavedData;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.LogicalSide;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.puffish.skillsmod.SkillsMod;

/** Native purchases, persisted practice and migration run together on the dedicated server. */
public final class MagicRebalanceRegression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final ResourceLocation MAGIC =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);
  private static final ResourceLocation PROFESSION =
      ResourceLocation.parse(ProgressionBridge.PROFESSION_CATEGORY);
  private static final ResourceLocation SOURCE =
      ResourceLocation.parse("aeroscapes_reimagine:skill_levels");
  private static final ResourceLocation FIXTURE =
      ResourceLocation.parse("aeroscapes_reimagine:magic_fixture");
  private static final String ROOT = "magic_mastery_1";
  private static final String ABILITIES = "aeroscapes_reimagine_abilities";
  private static final String PRACTICE = "aeroscapes_reimagine_magic_practice";

  private MagicRebalanceRegression() {}

  public static void start(ServerLevel level) {
    var listener =
        new Consumer<ServerTickEvent.Post>() {
          @Override
          public void accept(ServerTickEvent.Post event) {
            if (event.getServer() != level.getServer()) return;
            NeoForge.EVENT_BUS.unregister(this);
            level.getServer().getPlayerList().reloadResources();
            verifyGates(level);
            verifyIndependentBudgets(level);
            verifyPostCapPractice(level);
            verifyLegacyRebalance(level);
            verifySplitMigration(level);
            HusbandryLootRegression.verify(level);
            GuardianAidRegression.verify(level);
            BindingPactRegression.verify(level);
            MagicBonusesRegression.verify(level);
            AbilitiesRegression.verify(level);
            ProgressionRegression.verify(level);
            LogUtils.getLogger().info("Magic rebalance regression passed");
          }
        };
    NeoForge.EVENT_BUS.addListener(listener);
  }

  private static void verifyGates(ServerLevel level) {
    try (var session = session(level, "MagicGateFixture")) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        login(player);
        var provider = SkillsMod.getInstance();
        require(
            provider.getPointsLeft(player, MAGIC).orElseThrow() == 1, "An immediate root point");
        provider.tryUnlockSkill(player, MAGIC, ROOT, false);
        require(
            SpellKnowledge.spells().stream()
                    .filter(spell -> MagicTalents.learned(spell.getSpellId(), unlocked(player)))
                    .count()
                == 8,
            "One actual introductory purchase unlocks eight spells across native schools");
        provider.setPoints(player, MAGIC, FIXTURE, MagicTalents.pointCap(), true);
        int[] levels = {5, 15, 30, 45};
        for (String direction :
            List.of("destruction", "restoration", "conjuration", "transformation")) {
          for (int tier = 2; tier <= 5; tier++) {
            String node = "magic_" + direction + "_mastery_" + tier;
            require(MagicTalents.requiredLevel(node) == levels[tier - 2], "Readiness for " + node);
            Set<String> prerequisites = new HashSet<>(MagicTalents.nodes());
            prerequisites.remove(node);
            allocations(player, prerequisites);
            provider.tryUnlockSkill(player, MAGIC, node, false);
            require(
                !unlocked(player).contains(node) && MagicPractice.level(player) == 0,
                "A forged server purchase with points and every predecessor cannot skip " + node);
          }
        }
        allocations(player, Set.of());
        MagicPractice.award(player, 501);
        String mastery = "magic_destruction_mastery_2";
        provider.tryUnlockSkill(player, MAGIC, mastery, false);
        require(!unlocked(player).contains(mastery), "Relevant practice cannot replace the path");
        provider.tryUnlockSkill(player, MAGIC, ROOT, false);
        provider.tryUnlockSkill(player, MAGIC, mastery, false);
        require(
            !unlocked(player).contains(mastery), "Mastery requires a useful intermediate talent");
        provider.tryUnlockSkill(player, MAGIC, "magic_destruction_power", false);
        provider.tryUnlockSkill(player, MAGIC, mastery, false);
        require(
            unlocked(player).contains(mastery), "Practice and the real native path permit mastery");
        MagicPractice.award(player, 3800);
        provider.tryUnlockSkill(player, MAGIC, "magic_destruction_mana", false);
        provider.tryUnlockSkill(player, MAGIC, "magic_destruction_mastery_3", false);
        require(
            !unlocked(player).contains("magic_destruction_mastery_3"),
            "The second intermediate improvement cannot be skipped");
        provider.tryUnlockSkill(player, MAGIC, "magic_reserve_1", false);
        provider.tryUnlockSkill(player, MAGIC, "magic_destruction_mastery_3", false);
        require(
            unlocked(player).contains("magic_destruction_mastery_3"),
            "Two learned improvements open Destruction III");
        Set<String> earlierAllocation = Set.of(ROOT, "magic_reserve_1");
        allocations(player, earlierAllocation);
        provider.tryUnlockSkill(player, MAGIC, "magic_destruction_mastery_3", false);
        require(
            MagicPractice.level(player) == 15 && unlocked(player).equals(earlierAllocation),
            "A retained Mana Reserve cannot skip Destruction II or lose its existing allocation");
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void verifyIndependentBudgets(ServerLevel level) {
    try (var session = session(level, "MagicPoolFixture")) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        login(player);
        APIUtils.setLevel("combat", player, 50);
        APIUtils.setLevel("archery", player, 50);
        ProgressionBridge.sync(player);
        var provider = SkillsMod.getInstance();
        require(
            provider.getPoints(player, COMBAT, SOURCE).orElseThrow()
                    == ProgressionBridge.COMBAT_POINT_CAP
                && earned(player) == 1,
            "Both weapon skills at 50 fill combat without funding magic");
        long finalMagicXp = MagicPracticeProgress.xpForPoints(MagicTalents.pointCap());
        MagicPractice.award(player, finalMagicXp - 1);
        ProgressionBridge.sync(player);
        require(
            earned(player) == MagicTalents.pointCap() - 1
                && ProgressionBridge.nextMagicPointXp(player, MagicPractice.credit(player))
                    == finalMagicXp,
            "The last magic point still requires its full practice threshold");
        MagicPractice.award(player, 1);
        ProgressionBridge.sync(player);
        require(
            earned(player) == MagicTalents.pointCap()
                && ProgressionBridge.nextMagicPointXp(player, MagicPractice.credit(player)) == 0
                && provider.getPoints(player, COMBAT, SOURCE).orElseThrow()
                    == ProgressionBridge.COMBAT_POINT_CAP,
            "Finishing magic practice leaves the full combat budget intact");
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void verifyPostCapPractice(ServerLevel level) {
    UUID identity = UUID.randomUUID();
    Vec3 position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(8, 32, 0);
    CompoundTag saved;
    CompoundTag talents = new CompoundTag();
    try (var session =
        TestShipPlayerSession.create(level, identity, "MagicCapFixture", position, null)) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        login(player);
        MagicPractice.award(player, 72701);
        ProgressionBridge.sync(player);
        require(
            MagicPractice.level(player) == 50
                && APIUtils.getLevel("magic", player) == 50
                && earned(player) == 42,
            "Readiness 50 leaves most of the complete allocation to later practice");
        MagicPractice.award(player, 100);
        require(
            MagicPractice.credit(player) == 72801 && MagicPractice.level(player) == 50,
            "Actual practice continues to earn credit beyond readiness 50");
        MagicPractice.award(player, Long.MAX_VALUE);
        ProgressionBridge.sync(player);
        require(
            MagicPractice.credit(player)
                    == MagicPracticeProgress.xpForPoints(MagicTalents.pointCap())
                && earned(player) == MagicTalents.pointCap(),
            "Magic practice funds the complete finite magic allocation without overflow");
        require(
            APIUtils.getLevel("combat", player) == 0 && APIUtils.getLevel("archery", player) == 0,
            "A complete mage never requires unrelated weapon practice");
        require(
            APIUtils.getRawXpMap(player).get("magic").getLevel().getLevel() == 50,
            "Post-cap credit does not manufacture raw native level 51");
        long credit = MagicPractice.credit(player);
        for (int attempt = 0; attempt < 3; attempt++) {
          MagicPractice.award(player, 0);
          MagicPractice.award(player, -10);
          MagicPractice.synchronize(player);
          ProgressionBridge.sync(player);
        }
        require(
            MagicPractice.credit(player) == credit && earned(player) == MagicTalents.pointCap(),
            "Repeated sync and empty practice do not duplicate final rewards");
        buyCompleteMagic(player);
        var provider = SkillsMod.getInstance();
        require(
            provider.getSpentPoints(player, MAGIC).orElseThrow() == MagicTalents.totalCost()
                && provider.getPointsLeft(player, MAGIC).orElseThrow() == 0
                && provider.getPointsTotal(player, COMBAT).orElseThrow() == 0,
            "The entire magic tree is purchasable without earning a combat point");
        APIUtils.setLevel("combat", player, 50);
        APIUtils.setLevel("archery", player, 50);
        APIUtils.setLevel("mining", player, 10);
        ProgressionBridge.sync(player);
        require(
            earned(player) == MagicTalents.pointCap()
                && provider.getPoints(player, COMBAT, SOURCE).orElseThrow()
                    == ProgressionBridge.COMBAT_POINT_CAP
                && provider.getPoints(player, PROFESSION, SOURCE).orElseThrow() == 2,
            "Weapon practice and mining fund their own budgets");
        for (String id :
            List.of(
                "melee_strike",
                "melee_onslaught",
                "melee_veteran",
                "guard_armor",
                "guard_vitality",
                "quick_block",
                "shield_march",
                "bow_stride",
                "steady_aim",
                "quick_recovery",
                "opening_strike")) {
          provider.tryUnlockSkill(player, COMBAT, id, false);
        }
        for (String id : List.of("mining_efficiency", "mining_fortune")) {
          provider.tryUnlockSkill(player, PROFESSION, id, false);
        }
        require(
            provider.getPointsLeft(player, MAGIC).orElseThrow() == 0
                && provider.getPointsLeft(player, COMBAT).orElseThrow() == 0
                && provider.getPointsLeft(player, PROFESSION).orElseThrow() == 0,
            "A fully trained character can spend every earned point in all three pools");
        verifyDeathClone(player, credit);
        provider.exportPlayerData(player, talents);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        saved = player.saveWithoutId(new CompoundTag());
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    try (var session =
        TestShipPlayerSession.create(level, identity, "MagicCapFixture", position, saved)) {
      ServerPlayer restored = session.player;
      ProgressionRegression.listPlayer(level, restored, true);
      try {
        login(restored);
        SkillsMod.getInstance().importPlayerData(restored, talents);
        ProgressionBridge.sync(restored);
        require(
            MagicPractice.credit(restored)
                    == MagicPracticeProgress.xpForPoints(MagicTalents.pointCap())
                && MagicPractice.level(restored) == 50
                && earned(restored) == MagicTalents.pointCap()
                && unlocked(restored).containsAll(MagicTalents.nodes()),
            "Native player save/load preserves post-cap credit and every learned allocation");
      } finally {
        ProgressionRegression.listPlayer(level, restored, false);
      }
    }
  }

  private static void verifyLegacyRebalance(ServerLevel level) {
    try (var session = session(level, "MagicOldFixture")) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        APIUtils.setLevel("magic", player, 20);
        APIUtils.setLevel("mining", player, 10);
        var provider = SkillsMod.getInstance();
        provider.setPoints(player, COMBAT, SOURCE, 6, true);
        provider.setPoints(player, PROFESSION, SOURCE, 2, true);
        provider.setPoints(player, PROFESSION, FIXTURE, 3, true);
        CompoundTag legacy = new CompoundTag();
        provider.exportPlayerData(player, legacy);
        legacy
            .getCompound("categories")
            .getCompound(ProgressionBridge.COMBAT_CATEGORY)
            .put(
                "unlocked_skills",
                strings(
                    List.of(
                        "melee_strike",
                        "magic_reserve",
                        "magic_fire_depth_4",
                        "magic_spell_firebolt")));
        CompoundTag profession =
            legacy.getCompound("categories").getCompound(ProgressionBridge.PROFESSION_CATEGORY);
        profession.put("unlocked_skills", strings(List.of("mining_efficiency")));
        CompoundTag expectedProfession = profession.copy();
        provider.importPlayerData(player, legacy);
        CompoundTag abilities = new CompoundTag();
        CompoundTag ranks = new CompoundTag();
        ranks.putInt("irons_spellbooks:firebolt", 3);
        ranks.putInt("irons_spellbooks:eldritch_blast", 2);
        abilities.put("ranks", ranks.copy());
        Preparation prepared = Preparation.empty().assign(3, "irons_spellbooks:firebolt").select(3);
        abilities.put("slots", strings(prepared.slots()));
        abilities.putInt("selected", prepared.selected());
        player.getPersistentData().put(ABILITIES, abilities);
        MagicPractice.synchronize(player);
        ProgressionBridge.sync(player);
        require(
            MagicPractice.level(player) == 20, "Changing the curve retains the old practice level");
        require(
            unlocked(player).isEmpty()
                && Set.copyOf(provider.getUnlockedSkills(player, COMBAT).orElseThrow())
                    .equals(Set.of("melee_strike")),
            "Retired magic is removed and the physical purchase stays in combat");
        require(
            player.getPersistentData().getCompound(ABILITIES).getCompound("ranks").equals(ranks)
                && PreparedAbilities.preparation(player).equals(prepared),
            "Migration retains found ranks, prepared positions and selection");
        CompoundTag migrated = new CompoundTag();
        provider.exportPlayerData(player, migrated);
        require(
            migrated
                .getCompound("categories")
                .getCompound(ProgressionBridge.PROFESSION_CATEGORY)
                .equals(expectedProfession),
            "Migration preserves profession allocations and every profession point source");
        require(
            provider.getSpentPoints(player, COMBAT).orElseThrow() == 1
                && provider.getPointsLeft(player, MAGIC).orElseThrow() == earned(player),
            "Retired magic allocations leave the full practice-funded magic balance available");
        provider.tryUnlockSkill(player, MAGIC, ROOT, false);
        long credit = MagicPractice.credit(player);
        MagicPractice.synchronize(player);
        require(
            unlocked(player).contains(ROOT) && MagicPractice.credit(player) == credit,
            "The migration runs once and never clears newly purchased magic");
        var storage = (PmmoSavedData) Core.get(LogicalSide.SERVER).getData();
        var decoded =
            new PmmoSavedData(
                storage.save(new CompoundTag(), player.registryAccess()), player.registryAccess());
        require(
            decoded.getLevel("magic", player.getUUID()) == 20
                && decoded.getLevel("mining", player.getUUID()) == 10,
            "The native saved-data codec retains migrated magic and unrelated profession levels");
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void verifySplitMigration(ServerLevel level) {
    try (var session = session(level, "MagicSplit")) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        int previousCap = 142;
        long previousCapXp = MagicPracticeProgress.xpForPoints(previousCap);
        APIUtils.setLevel("magic", player, 50);
        APIUtils.setXp("magic", player, 1);
        APIUtils.setLevel("combat", player, 10);
        APIUtils.setLevel("mining", player, 10);
        CompoundTag practice = new CompoundTag();
        practice.putBoolean("initialized", true);
        practice.putLong("credit", previousCapXp);
        practice.putInt("native_level", 50);
        practice.putLong("native_xp", 1);
        player.getPersistentData().put(PRACTICE, practice);

        var provider = SkillsMod.getInstance();
        provider.setPoints(player, COMBAT, SOURCE, previousCap, true);
        provider.setPoints(player, COMBAT, FIXTURE, 3, true);
        provider.setPoints(player, PROFESSION, SOURCE, 2, true);
        CompoundTag snapshot = new CompoundTag();
        provider.exportPlayerData(player, snapshot);
        CompoundTag categories = snapshot.getCompound("categories");
        Set<String> learned =
            Set.of(ROOT, "magic_destruction_power", "magic_destruction_mastery_2");
        var mixed = new ArrayList<>(learned.stream().sorted().toList());
        mixed.add("melee_strike");
        categories
            .getCompound(ProgressionBridge.COMBAT_CATEGORY)
            .put("unlocked_skills", strings(mixed));
        categories
            .getCompound(ProgressionBridge.PROFESSION_CATEGORY)
            .put("unlocked_skills", strings(List.of("mining_efficiency")));
        provider.importPlayerData(player, snapshot);

        login(player);
        MagicPractice.award(player, 1);
        ProgressionBridge.sync(player);
        require(
            MagicPractice.credit(player) == previousCapXp
                && earned(player) == MagicTalents.pointCap()
                && unlocked(player).equals(learned)
                && provider.getSpentPoints(player, MAGIC).orElseThrow() == learned.size(),
            "The split preserves existing 0.7.0 magic purchases and previously banked practice");
        require(
            Set.copyOf(provider.getUnlockedSkills(player, COMBAT).orElseThrow())
                    .equals(Set.of("melee_strike"))
                && provider.getPoints(player, COMBAT, SOURCE).orElseThrow() == 2
                && provider.getPoints(player, COMBAT, FIXTURE).orElseThrow() == 3
                && Set.copyOf(provider.getUnlockedSkills(player, PROFESSION).orElseThrow())
                    .equals(Set.of("mining_efficiency"))
                && provider.getPoints(player, PROFESSION, SOURCE).orElseThrow() == 2,
            "Combat and profession retain their own purchases and independent point sources");
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void buyCompleteMagic(ServerPlayer player) {
    var provider = SkillsMod.getInstance();
    var remaining = new HashSet<>(MagicTalents.nodes());
    for (int pass = 0; pass < MagicTalents.nodes().size() && !remaining.isEmpty(); pass++) {
      int previous = remaining.size();
      for (String node : new ArrayList<>(remaining)) {
        provider.tryUnlockSkill(player, MAGIC, node, false);
        if (unlocked(player).contains(node)) remaining.remove(node);
      }
      require(
          remaining.size() < previous, "A fully trained mage can reach native paths: " + remaining);
    }
    require(remaining.isEmpty(), "Every magical purchase has a reachable prerequisite path");
    for (var spell : SpellKnowledge.spells())
      require(
          MagicTalents.learned(spell.getSpellId(), unlocked(player)),
          "A complete allocation covers the actual spell registry: " + spell.getSpellId());
  }

  private static void verifyDeathClone(ServerPlayer original, long credit) {
    var replacement =
        new ServerPlayer(
            original.server,
            original.serverLevel(),
            original.getGameProfile(),
            original.clientInformation());
    replacement.connection = original.connection;
    try {
      replacement.restoreFrom(original, false);
      require(
          MagicPractice.credit(replacement) == credit && MagicPractice.level(replacement) == 50,
          "Native death cloning retains the entire post-cap practice balance");
    } finally {
      replacement.getTextFilter().leave();
      replacement.getAdvancements().stopListening();
    }
  }

  private static TestShipPlayerSession session(ServerLevel level, String name) {
    return TestShipPlayerSession.create(
        level,
        UUID.randomUUID(),
        name,
        Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0),
        null);
  }

  private static void login(ServerPlayer player) {
    MagicData.getPlayerMagicData(player).setServerPlayer(player);
    MagicData.getPlayerMagicData(player).getSyncedData();
    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
    ProgressionBridge.sync(player);
  }

  private static void allocations(ServerPlayer player, Set<String> nodes) {
    var provider = SkillsMod.getInstance();
    CompoundTag snapshot = new CompoundTag();
    provider.exportPlayerData(player, snapshot);
    snapshot
        .getCompound("categories")
        .getCompound(ProgressionBridge.MAGIC_CATEGORY)
        .put("unlocked_skills", strings(nodes.stream().sorted().toList()));
    provider.importPlayerData(player, snapshot);
  }

  private static ListTag strings(List<String> values) {
    ListTag result = new ListTag();
    for (String value : values) result.add(StringTag.valueOf(value));
    return result;
  }

  private static Set<String> unlocked(ServerPlayer player) {
    return new HashSet<>(SkillsMod.getInstance().getUnlockedSkills(player, MAGIC).orElseThrow());
  }

  private static int earned(ServerPlayer player) {
    return SkillsMod.getInstance().getPoints(player, MAGIC, SOURCE).orElseThrow();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
