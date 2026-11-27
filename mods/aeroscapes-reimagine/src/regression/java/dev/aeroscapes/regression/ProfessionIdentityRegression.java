package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.origin.SetOriginResult;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.progression.TalentRefunds;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.ProfessionProgression;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.puffish.skillsmod.SkillsMod;

final class ProfessionIdentityRegression {
  private static final ResourceLocation WITHHELD =
      ResourceLocation.parse("aeroscapes_reimagine:profession_grant_fixture");

  private ProfessionIdentityRegression() {}

  static void verify(ServerLevel level) {
    var position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    for (var profession : Profession.values()) {
      for (var starter : profession.starters()) {
        try (var session =
            TestShipPlayerSession.create(
                level, UUID.randomUUID(), "ProfessionChoice", position, null)) {
          var player = session.player;
          ProgressionRegression.listPlayer(level, player, true);
          try {
            verifyChoice(player, profession, starter);
          } finally {
            ProgressionRegression.listPlayer(level, player, false);
          }
        }
      }
    }
    verifyDeferredKit(level, position);
    LogUtils.getLogger().info("Profession identity regression passed");
  }

  private static void verifyChoice(ServerPlayer player, Profession profession, String starter) {
    login(player);
    var category =
        ProfessionDiscipline.bySkill(profession.practiceSkill()).orElseThrow().category();
    String wrongStarter = profession == Profession.FARMER ? "long_reach" : "extra_harvest";
    require(
        !choose(player, profession, wrongStarter) && RaceIdentity.race(player) == null,
        "Reject foreign profession starter before committing any field");
    boolean previouslyPurchased = starter.equals("frugal_repair");
    if (previouslyPurchased) {
      APIUtils.setLevel("smithing", player, 1);
      ProgressionBridge.sync(player);
      SkillsMod.getInstance().tryUnlockSkill(player, category, starter, false);
      require(ProfessionProgression.learned(player, starter), "Native purchase before selection");
    }
    require(choose(player, profession, starter), "Select " + profession + "/" + starter);
    require(
        RaceIdentity.profession(player) == profession
            && RaceIdentity.professionStarter(player).equals(starter)
            && ProfessionProgression.learned(player, starter)
            && SkillsMod.getInstance().getSpentPoints(player, category).orElseThrow() == 1
            && SkillsMod.getInstance().getPointsLeft(player, category).orElseThrow() == 0
            && RaceIdentity.identityGrantOverlaps(player)
                    .contains(new TalentRefunds.Node(category.getPath(), starter))
                == previouslyPurchased
            && !RaceIdentity.awaitingChoice(player),
        "Confirmed native selection " + starter);
    for (var kitItem : profession.kit())
      require(
          player.getInventory().countItem(kitItem.getItem()) == kitItem.getCount(),
          "Received " + kitItem);
    if (profession == Profession.FARMER) {
      require(
          player.getInventory().countItem(Items.STONE_HOE) == 1
              && player.getInventory().countItem(Items.WOODEN_HOE) == 0,
          "Farmer starts with one stone hoe");
    }
    for (var branch : Profession.values()) {
      String skill = branch.practiceSkill();
      long before = APIUtils.getXp(skill, player);
      for (int i = 0; i < 5; i++) APIUtils.addXp(skill, player, 1);
      require(
          APIUtils.getXp(skill, player) - before == (profession == branch ? 6 : 5),
          "Personal practice affinity " + profession + "/" + skill);
    }
    require(!choose(player, profession, starter), "Repeated selection");
    require(!choose(player, Profession.FARMER, "extra_harvest"), "Confirmed profession is locked");
  }

  private static boolean choose(ServerPlayer player, Profession profession, String starter) {
    return RaceIdentity.choose(
        player, Race.ELF, 1, CharacterClass.MAGE, "first_mastery", profession, starter);
  }

