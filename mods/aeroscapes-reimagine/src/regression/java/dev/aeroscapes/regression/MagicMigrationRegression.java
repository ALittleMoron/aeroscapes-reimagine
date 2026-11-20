package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.puffish.skillsmod.SkillsMod;

/**
 * The same native fixture runs against the old pack, its upgrade and the untouched rollback copy.
 */
public final class MagicMigrationRegression {
  private static final UUID PLAYER = UUID.fromString("acc770d9-b1d7-4861-a6f5-23a6d54de611");
  private static final String NAME = "MagicMigration";
  private static final String ABILITIES = "aeroscapes_reimagine_abilities";
  private static final String PRACTICE = "aeroscapes_reimagine_magic_practice";
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse("aeroscapes_reimagine:combat");
  private static final ResourceLocation PROFESSION =
      ResourceLocation.parse("aeroscapes_reimagine:profession");
  private static final ResourceLocation SOURCE =
      ResourceLocation.parse("aeroscapes_reimagine:skill_levels");
  private static final ResourceLocation BANK =
      ResourceLocation.parse("aeroscapes_reimagine:migration_fixture");
  private static final Set<String> LEGACY_MAGIC =
      Set.of(
          "magic_reserve",
          "magic_fire_depth_1",
          "magic_fire_depth_2",
          "magic_fire_depth_3",
          "magic_fire_depth_4",
          "magic_spell_firebolt");
  private static final List<String> LAYOUT_PURCHASES =
      List.of(
          "melee_strike",
          "magic_mastery_1",
          "magic_destruction_power",
          "magic_destruction_mastery_2",
          "magic_reserve_1",
          "magic_destruction_mastery_3",
          "magic_destruction_cooldown",
          "magic_spell_fireball");

  private MagicMigrationRegression() {}

  public static void start(ServerLevel level, String mode) {
    require(
        Set.of(
                "prepare",
                "verify",
                "rollback",
                "race-prepare",
                "race-verify",
                "race-rollback",
                "class-verify",
                "class-reload",
                "profession-prepare",
                "profession-verify",
                "profession-reload",
                "profession-rollback",
                "layout-prepare",
                "layout-verify",
                "layout-rollback")
            .contains(mode),
        "Known migration fixture mode");
    var listener =
        new Consumer<ServerTickEvent.Post>() {
          @Override
          public void accept(ServerTickEvent.Post event) {
            if (event.getServer() != level.getServer()) return;
            NeoForge.EVENT_BUS.unregister(this);
            try {
              run(level, mode);
            } catch (IOException exception) {
              throw new IllegalStateException(
                  "Cannot persist migration fixture " + mode, exception);
            }
          }
        };
    NeoForge.EVENT_BUS.addListener(listener);
  }

