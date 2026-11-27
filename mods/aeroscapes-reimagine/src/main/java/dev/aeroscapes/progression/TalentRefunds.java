package dev.aeroscapes.progression;

import dev.aeroscapes.magic.SpellProgression;
import dev.aeroscapes.mixin.progression.TalentCategoryAccess;
import dev.aeroscapes.races.RaceIdentity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.puffish.skillsmod.SkillsMod;
import net.puffish.skillsmod.config.CategoryConfig;
import net.puffish.skillsmod.config.skill.SkillDefinitionConfig;

public final class TalentRefunds {
  public static final ResourceLocation IDENTITY_SOURCE =
      ResourceLocation.parse("aeroscapes_reimagine:identity_grants");
  public static final ResourceLocation STUDY_SOURCE =
      ResourceLocation.parse("aeroscapes_reimagine:scroll_study");
  private static final ResourceLocation OLD_SUBCLASS_SOURCE =
      ResourceLocation.parse("aeroscapes_reimagine:subclass_starter");
  private static final Comparator<Node> GRANT_ORDER =
      Comparator.comparing(Node::pool).thenComparing(Node::id);
  private static final List<String> POOLS = pools();

  public record Node(String pool, String id) {}

  public record PoolQuote(
      String pool,
      int before,
      int after,
      int paid,
      int loss,
      int remainder,
      int previousRemainder,
      int previousDue,
      int nextDue,
      int fixedPoints,
      int practicePoints,
      int identityCredit,
      int studyCredit,
      List<String> learned,
      List<String> paidNodes,
      List<String> grantsAfter) {
    public PoolQuote {
      learned = List.copyOf(learned);
      paidNodes = List.copyOf(paidNodes);
      grantsAfter = List.copyOf(grantsAfter);
    }
  }

  public record Quote(
      UUID owner, List<PoolQuote> pools, List<Node> addedGrants, List<Node> retiredGrants) {
    public Quote {
      pools = List.copyOf(pools);
      addedGrants = List.copyOf(addedGrants);
      retiredGrants = List.copyOf(retiredGrants);
    }

    public Set<String> afterNodes(String pool) {
      for (var bank : pools) {
        if (bank.pool().equals(pool)) return Set.copyOf(bank.grantsAfter());
      }
      return Set.of();
    }

    public boolean hasPaidNodes() {
      return pools.stream().anyMatch(pool -> !pool.paidNodes().isEmpty());
    }
  }

  private TalentRefunds() {}

  public static int grantCost(ResourceLocation category, String node) {
    var provider = SkillsMod.getInstance();
    return ((TalentCategoryAccess) provider)
        .aeroscapes$category(category)
        .map(config -> cost(config, node))
        .orElse(-1);
  }

  public static Optional<Quote> preview(
      ServerPlayer player,
      Set<Node> currentIdentity,
      Set<Node> paidOverlap,
      Set<Node> nextIdentity) {
    if (!ProgressionBridge.categoriesAvailable()
        || RaceIdentity.awaitingChoice(player)
        || !currentIdentity.containsAll(paidOverlap)) return Optional.empty();

    Set<String> studies = SpellProgression.studyGrants(player);
    var provider = SkillsMod.getInstance();
    var results = new ArrayList<PoolQuote>(POOLS.size());
    for (String pool : POOLS) {
      Set<String> grantsFromStudy = pool.equals("magic") ? studies : Set.of();
      var free = new HashSet<>(grantsFromStudy);
      for (var grant : currentIdentity) {
        if (grant.pool().equals(pool) && !paidOverlap.contains(grant)) free.add(grant.id());
      }
      var bank =
          quoteBank(player, provider, pool, free, grantsFromStudy, forPool(nextIdentity, pool));
      if (bank == null) return Optional.empty();
      results.add(bank);
    }
    return Optional.of(
        new Quote(
            player.getUUID(),
            results,
            difference(nextIdentity, currentIdentity),
            difference(currentIdentity, nextIdentity)));
  }

  @Nullable
  private static PoolQuote quoteBank(
      ServerPlayer player,
      SkillsMod provider,
      String pool,
      Set<String> free,
      Set<String> studies,
      Set<String> identity) {
    var category = id(pool);
    var config = ((TalentCategoryAccess) provider).aeroscapes$category(category).orElse(null);
    if (config == null) return null;
    var learned =
        provider.getUnlockedSkills(player, category).orElseThrow().stream().sorted().toList();
    var paidNodes = learned.stream().filter(node -> !free.contains(node)).toList();
    int paid = cost(config, paidNodes);
    int identityCredit =
        cost(config, identity.stream().filter(node -> !studies.contains(node)).toList());
    int studyCredit = cost(config, studies);
    if (paid < 0 || identityCredit < 0 || studyCredit < 0) return null;
    var next = new HashSet<>(studies);
    next.addAll(identity);
    int before = provider.getPointsLeft(player, category).orElseThrow();
    int fixed =
        provider.getPointsTotal(player, category).orElseThrow()
            - source(provider, player, category, ProgressionBridge.PRACTICE_POINTS)
            - source(provider, player, category, IDENTITY_SOURCE)
            - source(provider, player, category, STUDY_SOURCE)
            - source(provider, player, category, RefundPenalty.SOURCE)
            - source(provider, player, category, OLD_SUBCLASS_SOURCE);
    int oldCarry = RefundPenalty.tenths(player, pool);
    int oldDue = RefundPenalty.due(player, pool);
    int tenths = paid + oldCarry;
    int loss = tenths / 10;
    int due = RefundPenalty.addLoss(oldDue, loss);
    int practice = ProgressionBridge.practicePoints(player, pool, identityCredit + studyCredit);
    int newTotal = fixed + practice + identityCredit + studyCredit - RefundPenalty.pointsDue(due);
    int after =
        Math.min(config.general().spentPointsLimit(), newTotal) - identityCredit - studyCredit;
    return new PoolQuote(
        pool,
        before,
        after,
        paid,
        loss,
        tenths % 10,
        oldCarry,
        oldDue,
        due,
        fixed,
        practice,
        identityCredit,
        studyCredit,
        learned,
        paidNodes,
        next.stream().sorted().toList());
  }

