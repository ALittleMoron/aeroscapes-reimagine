package dev.aeroscapes.progression;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.races.ClassProgression;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.puffish.skillsmod.SkillsMod;

/**
 * Turns Project MMO practice levels into Pufferfish's Skills talent points.
 *
 * <p>Points are recomputed from the current levels and written as one named point source, so a
 * repeated or missed change can neither duplicate nor lose points. Respec penalties use a separate
 * source. Project MMO's admin commands change levels without its level-up event, so a
 * once-per-second comparison catches every route instead of a perk.
 */
public final class ProgressionBridge {
  public static final String WARRIOR_PRACTICE = "combat";
  public static final String RANGER_PRACTICE = "ranger";
  public static final List<String> COMBAT_SKILLS = List.of(WARRIOR_PRACTICE);
  public static final int LEVELS_PER_POINT = 5;
  public static final int CLASS_PRACTICE_MAX_LEVEL = 50;
  public static final int COMBAT_POINT_CAP = 21;
  public static final int RANGER_POINT_CAP = 35;
  public static final String COMBAT_CATEGORY = "aeroscapes_reimagine:combat";
  public static final String RANGER_CATEGORY = "aeroscapes_reimagine:ranger";
  public static final String MAGIC_CATEGORY = "aeroscapes_reimagine:magic";
  private static final ResourceLocation COMBAT = ResourceLocation.parse(COMBAT_CATEGORY);
  private static final ResourceLocation RANGER = ResourceLocation.parse(RANGER_CATEGORY);
  private static final ResourceLocation MAGIC = ResourceLocation.parse(MAGIC_CATEGORY);
  private static final ResourceLocation SUBCLASS_CREDIT =
      ResourceLocation.parse("aeroscapes_reimagine:subclass_starter");
  static final String POINT_SOURCE = "aeroscapes_reimagine:skill_levels";
  public static final ResourceLocation PRACTICE_POINTS = ResourceLocation.parse(POINT_SOURCE);
  private static final int CHECK_INTERVAL_TICKS = 20;

  private ProgressionBridge() {}

  public static void register() {
    MagicPractice.register();
    NeoForge.EVENT_BUS.addListener(RefundPenalty::clonePlayer);
    NeoForge.EVENT_BUS.addListener(ProgressionBridge::syncOnLogin);
    NeoForge.EVENT_BUS.addListener(ProgressionBridge::refreshAfterPractice);
    PracticeActions.register();
  }

  public static int classPracticePoints(long practiceLevel, int cap) {
    int level = Math.clamp(practiceLevel, 0, CLASS_PRACTICE_MAX_LEVEL);
    if (level < 13) return (level + 2) / LEVELS_PER_POINT;
    return 3 + (level - 13) * (cap - 3) / (CLASS_PRACTICE_MAX_LEVEL - 13);
  }

  /** Reconciles each category with the practice that funds it. */
  public static void sync(ServerPlayer player) {
    if (!categoriesAvailable()) return;
    Map<String, Long> levels = APIUtils.getAllLevels(player);
    TalentPoolMigration.splitProfessions(player, levels);
    var counts = new HashMap<String, Integer>();
    var paid = RaceIdentity.identityGrantOverlaps(player);
    for (var grant : RaceIdentity.identityGrants(player)) {
      if (!paid.contains(grant)) counts.merge(grant.pool(), 1, Integer::sum);
    }
    syncClass(player, COMBAT, levels, COMBAT_POINT_CAP, counts.getOrDefault(WARRIOR_PRACTICE, 0));
    syncClass(player, RANGER, levels, RANGER_POINT_CAP, counts.getOrDefault(RANGER_PRACTICE, 0));
    int magicGrants = counts.getOrDefault("magic", 0);
    setIdentityPoints(player, MAGIC, magicGrants);
    int studied =
        SkillsMod.getInstance().getPoints(player, MAGIC, TalentRefunds.STUDY_SOURCE).orElse(0);
    setPoints(player, MAGIC_CATEGORY, practicePoints(player, "magic", magicGrants + studied));
    RefundPenalty.sync(player, WARRIOR_PRACTICE);
    RefundPenalty.sync(player, RANGER_PRACTICE);
    RefundPenalty.sync(player, "magic");
    for (var tree : ProfessionDiscipline.values()) {
      String skill = tree.skill();
      int grants = counts.getOrDefault(skill, 0);
      setIdentityPoints(player, tree.category(), grants);
      setPoints(
          player,
          tree.category().toString(),
          Math.min(tree.cap() - grants, tree.earned(levels.getOrDefault(skill, 0L), false)));
      RefundPenalty.sync(player, skill);
    }
  }