  private static void verifyDeferredKit(ServerLevel level, Vec3 position) {
    var uuid = UUID.randomUUID();
    CompoundTag saved;
    try (var session =
        TestShipPlayerSession.create(level, uuid, "ProfessionRetry", position, null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        require(
            NeoOriginsAPI.setOrigin(
                    player,
                    ResourceLocation.parse("neoorigins:origin"),
                    ResourceLocation.parse("aeroscapes_reimagine:elf"))
                == SetOriginResult.SUCCESS,
            "Create original race");
        var legacy = new CompoundTag();
        legacy.putInt("style", 1);
        legacy.putString("class", "mage");
        legacy.putString("starter", "first_mastery");
        legacy.putBoolean("grant_pending", true);
        player.getPersistentData().put("aeroscapes_reimagine_racial_traits", legacy);
        var slots = player.getInventory().items;
        for (int i = 0; i < slots.size(); i++) slots.set(i, new ItemStack(Items.COBBLESTONE, 64));
        slots.set(0, new ItemStack(Items.COAL, 62));

        var skills = SkillsMod.getInstance();
        skills.setPoints(player, ProfessionDiscipline.SMITHING.category(), WITHHELD, -2, true);
        login(player);
        require(
            Utils.getPlayerSpellbookStack(player).is(ItemRegistry.COPPER_SPELL_BOOK.get())
                && RaceIdentity.awaitingChoice(player)
                && RaceIdentity.profession(player) == null,
            "Finish the pending 0.6.4 mage grant while waiting for the new choice");
        require(
            !choose(player, Profession.BLACKSMITH, "material_repair")
                && RaceIdentity.grantPending(player)
                && RaceIdentity.awaitingChoice(player)
                && player.getInventory().countItem(Items.COAL) == 62,
            "Provider refusal protects the chooser and keeps the kit untouched");
        skills.setPoints(player, ProfessionDiscipline.SMITHING.category(), WITHHELD, 0, true);
        require(
            !choose(player, Profession.BLACKSMITH, "frugal_repair")
                && !choose(player, Profession.COOK, "hearty_meal"),
            "Pending choices stay locked");
        login(player);
        require(
            !RaceIdentity.awaitingChoice(player)
                && ProfessionProgression.learned(player, "material_repair")
                && player.getInventory().countItem(Items.COAL) == 64
                && player.getInventory().countItem(Items.STONE_PICKAXE) == 0,
            "Finishing a profession with a full inventory delivers only two coal");
        saved = player.saveWithoutId(new CompoundTag());
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
    try (var session =
        TestShipPlayerSession.create(level, uuid, "ProfessionRetry", position, saved)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        var slots = player.getInventory().items;
        // Let other providers deliver any login gifts before freeing a single slot for the kit.
        login(player);
        require(
            player.getInventory().countItem(Items.STONE_PICKAXE) == 0
                && player.getInventory().countItem(Items.COAL) == 64,
            "A fully packed player retains the undelivered items across login");
        slots.set(1, ItemStack.EMPTY);
        player.tickCount = 20;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        require(
            player.getInventory().countItem(Items.STONE_PICKAXE) == 1
                && player.getInventory().countItem(Items.COAL) == 64,
            "One free slot after login delivers just the owed tool");
        // Leave room for the native login gifts before trying the pending kit again.
        slots.set(2, ItemStack.EMPTY);
        slots.set(3, ItemStack.EMPTY);
        // Recover a native talent save that predates the committed player inventory and identity.
        SkillsMod.getInstance()
            .lockSkill(player, ProfessionDiscipline.SMITHING.category(), "material_repair");
        require(!ProfessionProgression.learned(player, "material_repair"), "Provider lost starter");
        login(player);
        require(
            ProfessionProgression.learned(player, "material_repair"),
            "Login restores the already granted native talent: profession "
                + RaceIdentity.profession(player)
                + ", starter "
                + RaceIdentity.professionStarter(player)
                + ", points "
                + SkillsMod.getInstance()
                    .getPointsLeft(player, ProfessionDiscipline.SMITHING.category())
                    .orElseThrow());
        slots.set(4, ItemStack.EMPTY);
        player.tickCount = 40;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
        int tools = player.getInventory().countItem(Items.STONE_PICKAXE);
        int coal = player.getInventory().countItem(Items.COAL);
        int books = player.getInventory().countItem(ItemRegistry.COPPER_SPELL_BOOK.get());
        require(
            tools == 1 && coal == 66 && books == 0,
            "Only the owed two coal are delivered after a slot opens: tools "
                + tools
                + ", coal "
                + coal
                + ", extra books "
                + books);
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void login(ServerPlayer player) {
    NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
  }

  static void upgrade(ServerPlayer player) {
    require(
        RaceIdentity.awaitingChoice(player) && RaceIdentity.profession(player) == null,
        "An existing 0.6.4 character must choose a profession before resuming play");
    require(
        !RaceIdentity.choose(
                player,
                Race.GOBLIN,
                1,
                CharacterClass.WARRIOR,
                "quick_block",
                Profession.BLACKSMITH,
                "material_repair")
            && !RaceIdentity.choose(
                player,
                Race.GOBLIN,
                0,
                CharacterClass.MAGE,
                "first_mastery",
                Profession.BLACKSMITH,
                "material_repair")
            && !RaceIdentity.choose(
                player,
                Race.ELF,
                1,
                CharacterClass.MAGE,
                "first_mastery",
                Profession.BLACKSMITH,
                "material_repair"),
        "Migration cannot change a previously confirmed class, appearance or race");
    require(
        RaceIdentity.choose(
            player,
            Race.GOBLIN,
            1,
            CharacterClass.MAGE,
            "first_mastery",
            Profession.BLACKSMITH,
            "material_repair"),
        "Complete only the new part of creation");
  }

  static void verifyUpgraded(ServerPlayer player) {
    require(
        !RaceIdentity.awaitingChoice(player)
            && RaceIdentity.profession(player) == Profession.BLACKSMITH
            && RaceIdentity.professionStarter(player).equals("material_repair")
            && !RaceIdentity.choose(
                player,
                Race.GOBLIN,
                1,
                CharacterClass.MAGE,
                "first_mastery",
                Profession.BLACKSMITH,
                "material_repair"),
        "Replayed confirmations and rejoining cannot repeat a completed profession grant");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
