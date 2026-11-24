package dev.aeroscapes.progression;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.ClassProgression;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import java.util.Arrays;
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
  public static final List<String> COMBAT_SKILLS = List.of("combat", "archery");
  public static final int LEVELS_PER_POINT = 5;
  public static final int COMBAT_POINT_CAP = 11;
  public static final String COMBAT_CATEGORY = "aeroscapes_reimagine:combat";
  public static final String MAGIC_CATEGORY = "aeroscapes_reimagine:magic";
  private static final ResourceLocation COMBAT = ResourceLocation.parse(COMBAT_CATEGORY);
  private static final ResourceLocation MAGIC = ResourceLocation.parse(MAGIC_CATEGORY);
  static final String POINT_SOURCE = "aeroscapes_reimagine:skill_levels";
  private static final int CHECK_INTERVAL_TICKS = 20;

  private ProgressionBridge() {}

  public static void register() {
    MagicPractice.register();
    NeoForge.EVENT_BUS.addListener(RefundPenalty::clonePlayer);
    NeoForge.EVENT_BUS.addListener(ProgressionBridge::syncOnLogin);
    NeoForge.EVENT_BUS.addListener(ProgressionBridge::refreshAfterPractice);
    PracticeActions.register();
  }

  public static int points(Map<String, Long> levels, List<String> skills) {
    int total = 0;
    for (String skill : skills) {
      total += Math.clamp(levels.getOrDefault(skill, 0L), 0, 50) / LEVELS_PER_POINT;
    }
    return total;
  }

  /** Reconciles each category with the practice that funds it. */
  public static void sync(ServerPlayer player) {
    if (!categoriesAvailable()) return;
    Map<String, Long> levels = APIUtils.getAllLevels(player);
    CharacterClass characterClass = RaceIdentity.characterClass(player);
    int combatStarter = characterClass != null && characterClass != CharacterClass.MAGE ? 1 : 0;
    TalentPoolMigration.split(player);
    TalentPoolMigration.splitProfessions(player, levels);
    setPoints(
        player,
        COMBAT_CATEGORY,
        Math.min(COMBAT_POINT_CAP, combatStarter + points(levels, COMBAT_SKILLS)));
    setPoints(player, MAGIC_CATEGORY, magicPoints(player, MagicPractice.credit(player)));
    RefundPenalty.sync(player, "combat");
    RefundPenalty.sync(player, "magic");
    var starter = RaceIdentity.profession(player);
    for (var tree : ProfessionDiscipline.values()) {
      String skill = tree.skill();
      boolean starterCredit = starter != null && starter.practiceSkill().equals(skill);
      setPoints(
          player,
          tree.category().toString(),
          tree.earned(levels.getOrDefault(skill, 0L), starterCredit));
      RefundPenalty.sync(player, skill);
    }
  }

  public static boolean categoriesAvailable() {
    if (!ModList.get().isLoaded("pmmo") || !ModList.get().isLoaded("puffish_skills")) return false;
    var skills = SkillsMod.getInstance();
    return skills.hasCategory(COMBAT)
        && skills.hasCategory(MAGIC)
        && Arrays.stream(ProfessionDiscipline.values())
            .allMatch(tree -> skills.hasCategory(tree.category()));
  }

  /** Reconciles persisted points even after a reload or another writer replaced the source. */
  public static void refresh(ServerPlayer player) {
    sync(player);
  }

  private static int magicPoints(ServerPlayer player, long magicCredit) {
    int cap = MagicTalents.pointCap();
    int mageStarter = RaceIdentity.characterClass(player) == CharacterClass.MAGE ? 1 : 0;
    return Math.min(cap, mageStarter + MagicPracticeProgress.points(magicCredit, cap));
  }

  public static long nextMagicPointXp(ServerPlayer player, long magicCredit) {
    int cap = MagicTalents.pointCap();
    long next =
        magicPoints(player, magicCredit) >= cap
            ? 0
            : MagicPracticeProgress.nextPointXp(magicCredit, cap);
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