  private static void run(ServerLevel level, String mode) throws IOException {
    level.getServer().getPlayerList().reloadResources();
    String configured = System.getProperty("aeroscapes.regression.magicMigrationPlayer");
    Path path =
        configured == null
            ? level
                .getServer()
                .getWorldPath(LevelResource.ROOT)
                .resolve("magic-migration-player.dat")
            : Path.of(configured).toAbsolutePath().normalize();
    CompoundTag saved = null;
    if (mode.equals("prepare") || mode.endsWith("-prepare")) {
      require(!Files.exists(path), "Prepare requires a fresh fixture player snapshot: " + path);
    } else {
      require(Files.isRegularFile(path), "The previous process saved its fixture player: " + path);
      saved = NbtIo.readCompressed(path, NbtAccounter.create(16L * 1024 * 1024));
    }
    Vec3 position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    try (var session = TestShipPlayerSession.create(level, PLAYER, NAME, position, saved)) {
      ServerPlayer player = session.player;
      listPlayer(level, player, true);
      try {
        MagicData.getPlayerMagicData(player).setServerPlayer(player);
        MagicData.getPlayerMagicData(player).getSyncedData();
        boolean alreadyMigrated =
            player.getPersistentData().getCompound(PRACTICE).getBoolean("initialized");
        if (mode.equals("rollback"))
          require(!alreadyMigrated, "Rollback must use a copy of the original prepare save");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        switch (mode) {
          case "prepare" -> prepare(player);
          case "verify" -> verifyUpgrade(player, alreadyMigrated);
          case "rollback" -> verifyLegacy(player);
          case "race-prepare" -> {
            MagicPractice.award(player, 700);
            SkillsMod.getInstance().unlockSkill(player, COMBAT, "magic_mastery_1");
            SkillsMod.getInstance().unlockSkill(player, COMBAT, "magic_destruction_mastery_2");
            SkillsMod.getInstance().unlockSkill(player, COMBAT, "magic_destruction_mastery_3");
            prepareFirebolt(player);
            verifyRaceCarryover(player);
          }
          case "race-verify" -> {
            verifyRaceCarryover(player);
            require(
                dev.aeroscapes.races.RaceIdentity.race(player) == null,
                "Upgrading a 0.6.2 character must leave the race choice open");
            require(
                dev.aeroscapes.races.RaceIdentity.choose(
                    player,
                    dev.aeroscapes.races.Race.GOBLIN,
                    1,
                    CharacterClass.WARRIOR,
                    "quick_block",
                    Profession.BLACKSMITH,
                    "material_repair"),
                "A returning player can choose a race and appearance once");
            verifyRaceCarryover(player);
          }
          case "race-rollback" -> verifyRaceCarryover(player);
          case "class-verify", "class-reload" ->
              verifyClassCarryover(player, mode.equals("class-verify"));
          case "profession-prepare" -> {
            prepareProfessionBaseline(player);
            verifyProfessionCarryover(player, false);
          }
          case "profession-verify" -> {
            verifyProfessionCarryover(player, false);
            ProfessionIdentityRegression.upgrade(player);
            verifyProfessionCarryover(player, true);
          }
          case "profession-reload" -> verifyProfessionCarryover(player, true);
          case "profession-rollback" -> verifyProfessionCarryover(player, false);
          case "layout-prepare" -> {
            prepareLayout(player);
            verifyLayout(player, true);
          }
          default -> verifyLayout(player, mode.equals("layout-rollback"));
        }
        level.getServer().getPlayerList().saveAll();
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        NbtIo.writeCompressed(player.saveWithoutId(new CompoundTag()), path);
        level.getServer().saveAllChunks(true, true, true);
        LogUtils.getLogger()
            .info("Magic migration {} regression passed: player={}, saved={}", mode, PLAYER, path);
      } finally {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        listPlayer(level, player, false);
      }
    }
  }

  private static void prepare(ServerPlayer player) {
    require(
        !player.getPersistentData().contains(PRACTICE, Tag.TAG_COMPOUND),
        "Prepare runs against the original pack without the new practice adapter");
    APIUtils.setLevel("magic", player, 20);
    APIUtils.setXp("magic", player, 0);
    APIUtils.setLevel("combat", player, 10);
    APIUtils.setLevel("mining", player, 10);
    ProgressionBridge.sync(player);
    var provider = SkillsMod.getInstance();
    require(
        provider.getPoints(player, COMBAT, SOURCE).orElseThrow() == 6,
        "The original bridge has its real six earned combat points");
    provider.setPoints(player, COMBAT, BANK, 10, true);
    provider.setPoints(player, PROFESSION, BANK, 10, true);
    for (String id : LEGACY_MAGIC) {
      require(provider.hasSkill(COMBAT, id), "The original school node exists: " + id);
      provider.unlockSkill(player, COMBAT, id);
    }
    provider.unlockSkill(player, COMBAT, "melee_strike");
    provider.unlockSkill(player, PROFESSION, "mining_efficiency");
    prepareFirebolt(player);
    verifyLegacy(player);
  }

