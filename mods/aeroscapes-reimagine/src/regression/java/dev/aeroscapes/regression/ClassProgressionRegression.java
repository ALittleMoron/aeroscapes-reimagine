package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.ClassProgression;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

final class ClassProgressionRegression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final String ROOT = "magic_mastery_1";

  private ClassProgressionRegression() {}

  static void verify(ServerLevel level) {
    verifyPendingGrant(level, false);
    verifyPendingGrant(level, true);
    for (Race race : Race.values()) {
      for (CharacterClass characterClass : CharacterClass.values()) {
        verifyCharacter(level, race, characterClass);
      }
    }
  }

  private static void verifyPendingGrant(ServerLevel level, boolean retryOnLogin) {
    var position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "ClassPending", position, null)) {
      var player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        MagicData.getPlayerMagicData(player).setServerPlayer(player);
        var skills = SkillsMod.getInstance();
        var withheld = ResourceLocation.parse("aeroscapes_reimagine:grant_fixture");
        // Simulate a provider refusing an otherwise valid starter until its point source recovers.
        skills.setPoints(player, COMBAT, withheld, -2, true);
        require(
            !RaceIdentity.choose(
                player,
                Race.HUMAN,
                0,
                CharacterClass.MAGE,
                "first_mastery",
                Profession.BLACKSMITH,
                "material_repair"),
            "Provider refusal keeps the grant pending");
        require(
            RaceIdentity.grantPending(player)
                && RaceIdentity.awaitingChoice(player)
                && player.isInvulnerableTo(player.damageSources().fellOutOfWorld()),
            "Pending class remains protected");
        require(
            !SpellKnowledge.unlocked(player).contains(ROOT)
                && !SpellKnowledge.hasFocus(player)
                && player.getInventory().countItem(ItemRegistry.COPPER_SPELL_BOOK.get()) == 0,
            "No talent or journal before the provider accepts");
        skills.setPoints(player, COMBAT, withheld, 0, true);
        require(
            !RaceIdentity.choose(
                player,
                Race.HUMAN,
                0,
                CharacterClass.RANGER,
                "bow_stride",
                Profession.BLACKSMITH,
                "material_repair"),
            "An alternate submission cannot replace the reserved class");
        if (retryOnLogin) NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        else
          require(
              RaceIdentity.choose(
                  player,
                  Race.HUMAN,
                  0,
                  CharacterClass.MAGE,
                  "first_mastery",
                  Profession.BLACKSMITH,
                  "material_repair"),
              "Matching submission retries the grant");
        require(
            !RaceIdentity.awaitingChoice(player)
                && SpellKnowledge.unlocked(player).contains(ROOT)
                && SpellKnowledge.hasFocus(player)
                && skills.getPointsLeft(player, COMBAT).orElseThrow() == 1,
            "Recovered choice grants the paid root, focus and free ordinary point");
        require(
            !RaceIdentity.choose(
                player,
                Race.HUMAN,
                0,
                CharacterClass.MAGE,
                "first_mastery",
                Profession.BLACKSMITH,
                "material_repair"),
            "Confirmed packets cannot repeat a successful grant");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        require(
            player.getInventory().countItem(ItemRegistry.COPPER_SPELL_BOOK.get()) == 0
                && skills.getPointsLeft(player, COMBAT).orElseThrow() == 1,
            "Recovered class remains single-grant on subsequent login");
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void verifyCharacter(ServerLevel level, Race race, CharacterClass characterClass) {
    var position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "ClassPractice", position, null)) {
      ServerPlayer player = session.player;
      ProgressionRegression.listPlayer(level, player, true);
      try {
        PlayerLifecycleEvents.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        MagicData.getPlayerMagicData(player).setServerPlayer(player);
        ProgressionBridge.sync(player);
        String starter =
            characterClass.starters().get(race.ordinal() % characterClass.starters().size());
        boolean mage = characterClass == CharacterClass.MAGE;
        if (mage && race != Race.HUMAN) prepareExistingMage(player, race);
        require(
            RaceIdentity.choose(
                player, race, 0, characterClass, starter, Profession.BLACKSMITH, "material_repair"),
            "Class selection for " + race + " " + characterClass);
        String node = mage ? ROOT : starter;
        var provider = SkillsMod.getInstance();
        require(SpellKnowledge.unlocked(player).contains(node), "Native starter node " + node);
        require(
            provider.getPointsLeft(player, COMBAT).orElseThrow() == 1,
            "Starting combat point remains free");
        ClassProgression.afterChoice(player);
        require(
            provider.getPointsLeft(player, COMBAT).orElseThrow() == 1,
            "Retry does not duplicate credit");
        if (mage) verifyMage(player, race);
        verifyPractice(player, race, characterClass);
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void prepareExistingMage(ServerPlayer player, Race race) {
    SkillsMod.getInstance().tryUnlockSkill(player, COMBAT, ROOT, false);
    require(
        SkillsMod.getInstance().getPointsLeft(player, COMBAT).orElseThrow() == 0,
        "Previously purchased root");
    Utils.setPlayerSpellbookStack(player, new ItemStack(ItemRegistry.WIMPY_SPELL_BOOK.get()));
    PreparedAbilities.edit(player, 5, SpellRegistry.FIREBOLT_SPELL.get().getSpellId());
    if (race == Race.GOBLIN) {
      var items = player.getInventory().items;
      for (int i = 0; i < items.size(); i++) items.set(i, new ItemStack(Items.COBBLESTONE, 64));
    }
  }

  private static void verifyMage(ServerPlayer player, Race race) {
    var known = SpellKnowledge.unlocked(player);
    require(
        SpellKnowledge.spells().stream()
                .filter(spell -> MagicTalents.learned(spell.getSpellId(), known))
                .count()
            == 8,
        "Mage starts knowing exactly the eight spells of first mastery");
    var book = Utils.getPlayerSpellbookStack(player);
    require(
        book != null
            && book.is(
                race == Race.HUMAN
                    ? ItemRegistry.COPPER_SPELL_BOOK.get()
                    : ItemRegistry.WIMPY_SPELL_BOOK.get()),
        "Equip journal or preserve existing focus");
    if (race != Race.HUMAN) {
      require(
          PreparedAbilities.preparation(player)
              .slots()
              .get(5)
              .equals(SpellRegistry.FIREBOLT_SPELL.get().getSpellId()),
          "Existing prepared slot retained");
      var dropped =
          player
              .serverLevel()
              .getEntitiesOfClass(
                  ItemEntity.class,
                  player.getBoundingBox().inflate(3),
                  entity -> entity.getItem().is(ItemRegistry.COPPER_SPELL_BOOK.get()));
      int inventoryBooks = player.getInventory().countItem(ItemRegistry.COPPER_SPELL_BOOK.get());
      require(
          race == Race.GOBLIN
              ? dropped.size() == 1 && inventoryBooks == 0
              : dropped.isEmpty() && inventoryBooks == 1,
          "Deliver one journal even with an occupied focus or full inventory");
      dropped.forEach(ItemEntity::discard);
      return;
    }
    require(
        player.getInventory().countItem(ItemRegistry.COPPER_SPELL_BOOK.get()) == 0,
        "No duplicate gift after retry");
    require(MagicPractice.credit(player) == 0, "Starter spells available before practice");
    var spell = SpellRegistry.FIREBOLT_SPELL.get();
    PreparedAbilities.edit(player, 0, spell.getSpellId());
    PreparedAbilities.select(player, 0);
    var magic = MagicData.getPlayerMagicData(player);
    magic.setMana(100);
    PreparedAbilities.cast(player);
    require(magic.isCasting(), "The starter spell can be cast with the granted book");
    AbilitiesRegression.complete(player);
    require(
        magic.getMana() < 100 && PreparedAbilities.cooldownTicks(player, spell) > 0,
        "Native starter casting pays mana and starts cooldown");
  }

  private static void verifyPractice(
      ServerPlayer player, Race race, CharacterClass characterClass) {
    for (String skill : List.of("combat", "archery", "magic", "mining")) {
      boolean magic = skill.equals("magic");
      long before = magic ? MagicPractice.credit(player) : APIUtils.getXp(skill, player);
      for (int i = 0; i < 5; i++) {
        if (magic) MagicPractice.award(player, 1);
        else APIUtils.addXp(skill, player, 1);
      }
      int expected = 5;
      if (race == Race.ELF && (magic || skill.equals("archery"))) expected++;
      if (skill.equals(characterClass.practiceSkill())) expected++;
      long after = magic ? MagicPractice.credit(player) : APIUtils.getXp(skill, player);
      require(
          after - before == expected,
          race + " " + characterClass + " " + skill + " credit: " + (after - before));
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
