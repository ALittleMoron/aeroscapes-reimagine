package dev.aeroscapes.progression;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aeroscapes.magic.MagicPractice;
import dev.aeroscapes.magic.MagicPracticeProgress;
import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.races.ClassProgression;
import dev.aeroscapes.races.RaceIdentity;
import harmonised.pmmo.api.APIUtils;
import harmonised.pmmo.api.enums.EventType;
import harmonised.pmmo.api.enums.ObjectType;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.puffish.skillsmod.SkillsMod;

/**
 * Turns Project MMO practice levels into Pufferfish's Skills talent points.
 *
 * <p>Points are recomputed from the current levels and written as one named point source, so a
 * repeated or missed change can neither duplicate nor lose points. Later respec penalties belong in
 * a separate source. Project MMO's admin commands change levels without its level-up event, so a
 * once-per-second comparison catches every route instead of a perk.
 */
public final class ProgressionBridge {
  public static final List<String> COMBAT_SKILLS = List.of("combat", "archery");
  public static final List<String> PROFESSION_SKILLS =
      List.of("mining", "crafting", "smithing", "building", "farming", "cooking");
  public static final int LEVELS_PER_POINT = 5;
  public static final int PROFESSION_POINT_CAP = 2;
  public static final String COMBAT_CATEGORY = "aeroscapes_reimagine:combat";
  public static final String PROFESSION_CATEGORY = "aeroscapes_reimagine:profession";
  private static final ResourceLocation COMBAT = ResourceLocation.parse(COMBAT_CATEGORY);
  private static final ResourceLocation PROFESSION = ResourceLocation.parse(PROFESSION_CATEGORY);
  static final String POINT_SOURCE = "aeroscapes_reimagine:skill_levels";
  private static final long FOOD_CRAFT_XP = 10;
  private static final long SMELT_XP_PER_ITEM = 2;
  private static final int CHECK_INTERVAL_TICKS = 20;

  private ProgressionBridge() {}

  public static void register() {
    MagicPractice.register();
    NeoForge.EVENT_BUS.addListener(ProgressionBridge::syncOnLogin);
    NeoForge.EVENT_BUS.addListener(ProgressionBridge::refreshAfterPractice);
    NeoForge.EVENT_BUS.addListener(ProgressionBridge::awardSmelting);
    NeoForge.EVENT_BUS.addListener(ProgressionBridge::routeFoodCrafting);
  }

  public static int points(Map<String, Long> levels, List<String> skills) {
    int total = 0;
    for (String skill : skills) {
      total += Math.clamp(levels.getOrDefault(skill, 0L), 0, 50) / LEVELS_PER_POINT;
    }
    return total;
  }

  /** Writes the earned points of both categories when they differ from the stored value. */
  public static void sync(ServerPlayer player) {
    if (!categoriesAvailable()) return;
    Earned earned = earned(player);
    setPoints(player, COMBAT_CATEGORY, earned.combat());
    setPoints(player, PROFESSION_CATEGORY, earned.profession());
  }

  public static boolean categoriesAvailable() {
    if (!ModList.get().isLoaded("pmmo") || !ModList.get().isLoaded("puffish_skills")) return false;
    var skills = SkillsMod.getInstance();
    return skills.hasCategory(COMBAT) && skills.hasCategory(PROFESSION);
  }

  /** Reconciles persisted points even after a reload or another writer replaced the source. */
  public static void refresh(ServerPlayer player) {
    sync(player);
  }

  private static Earned earned(ServerPlayer player) {
    long magicCredit = MagicPractice.credit(player);
    Map<String, Long> levels = APIUtils.getAllLevels(player);
    return new Earned(
        combatPoints(player, levels, magicCredit),
        Math.min(PROFESSION_POINT_CAP, points(levels, PROFESSION_SKILLS)));
  }

  private static int combatPoints(ServerPlayer player, Map<String, Long> levels, long magicCredit) {
    int cap = MagicTalents.pointCap();
    int classPoint = RaceIdentity.characterClass(player) == null ? 0 : 1;
    return Math.min(
        cap,
        classPoint
            + MagicPracticeProgress.points(magicCredit, cap)
            + points(levels, COMBAT_SKILLS));
  }

  public static long nextCombatPointXp(ServerPlayer player, long magicCredit) {
    int cap = MagicTalents.pointCap();
    return combatPoints(player, APIUtils.getAllLevels(player), magicCredit) >= cap
        ? 0
        : MagicPracticeProgress.nextPointXp(magicCredit, cap);
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

  /** Credits the player who takes the result, never the furnace owner or an automated hopper. */
  private static void awardSmelting(PlayerEvent.ItemSmeltedEvent event) {
    ItemStack result = event.getSmelting();
    if (!(event.getEntity() instanceof ServerPlayer player) || result.isEmpty()) return;
    String skill = result.has(DataComponents.FOOD) ? "cooking" : "smithing";
    APIUtils.addXp(skill, player, SMELT_XP_PER_ITEM * result.getCount());
  }

  /** Crafting food trains cooking instead of general crafting. */
  private static void routeFoodCrafting(ServerStartingEvent event) {
    for (var item : BuiltInRegistries.ITEM) {
      if (item.components().has(DataComponents.FOOD)) {
        APIUtils.registerXpAward(
            ObjectType.ITEM,
            BuiltInRegistries.ITEM.getKey(item),
            EventType.CRAFT,
            Map.of("cooking", FOOD_CRAFT_XP),
            true);
      }
    }
  }

  private record Earned(int combat, int profession) {}
}
