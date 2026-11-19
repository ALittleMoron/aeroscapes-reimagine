package dev.aeroscapes.progression.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** The cheapest remaining purchases in the acyclic magic tree, including its mastery gates. */
public record MagicTalentPath(Set<String> unpaid, Set<Edge> edges, int cost, int practice) {
  public record Edge(String parent, String child) {}

  public record Node(
      int cost, int practice, boolean root, List<String> parents, List<String> guards) {
    public Node {
      parents = parents.stream().distinct().sorted().toList();
      guards = List.copyOf(guards);
    }
  }

  public static MagicTalentPath find(Map<String, Node> graph, Set<String> owned, String goal) {
    if (!graph.containsKey(goal)) return null;
    if (owned.contains(goal)) return new MagicTalentPath(Set.of(), Set.of(), 0, 0);
    var search = new Search(graph, owned);
    search.visit(new TreeSet<>(Set.of(goal)), graph.get(goal).cost(), graph.get(goal).practice());
    return search.best;
  }

  private static final class Search {
    private final Map<String, Node> graph;
    private final Set<String> owned;
    private final Set<Set<String>> visited = new HashSet<>();
    private MagicTalentPath best;

    private Search(Map<String, Node> graph, Set<String> owned) {
      this.graph = graph;
      this.owned = owned;
    }

    private void visit(Set<String> unpaid, int cost, int practice) {
      if ((best != null
              && (cost > best.cost() || (cost == best.cost() && practice >= best.practice())))
          || !visited.add(unpaid)) return;
      for (String id : unpaid) {
        Node node = graph.get(id);
        for (String guard : node.guards()) {
          if (!owned.contains(guard) && !unpaid.contains(guard)) {
            add(guard, unpaid, cost, practice);
            return;
          }
        }
        if (!node.root() && parentIn(node.parents(), unpaid) == null) {
          for (String option : node.parents()) add(option, unpaid, cost, practice);
          return;
        }
      }
      var edges = new HashSet<Edge>();
      for (String id : unpaid) {
        Node node = graph.get(id);
        if (!node.root()) edges.add(new Edge(parentIn(node.parents(), unpaid), id));
      }
      best = new MagicTalentPath(Set.copyOf(unpaid), Set.copyOf(edges), cost, practice);
    }

    private void add(String id, Set<String> unpaid, int cost, int practice) {
      Node node = graph.get(id);
      if (node == null) return;
      var expanded = new TreeSet<>(unpaid);
      expanded.add(id);
      visit(expanded, cost + node.cost(), Math.max(practice, node.practice()));
    }

    private String parentIn(Collection<String> parents, Set<String> unpaid) {
      for (String parent : parents) if (owned.contains(parent)) return parent;
      for (String parent : parents) if (unpaid.contains(parent)) return parent;
      return null;
    }
  }
}