  private static void prepareFirebolt(ServerPlayer player) {
    CompoundTag abilities = player.getPersistentData().getCompound(ABILITIES);
    CompoundTag ranks = abilities.getCompound("ranks");
    ranks.putInt("irons_spellbooks:firebolt", 3);
    abilities.put("ranks", ranks);
    player.getPersistentData().put(ABILITIES, abilities);
    Utils.setPlayerSpellbookStack(player, new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get()));
    PreparedAbilities.edit(player, 3, "irons_spellbooks:firebolt");
    PreparedAbilities.select(player, 3);
  }

  private static void verifyRaceCarryover(ServerPlayer player) {
    require(MagicPractice.credit(player) == 700, "Race selection changed existing practice");
    require(
        SkillsMod.getInstance()
            .getUnlockedSkills(player, COMBAT)
            .orElseThrow()
            .contains("magic_mastery_1"),
        "Race selection changed the existing mastery");
    verifyRanksAndSlots(player);
  }

  private static void verifyClassCarryover(ServerPlayer player, boolean firstLogin) {
    var goblin = dev.aeroscapes.races.Race.GOBLIN;
    require(
        dev.aeroscapes.races.RaceIdentity.race(player) == goblin
            && dev.aeroscapes.races.RaceIdentity.style(player) == 1,
        "The confirmed 0.6.3 race and appearance must survive the update");
    verifyRaceCarryover(player);
    if (firstLogin) {
      require(
          dev.aeroscapes.races.RaceIdentity.awaitingChoice(player),
          "A returning race-only character must choose their class");
      require(
          !dev.aeroscapes.races.RaceIdentity.choose(
              player,
              goblin,
              0,
              CharacterClass.MAGE,
              "first_mastery",
              Profession.BLACKSMITH,
              "material_repair"),
          "Class selection changed a locked 0.6.3 appearance");
      require(
          !dev.aeroscapes.races.RaceIdentity.choose(
              player,
              dev.aeroscapes.races.Race.ELF,
              1,
              CharacterClass.MAGE,
              "first_mastery",
              Profession.BLACKSMITH,
              "material_repair"),
          "Class selection changed a locked 0.6.3 race");
      require(
          dev.aeroscapes.races.RaceIdentity.choose(
              player,
              goblin,
              1,
              CharacterClass.MAGE,
              "first_mastery",
              Profession.BLACKSMITH,
              "material_repair"),
          "A returning character could not complete the missing class");
    }
    require(
        !dev.aeroscapes.races.RaceIdentity.awaitingChoice(player)
            && dev.aeroscapes.races.RaceIdentity.characterClass(player) == CharacterClass.MAGE
            && dev.aeroscapes.races.RaceIdentity.starter(player).equals("first_mastery"),
        "Class confirmation must survive login and end protection");
    require(
        !dev.aeroscapes.races.RaceIdentity.choose(
            player,
            goblin,
            1,
            CharacterClass.WARRIOR,
            "quick_block",
            Profession.BLACKSMITH,
            "material_repair"),
        "A completed character could reselect through the sheet");
    verifyRaceCarryover(player);
  }

  private static void prepareLayout(ServerPlayer player) {
    MagicPractice.award(player, 8401);
    APIUtils.setLevel("combat", player, 10);
    APIUtils.setLevel("mining", player, 25);
    ProgressionBridge.sync(player);
    var provider = SkillsMod.getInstance();
    provider.setPoints(player, PROFESSION, BANK, 10, true);
    provider.tryUnlockSkill(player, PROFESSION, "mining_efficiency", false);
    for (String id : LAYOUT_PURCHASES) {
      provider.tryUnlockSkill(player, COMBAT, id, false);
      require(
          provider.getUnlockedSkills(player, COMBAT).orElseThrow().contains(id),
          "Purchasable on the previous four-direction layout: " + id);
    }
    prepareFirebolt(player);
  }

  private static void prepareProfessionBaseline(ServerPlayer player) {
    // This phase runs inside the 0.6.4 JVM with its original five-argument creation API.
    try {
      var choose =
          dev.aeroscapes.races.RaceIdentity.class.getMethod(
              "choose",
              ServerPlayer.class,
              dev.aeroscapes.races.Race.class,
              int.class,
              CharacterClass.class,
              String.class);
      require(
          Boolean.TRUE.equals(
              choose.invoke(
                  null,
                  player,
                  dev.aeroscapes.races.Race.GOBLIN,
                  1,
                  CharacterClass.MAGE,
                  "first_mastery")),
          "Create the baseline 0.6.4 mage");
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Baseline requires the 0.6.4 owned mod", exception);
    }
    APIUtils.setLevel("mining", player, 10);
    ProgressionBridge.sync(player);
    var skills = SkillsMod.getInstance();
    skills.tryUnlockSkill(player, PROFESSION, "mining_efficiency", false);
    skills.tryUnlockSkill(player, PROFESSION, "mining_speed", false);
    MagicPractice.award(player, 700);
    skills.unlockSkill(player, COMBAT, "magic_destruction_mastery_2");
    skills.unlockSkill(player, COMBAT, "magic_destruction_mastery_3");
    prepareFirebolt(player);
  }

  private static void verifyProfessionCarryover(ServerPlayer player, boolean updated) {
    require(
        dev.aeroscapes.races.RaceIdentity.race(player) == dev.aeroscapes.races.Race.GOBLIN
            && dev.aeroscapes.races.RaceIdentity.style(player) == 1
            && dev.aeroscapes.races.RaceIdentity.characterClass(player) == CharacterClass.MAGE
            && dev.aeroscapes.races.RaceIdentity.starter(player).equals("first_mastery"),
        "Original race, appearance, class and starter retained");
    // A mage affinity also applied when the original character earned its 700 practice XP.
    require(
        MagicPractice.credit(player) == 840 && APIUtils.getLevel("mining", player) == 10,
        "Preserve original magical and profession practice");
    verifyRanksAndSlots(player);
    var skills = SkillsMod.getInstance();
    require(
        Utils.getPlayerSpellbookStack(player).is(ItemRegistry.WIMPY_SPELL_BOOK.get())
            && player.getInventory().countItem(ItemRegistry.COPPER_SPELL_BOOK.get()) == 0
            && skills
                .getUnlockedSkills(player, COMBAT)
                .orElseThrow()
                .containsAll(
                    List.of(
                        "magic_mastery_1",
                        "magic_destruction_mastery_2",
                        "magic_destruction_mastery_3")),
        "Preserve the existing focus and mastery without replaying the mage journal");
    Set<String> expected =
        updated
            ? Set.of("mining_efficiency", "mining_speed", "material_repair")
            : Set.of("mining_efficiency", "mining_speed");
    require(
        Set.copyOf(skills.getUnlockedSkills(player, PROFESSION).orElseThrow()).equals(expected)
            && skills.getPointsLeft(player, PROFESSION).orElseThrow() == 0,
        "Original paid purchases retained, funded starter consumes its own point");
    require(
        player.getInventory().countItem(Items.STONE_PICKAXE) == (updated ? 1 : 0)
            && player.getInventory().countItem(Items.COAL) == (updated ? 4 : 0),
        "Exactly one profession kit in the upgraded save, none in the original");
    if (updated) ProfessionIdentityRegression.verifyUpgraded(player);
  }

  private static void verifyLayout(ServerPlayer player, boolean previousBuild) {
    ProgressionBridge.sync(player);
    boolean expandedProfessions = SkillsMod.getInstance().hasSkill(PROFESSION, "material_repair");
    verifyUnrelatedProgress(player, 25, previousBuild || expandedProfessions ? 5 : 2);
    verifyRanksAndSlots(player);
    var provider = SkillsMod.getInstance();
    require(MagicPractice.credit(player) == 8401, "Layout keeps previously initialized practice");
    var firebolt = SpellKnowledge.spell("irons_spellbooks:firebolt");
    Set<String> skills = SpellKnowledge.unlocked(player);
    require(
        SpellKnowledge.rank(player, firebolt, skills) == 3
            && SpellKnowledge.knowledgeReason(player, firebolt, skills).isEmpty(),
        "The prepared rank stays usable with the grandfathered mastery path");
    require(
        Set.copyOf(provider.getUnlockedSkills(player, COMBAT).orElseThrow())
            .equals(Set.copyOf(LAYOUT_PURCHASES)),
        "Exactly the previous purchases survive the new prerequisite paths");
    require(
        provider.getPoints(player, COMBAT, SOURCE).orElseThrow() == 19
            && provider.getSpentPoints(player, COMBAT).orElseThrow() == LAYOUT_PURCHASES.size()
            && provider.getPointsLeft(player, COMBAT).orElseThrow() == 19 - LAYOUT_PURCHASES.size(),
        "Moving owned talents preserves earned, spent and available combat points");
  }

  private static void verifyLegacy(ServerPlayer player) {
    ProgressionBridge.sync(player);
    verifyUnrelatedProgress(player, 10, 2);
    var provider = SkillsMod.getInstance();
    Set<String> actual = Set.copyOf(provider.getUnlockedSkills(player, COMBAT).orElseThrow());
    require(
        actual.containsAll(LEGACY_MAGIC) && actual.contains("melee_strike"),
        "Native saved talent allocations retain every original magical and physical choice");
    require(
        provider.getPoints(player, COMBAT, SOURCE).orElseThrow() == 6,
        "The baseline bridge retains the original earned source after process restart");
    verifyRanksAndSlots(player);
    require(
        !player.getPersistentData().contains(PRACTICE, Tag.TAG_COMPOUND),
        "The original rollback save never contains converted practice credit");
  }

  private static void verifyUpgrade(ServerPlayer player, boolean alreadyMigrated) {
    ProgressionBridge.sync(player);
    verifyUnrelatedProgress(player, 10, 2);
    verifyRanksAndSlots(player);
    var provider = SkillsMod.getInstance();
    Set<String> actual = Set.copyOf(provider.getUnlockedSkills(player, COMBAT).orElseThrow());
    require(
        actual.stream().noneMatch(LEGACY_MAGIC::contains) && actual.contains("melee_strike"),
        "The upgrade removes only retired magical allocations from native saved player data");
    CompoundTag practice = player.getPersistentData().getCompound(PRACTICE);
    require(
        practice.getBoolean("initialized") && practice.getLong("credit") == 8401,
        "The migration preserves readiness 20 as equivalent credit on the new curve");
    require(
        provider.getPoints(player, COMBAT, SOURCE).orElseThrow() == 19,
        "The new source contains seventeen magic rewards and two physical points");
    require(
        provider.getPoints(player, COMBAT, BANK).orElseThrow() == 10,
        "Migration preserves the separate legitimate combat point source");
    if (!alreadyMigrated) {
      require(
          provider.getSpentPoints(player, COMBAT).orElseThrow() == 1,
          "Retired allocations refund through their removed spent cost");
      provider.tryUnlockSkill(player, COMBAT, "magic_mastery_1", false);
    }
    require(
        provider.getUnlockedSkills(player, COMBAT).orElseThrow().contains("magic_mastery_1"),
        "The new root is purchasable and survives the next process restart");
    ProgressionBridge.sync(player);
    require(
        provider.getUnlockedSkills(player, COMBAT).orElseThrow().contains("magic_mastery_1")
            && player.getPersistentData().getCompound(PRACTICE).getLong("credit") == 8401,
        "Migration and reward reconciliation do not run twice");
  }

  private static void verifyUnrelatedProgress(
      ServerPlayer player, int miningLevel, int professionPoints) {
    require(
        APIUtils.getLevel("magic", player) == 20
            && APIUtils.getLevel("combat", player) == 10
            && APIUtils.getLevel("mining", player) == miningLevel,
        "Native saved practice levels survive upgrade and rollback");
    var provider = SkillsMod.getInstance();
    require(
        provider.getUnlockedSkills(player, PROFESSION).orElseThrow().contains("mining_efficiency")
            && provider.getPoints(player, PROFESSION, SOURCE).orElseThrow() == professionPoints
            && provider.getPoints(player, PROFESSION, BANK).orElseThrow() == 10,
        "Native profession allocation and both profession point sources remain intact");
  }

  private static void verifyRanksAndSlots(ServerPlayer player) {
    require(
        player
                .getPersistentData()
                .getCompound(ABILITIES)
                .getCompound("ranks")
                .getInt("irons_spellbooks:firebolt")
            == 3,
        "The original studied Firebolt rank survives the process transition");
    var prepared = PreparedAbilities.preparation(player);
    require(
        prepared.selected() == 3
            && prepared.slots().get(3).equals("irons_spellbooks:firebolt")
            && prepared.slots().stream().filter(id -> !id.isEmpty()).count() == 1,
        "The original prepared position and selected slot survive the process transition");
  }

  @SuppressWarnings("unchecked")
  private static void listPlayer(ServerLevel level, ServerPlayer player, boolean listed) {
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
      throw new IllegalStateException("Cannot list the migration fixture player", exception);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
