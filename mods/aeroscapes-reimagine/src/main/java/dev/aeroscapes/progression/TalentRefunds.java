package dev.aeroscapes.progression;

import dev.aeroscapes.magic.MagicTalents;
import dev.aeroscapes.mixin.progression.TalentCategoryAccess;
import dev.aeroscapes.races.CharacterClass;
import dev.aeroscapes.races.RaceIdentity;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.config.CategoryConfig;
import net.puffish.skillsmod.config.skill.SkillDefinitionConfig;

public final class TalentRefunds {
  public record Quote(String pool, List<String> nodes, int spent, int loss, int remainder) {
    public Quote {
      nodes = List.copyOf(nodes);
    }
  }

  private TalentRefunds() {}

  public static Optional<Quote> preview(
      ServerPlayer player, String pool, Collection<String> selected) {
    if (selected.isEmpty()
        || !(pool.equals("combat") || pool.equals("profession"))
        || !ProgressionBridge.categoriesAvailable()
        || RaceIdentity.awaitingChoice(player)) return Optional.empty();
    var id = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", pool);
    var skills = SkillsMod.getInstance();
    var category = ((TalentCategoryAccess) skills).aeroscapes$category(id);
    if (category.isEmpty()) return Optional.empty();
    var learned = Set.copyOf(skills.getUnlockedSkills(player, id).orElse(List.of()));
    if (!learned.containsAll(selected)) return Optional.empty();

    var retained = new HashSet<>(learned);
    retained.removeAll(selected);
    var removed = reachable(category.get(), learned);
    removed.removeAll(reachable(category.get(), retained));
    // An unrelated orphan in an older save is only refundable when explicitly selected.
    removed.addAll(selected);
    String protectedNode =
        pool.equals("profession")
            ? RaceIdentity.professionStarter(player)
            : (RaceIdentity.characterClass(player) == CharacterClass.MAGE
                ? "magic_mastery_1"
                : RaceIdentity.starter(player));
    if (removed.contains(protectedNode)) return Optional.empty();

    int spent = 0;
    for (String node : removed) {
      var definition =
          category
              .get()
              .skills()
              .getById(node)
              .flatMap(skill -> category.get().definitions().getById(skill.definitionId()));
      if (definition.isEmpty()) return Optional.empty();
      spent += definition.get().cost();
    }
    if (spent <= 0) return Optional.empty();
    int tenths = spent + RefundPenalty.tenths(player, pool);
    return Optional.of(
        new Quote(pool, removed.stream().sorted().toList(), spent, tenths / 10, tenths % 10));
  }

  private static Set<String> reachable(CategoryConfig category, Set<String> allowed) {
    Set<String> reached = new HashSet<>();
    boolean changed;
    do {
      changed = false;
      for (var skill : category.skills().getAll()) {
        String id = skill.id();
        if (!allowed.contains(id)
            || reached.contains(id)
            || MagicTalents.missingPriorMastery(id, reached::contains) != null) continue;
        if (!skill.isRoot()) {
          int required =
              category
                  .definitions()
                  .getById(skill.definitionId())
                  .map(SkillDefinitionConfig::requiredSkills)
                  .orElseThrow();
          var incoming = category.connections().normal().getNeighborsFor(id).orElse(List.of());
          if (incoming.stream().filter(reached::contains).count() < required) continue;
        }
        reached.add(id);
        changed = true;
      }
    } while (changed);
    return reached;
  }

  public static boolean apply(ServerPlayer player, Collection<String> selected, Quote expected) {
    if (preview(player, expected.pool(), selected).filter(expected::equals).isEmpty()) return false;
    var provider = SkillsMod.getInstance();
    var category = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", expected.pool());
    int before = provider.getSpentPoints(player, category).orElseThrow();
    var snapshot = new CompoundTag();
    provider.exportPlayerData(player, snapshot);
    boolean committed = false;
    try {
      for (var node : expected.nodes()) provider.lockSkill(player, category, node);
      if (before - provider.getSpentPoints(player, category).orElseThrow() != expected.spent())
        return false;
      RefundPenalty.charge(player, expected);
      committed = true;
      return true;
    } finally {
      if (!committed) provider.importPlayerData(player, snapshot);
    }
  }
}