  public static boolean apply(
      ServerPlayer player,
      Set<Node> currentIdentity,
      Set<Node> paidOverlap,
      Set<Node> nextIdentity,
      Quote expected,
      BooleanSupplier finishIdentity) {
    if (preview(player, currentIdentity, paidOverlap, nextIdentity)
        .filter(expected::equals)
        .isEmpty()) return false;

    var provider = SkillsMod.getInstance();
    var before = new CompoundTag();
    provider.exportPlayerData(player, before);
    var penaltyBefore = RefundPenalty.snapshot(player);
    boolean committed = false;
    try {
      provider.importPlayerData(player, after(before, expected));
      if (!matches(player, provider, expected)) return false;
      RefundPenalty.apply(player, expected.pools());
      committed = finishIdentity.getAsBoolean();
      return committed;
    } finally {
      if (!committed) {
        RefundPenalty.restore(player, penaltyBefore);
        provider.importPlayerData(player, before);
      }
    }
  }

  private static CompoundTag after(CompoundTag before, Quote quote) {
    CompoundTag result = before.copy();
    CompoundTag categories = result.getCompound("categories");
    for (var bank : quote.pools()) {
      String categoryId = id(bank.pool()).toString();
      var category = categories.getCompound(categoryId);
      var learned = new ListTag();
      for (String node : bank.grantsAfter()) learned.add(StringTag.valueOf(node));
      category.put("unlocked_skills", learned);
      var points = category.getCompound("points");
      setSource(points, ProgressionBridge.PRACTICE_POINTS, bank.practicePoints());
      setSource(points, IDENTITY_SOURCE, bank.identityCredit());
      setSource(points, STUDY_SOURCE, bank.studyCredit());
      setSource(points, RefundPenalty.SOURCE, -RefundPenalty.pointsDue(bank.nextDue()));
      points.remove(OLD_SUBCLASS_SOURCE.toString());
      category.put("points", points);
      categories.put(categoryId, category);
    }
    result.put("categories", categories);
    return result;
  }

  private static boolean matches(ServerPlayer player, SkillsMod provider, Quote quote) {
    for (var row : quote.pools()) {
      var category = id(row.pool());
      if (provider.getPointsLeft(player, category).orElseThrow() != row.after()
          || !Set.copyOf(provider.getUnlockedSkills(player, category).orElseThrow())
              .equals(Set.copyOf(row.grantsAfter()))) return false;
    }
    return true;
  }

  private static int source(
      SkillsMod provider, ServerPlayer player, ResourceLocation category, ResourceLocation source) {
    return provider.getPoints(player, category, source).orElse(0);
  }

  private static void setSource(CompoundTag points, ResourceLocation source, int amount) {
    if (amount == 0) points.remove(source.toString());
    else points.putInt(source.toString(), amount);
  }

  private static int cost(CategoryConfig category, Iterable<String> nodes) {
    int result = 0;
    for (String node : nodes) {
      int next = cost(category, node);
      if (next < 0) return -1;
      result += next;
    }
    return result;
  }

  private static int cost(CategoryConfig category, String node) {
    return category
        .skills()
        .getById(node)
        .flatMap(skill -> category.definitions().getById(skill.definitionId()))
        .map(SkillDefinitionConfig::cost)
        .orElse(-1);
  }

  private static List<Node> difference(Set<Node> first, Set<Node> second) {
    return first.stream().filter(node -> !second.contains(node)).sorted(GRANT_ORDER).toList();
  }

  private static Set<String> forPool(Set<Node> nodes, String pool) {
    var found = new HashSet<String>();
    for (Node node : nodes) {
      if (node.pool().equals(pool)) found.add(node.id());
    }
    return found;
  }

  private static ResourceLocation id(String pool) {
    return ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", pool);
  }

  private static List<String> pools() {
    var ids = new ArrayList<>(List.of("combat", "ranger", "magic"));
    for (var discipline : ProfessionDiscipline.values()) ids.add(discipline.skill());
    return List.copyOf(ids);
  }
}
