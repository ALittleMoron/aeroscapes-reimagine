package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.progression.ProfessionDiscipline;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.AnimalLure;
import dev.aeroscapes.races.ChangeItems;
import dev.aeroscapes.races.ChangeNetwork;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

final class ChangeTransactionRegression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
  private static final ResourceLocation RANGER =
      ResourceLocation.parse(ProgressionBridge.RANGER_CATEGORY);
  private static final ResourceLocation MAGIC =
      ResourceLocation.parse(ProgressionBridge.MAGIC_CATEGORY);

  private ChangeTransactionRegression() {}

  static void verify(ServerLevel level) {
    var position = Vec3.atBottomCenterOf(level.getSharedSpawnPos()).add(0, 32, 0);
    try (var session =
        TestShipPlayerSession.create(level, UUID.randomUUID(), "ChangeItems", position, null)) {
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
            "Confirm the original character");
        verifySeals(session);
        verifyThread(session);
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    }
  }

  private static void verifySeals(TestShipPlayerSession session) {
    var player = session.player;
    var seals = new ItemStack(ChangeItems.IDENTITY_SEAL.get(), 2);
    player.setItemInHand(InteractionHand.MAIN_HAND, seals);
    long id = open(session, InteractionHand.MAIN_HAND, true);
    var canceled = racePreview(session, id, "elf", 1);
    ChangeNetwork.confirm(player, new ChangeNetwork.Confirm(id, 0));
    confirm(session, canceled, "expired");
    require(
        seals.getCount() == 2 && RaceIdentity.race(player) == Race.HUMAN,
        "Canceled change leaves both items and character intact");

    id = open(session, InteractionHand.MAIN_HAND, true);
    var moved = racePreview(session, id, "elf", 1);
    var replacement = new ItemStack(Items.STICK);
    player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
    player.setItemInHand(InteractionHand.OFF_HAND, seals);
    confirm(session, moved, "expired");
    require(
        seals.getCount() == 2
            && replacement.getCount() == 1
            && RaceIdentity.race(player) == Race.HUMAN,
        "Switching hands invalidates confirmation against the original hand");

    id = open(session, InteractionHand.OFF_HAND, true);
    var replaced = racePreview(session, id, "elf", 1);
    var identical = seals.copy();
    player.setItemInHand(InteractionHand.OFF_HAND, identical);
    confirm(session, replaced, "expired");
    require(
        identical.getCount() == 2
            && seals.getCount() == 2
            && RaceIdentity.race(player) == Race.HUMAN,
        "An identical replacement stack cannot spend a preview from another stack");
    player.setItemInHand(InteractionHand.OFF_HAND, seals);

    id = open(session, InteractionHand.OFF_HAND, true);
    var amountChanged = racePreview(session, id, "elf", 1);
    seals.setCount(3);
    confirm(session, amountChanged, "expired");
    require(
        seals.getCount() == 3 && RaceIdentity.race(player) == Race.HUMAN,
        "Changing the original stack quantity invalidates the preview");
    seals.setCount(2);

    id = open(session, InteractionHand.OFF_HAND, true);
    var abandoned = racePreview(session, id, "elf", 1);
    ChangeNetwork.confirm(player, new ChangeNetwork.Confirm(id, 0, true));
    confirm(session, abandoned, "expired");
    require(seals.getCount() == 2, "Returning to the editor invalidates only the prior preview");

    var stale = racePreview(session, id, "dwarf", 0);
    var elf = racePreview(session, id, "elf", 1);
    confirm(session, stale, "expired");
    require(
        RaceIdentity.race(player) == Race.HUMAN && seals.getCount() == 2,
        "An earlier selection cannot confirm a revised preview");
    confirm(session, elf, "done");
    confirm(session, elf, "expired");
    require(
        seals.getCount() == 1
            && RaceIdentity.race(player) == Race.ELF
            && RaceIdentity.style(player) == 1
            && RaceIdentity.characterClass(player) == CharacterClass.WARRIOR,
        "Race changes once with the chosen appearance and a two-item stack");

    id = open(session, InteractionHand.OFF_HAND, true);
    var ranger =
        preview(
            session,
            new ChangeNetwork.Request(id, "class", "ranger", 0, "steady_aim", ""),
            "identity");
    require(
        ranger.pools().size() == 14
            && ranger.addedGrants().contains(new ChangeNetwork.Grant("ranger", "steady_aim"))
            && ranger.retiredGrants().contains(new ChangeNetwork.Grant("combat", "quick_block")),
        "The Seal quotes every bank plus the restored and retiring starter choices");
    confirm(session, ranger, "done");
    require(
        seals.isEmpty()
            && RaceIdentity.race(player) == Race.ELF
            && RaceIdentity.style(player) == 1
            && RaceIdentity.characterClass(player) == CharacterClass.RANGER
            && RaceIdentity.starter(player).equals("steady_aim")
            && RaceIdentity.profession(player) == Profession.BLACKSMITH,
        "Changing only the class consumes the second seal and keeps the other identity choices");
  }

  private static void verifyThread(TestShipPlayerSession session) {
    var player = session.player;
    APIUtils.setLevel("combat", player, 13);
    APIUtils.setLevel("farming", player, 10);
    APIUtils.setLevel("pickaxe_handling", player, 4);
    MagicPractice.award(player, 100);
    ProgressionBridge.sync(player);
    var skills = SkillsMod.getInstance();
    var farming = ProfessionDiscipline.FARMING.category();
    var pickaxe = ProfessionDiscipline.PICKAXE_HANDLING.category();
    Map<ResourceLocation, Set<String>> purchases =
        Map.of(
            COMBAT, Set.of("guard_armor", "guard_vitality", "heavy_armor_handling"),
            MAGIC, Set.of("magic_mastery_1", "magic_recovery_1"),
            farming, Set.of("animal_lure"),
            pickaxe, Set.of("copper"));
    skills.tryUnlockSkill(player, COMBAT, "guard_armor", false);
    skills.tryUnlockSkill(player, COMBAT, "guard_vitality", false);
    skills.tryUnlockSkill(player, COMBAT, "heavy_armor_handling", false);
    skills.tryUnlockSkill(player, MAGIC, "magic_mastery_1", false);
    skills.tryUnlockSkill(player, MAGIC, "magic_recovery_1", false);
    skills.tryUnlockSkill(player, farming, "animal_lure", false);
    skills.tryUnlockSkill(player, pickaxe, "copper", false);
    purchases.forEach(
        (pool, nodes) ->
            require(
                skills.getUnlockedSkills(player, pool).orElseThrow().containsAll(nodes),
                "Set up paid talents in " + pool));
    APIUtils.setLevel("combat", player, 10);
    ProgressionBridge.sync(player);
    var armor = new ItemStack(Items.IRON_CHESTPLATE);
    player.setItemSlot(EquipmentSlot.CHEST, armor);
    PreparedAbilities.edit(player, 0, 0, AnimalLure.ID);
    require(
        PreparedAbilities.loadout(player).contains(AnimalLure.ID),
        "Prepare the purchased Farming ability");

    var threads = new ItemStack(ChangeItems.KNOTTED_THREAD.get(), 2);
    player.setItemInHand(InteractionHand.MAIN_HAND, threads);
    long id = open(session, InteractionHand.MAIN_HAND, false);
    var requestScope = new ChangeNetwork.Request(id, "combat", "", 0, "", "").numbered(12);
    ChangeNetwork.preview(player, requestScope);
    var rejected = session.takePayload(ChangeNetwork.Reply.class);
    require(
        rejected.sequence() == requestScope.sequence()
            && rejected.ticket() == 0
            && rejected.status().equals("invalid"),
        "The Thread refuses a scoped pool request");

    var quoted = preview(session, ChangeNetwork.Request.reset(id).numbered(13), "refund");
    require(
        quoted.disabled().contains(AnimalLure.ID),
        "Preview warns when a prepared Farming ability will become unavailable");
    require(
        quoted.wornGear().contains("minecraft:iron_chestplate"),
        "Preview identifies worn armor affected by lost training");
    PreparedAbilities.edit(player, 0, 0, "");
    confirm(session, quoted, "expired");
    require(
        threads.getCount() == 2 && AnimalLure.learned(player),
        "A changed preparation makes the quote stale without spending or refunding anything");
    PreparedAbilities.edit(player, 0, 0, AnimalLure.ID);

    id = open(session, InteractionHand.MAIN_HAND, false);
    var refund = preview(session, ChangeNetwork.Request.reset(id).numbered(14), "refund");
    var byPool =
        refund.pools().stream().collect(Collectors.toMap(ChangeNetwork.Pool::pool, row -> row));
    require(
        byPool.size() == 14 && refund.addedGrants().isEmpty() && refund.retiredGrants().isEmpty(),
        "The Thread quotes every independent bank and preserves starting grants");
    require(
        byPool.get("combat").before() == -1
            && byPool.get("combat").paid() == 3
            && byPool.get("combat").loss() == 0
            && byPool.get("combat").remainder() == 3
            && byPool.get("magic").paid() == 2
            && byPool.get("farming").paid() == 1
            && byPool.get("pickaxe_handling").paid() == 1,
        "The full quote includes paid class, magic, profession and tool investments and the"
            + " fractional fee");
    var beforeXp = MagicPractice.credit(player);
    confirm(session, refund, "done");
    confirm(session, refund, "expired");
    require(
        threads.getCount() == 1
            && RaceIdentity.characterClass(player) == CharacterClass.RANGER
            && RaceIdentity.race(player) == Race.ELF
            && RaceIdentity.profession(player) == Profession.BLACKSMITH
            && skills.getUnlockedSkills(player, RANGER).orElseThrow().contains("steady_aim")
            && MagicPractice.credit(player) == beforeXp
            && player.getItemBySlot(EquipmentSlot.CHEST) == armor
            && PreparedAbilities.loadout(player).contains(AnimalLure.ID)
            && !AnimalLure.learned(player),
        "The Thread spends once, preserves identity, starter, practice and saved preparation, and"
            + " disables the refunded ability");
    purchases.forEach(
        (pool, nodes) ->
            require(
                java.util.Collections.disjoint(
                        skills.getUnlockedSkills(player, pool).orElseThrow(), nodes)
                    && skills.getPointsLeft(player, pool).orElseThrow()
                        == byPool.get(pool.getPath()).after(),
                "Actual post-reset state and balance agree with the quote for " + pool));
  }

  private static long open(TestShipPlayerSession session, InteractionHand hand, boolean identity) {
    var player = session.player;
    require(
        player.getItemInHand(hand).use(player.level(), player, hand).getResult().consumesAction(),
        "Use the held change item");
    var opened = session.takePayload(ChangeNetwork.Open.class);
    require(opened.identity() == identity && opened.id() != 0, "Item opens its own server session");
    return opened.id();
  }

  private static ChangeNetwork.Reply racePreview(
      TestShipPlayerSession session, long id, String target, int style) {
    return preview(
        session, new ChangeNetwork.Request(id, "race", target, style, "", ""), "identity");
  }

  private static ChangeNetwork.Reply preview(
      TestShipPlayerSession session, ChangeNetwork.Request request, String status) {
    ChangeNetwork.preview(session.player, request);
    var result = session.takePayload(ChangeNetwork.Reply.class);
    require(
        result.id() == request.id()
            && result.sequence() == request.sequence()
            && result.status().equals(status)
            && result.ticket() != 0,
        "Expected " + status + " preview, got " + result);
    return result;
  }

  private static void confirm(
      TestShipPlayerSession session, ChangeNetwork.Reply quote, String status) {
    ChangeNetwork.confirm(session.player, new ChangeNetwork.Confirm(quote.id(), quote.ticket()));
    var result = session.takePayload(ChangeNetwork.Reply.class);
    require(
        result.id() == quote.id() && result.status().equals(status),
        "Expected " + status + " confirmation, got " + result);
  }

  private static void require(boolean condition, String description) {
    if (!condition) throw new IllegalStateException(description);
  }
}