  private static void syncClass(
      ServerPlayer player,
      ResourceLocation category,
      Map<String, Long> levels,
      int cap,
      int grants) {
    var skills = SkillsMod.getInstance();
    if (skills.getPoints(player, category, SUBCLASS_CREDIT).orElse(0) != 0)
      skills.setPoints(player, category, SUBCLASS_CREDIT, 0, true);
    setIdentityPoints(player, category, grants);
    setPoints(
        player,
        category.toString(),
        classPoints(levels.getOrDefault(category.getPath(), 0L), cap, grants));
  }

  private static void setIdentityPoints(ServerPlayer player, ResourceLocation category, int count) {
    var skills = SkillsMod.getInstance();
    if (skills.getPoints(player, category, TalentRefunds.IDENTITY_SOURCE).orElse(0) != count)
      skills.setPoints(player, category, TalentRefunds.IDENTITY_SOURCE, count, true);
  }

  public static boolean categoriesAvailable() {
    if (!ModList.get().isLoaded("pmmo") || !ModList.get().isLoaded("puffish_skills")) return false;
    var skills = SkillsMod.getInstance();
    return skills.hasCategory(COMBAT)
        && skills.hasCategory(RANGER)
        && skills.hasCategory(MAGIC)
        && Arrays.stream(ProfessionDiscipline.values())
            .allMatch(tree -> skills.hasCategory(tree.category()));
  }

  /** Reconciles persisted points even after a reload or another writer replaced the source. */
  public static void refresh(ServerPlayer player) {
    sync(player);
  }

  public static int practicePoints(ServerPlayer player, String pool, int freeGrantCost) {
    if (pool.equals("magic")) {
      return MagicPracticeProgress.points(
          MagicPractice.credit(player), MagicTalents.pointCap() - freeGrantCost);
    }
    long level = APIUtils.getAllLevels(player).getOrDefault(pool, 0L);
    if (pool.equals(WARRIOR_PRACTICE)) return classPoints(level, COMBAT_POINT_CAP, freeGrantCost);
    if (pool.equals(RANGER_PRACTICE)) return classPoints(level, RANGER_POINT_CAP, freeGrantCost);
    var bank = ProfessionDiscipline.bySkill(pool).orElseThrow();
    return Math.min(bank.cap() - freeGrantCost, bank.earned(level, false));
  }

  private static int classPoints(long practiceLevel, int cap, int freeGrantCost) {
    return Math.min(cap - freeGrantCost, classPracticePoints(practiceLevel, cap));
  }

  public static long nextMagicPointXp(ServerPlayer player, long magicCredit) {
    var skills = SkillsMod.getInstance();
    int free =
        skills.getPoints(player, MAGIC, TalentRefunds.IDENTITY_SOURCE).orElse(0)
            + skills.getPoints(player, MAGIC, TalentRefunds.STUDY_SOURCE).orElse(0);
    long next = MagicPracticeProgress.nextPointXp(magicCredit, MagicTalents.pointCap() - free);
    int recovery = RefundPenalty.untilNext(player, "magic");
    return recovery > 0 && (next == 0 || magicCredit + recovery < next)
        ? magicCredit + recovery
        : next;
  }

  public static int storedPoints(ServerPlayer player, String category)
      throws CommandSyntaxException {
    return run(player, "puffish_skills points get @s " + category + " " + POINT_SOURCE);
  }

  private static void setPoints(ServerPlayer player, String category, int count) {
    try {
      if (storedPoints(player, category) == count) return;
      run(player, "puffish_skills points set @s " + category + " " + count + " " + POINT_SOURCE);
    } catch (CommandSyntaxException exception) {
      throw new IllegalStateException("Talent category " + category + " is unavailable", exception);
    }
  }

  private static int run(ServerPlayer player, String command) throws CommandSyntaxException {
    CommandSourceStack source =
        player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
    return player.server.getCommands().getDispatcher().execute(command, source);
  }

  private static void syncOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      sync(player);
      if (!RaceIdentity.grantPending(player)) ClassProgression.restore(player);
    }
  }

  private static void refreshAfterPractice(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player
        && player.tickCount % CHECK_INTERVAL_TICKS == 0) {
      refresh(player);
    }
  }
}
