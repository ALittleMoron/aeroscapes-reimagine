package dev.aeroscapes.regression;

import com.cyberday1.neoorigins.event.PlayerLifecycleEvents;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.progression.ProgressionBridge;
import dev.aeroscapes.races.ChangeItems;
import dev.aeroscapes.races.ChangeNetwork;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.Profession;
import dev.aeroscapes.races.Race;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.puffish.skillsmod.SkillsMod;

final class ChangeTransactionRegression {
  private static final ResourceLocation COMBAT =
      ResourceLocation.parse(ProgressionBridge.COMBAT_CATEGORY);
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
            new ChangeNetwork.Request(id, "class", "ranger", 0, "steady_aim", false, List.of()),
            "identity");
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
    APIUtils.setLevel("combat", player, 10);
    MagicPractice.award(player, 100);
    ProgressionBridge.sync(player);
    var skills = SkillsMod.getInstance();
    skills.tryUnlockSkill(player, COMBAT, "guard_armor", false);
    skills.tryUnlockSkill(player, COMBAT, "guard_vitality", false);
    skills.tryUnlockSkill(player, MAGIC, "magic_mastery_1", false);
    skills.tryUnlockSkill(player, MAGIC, "magic_recovery_1", false);
    var threads = new ItemStack(ChangeItems.KNOTTED_THREAD.get(), 2);
    player.setItemInHand(InteractionHand.MAIN_HAND, threads);
    long id = open(session, InteractionHand.MAIN_HAND, false);
    var refund =
        preview(
            session,
            new ChangeNetwork.Request(id, "combat", "", 0, "", false, List.of("guard_armor")),
            "refund");
    require(
        refund.nodes().equals(List.of("guard_armor", "guard_vitality"))
            && refund.freed() == 2
            && refund.cost() == 0
            && refund.remainder() == 2,
        "Thread preview includes the dependent talent and the exact fractional fee");
    int before = skills.getPointsLeft(player, COMBAT).orElseThrow();
    confirm(session, refund, "done");
    confirm(session, refund, "expired");
    require(
        threads.getCount() == 1
            && skills.getPointsLeft(player, COMBAT).orElseThrow() == before + 2
            && skills.getPointsLeft(player, MAGIC).orElseThrow() == 0
            && !skills.getUnlockedSkills(player, COMBAT).orElseThrow().contains("guard_vitality"),
        "Confirming the quoted refund twice consumes one thread and releases its points once");
    id = open(session, InteractionHand.MAIN_HAND, false);
    var magicRefund =
        preview(
            session,
            new ChangeNetwork.Request(id, "magic", "", 0, "", false, List.of("magic_mastery_1")),
            "refund");
    require(
        magicRefund.nodes().equals(List.of("magic_mastery_1", "magic_recovery_1"))
            && magicRefund.freed() == 2
            && magicRefund.cost() == 0
            && magicRefund.remainder() == 2,
        "Magic refund has its own dependents and fractional fee");
    confirm(session, magicRefund, "done");
    require(
        threads.isEmpty()
            && skills.getPointsLeft(player, MAGIC).orElseThrow() == 2
            && skills.getPointsLeft(player, COMBAT).orElseThrow() == before + 2,
        "The second thread refunds only magic points");
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
        session,
        new ChangeNetwork.Request(id, "race", target, style, "", false, List.of()),
        "identity");
  }

  private static ChangeNetwork.Reply preview(
      TestShipPlayerSession session, ChangeNetwork.Request request, String status) {
    ChangeNetwork.preview(session.player, request);
    var result = session.takePayload(ChangeNetwork.Reply.class);
    require(
        result.id() == request.id() && result.status().equals(status) && result.ticket() != 0,
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
