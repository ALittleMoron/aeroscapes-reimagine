package dev.aeroscapes.progression;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aeroscapes.progression.client.TalentClient.ClassTree;
import dev.aeroscapes.progression.client.TreeViewport;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Checks the visible node and connection geometry in the talent screen's layout coordinates. */
public final class TalentLayoutTest {
  private static final int BADGE_RADIUS = TreeViewport.NODE_OUTER_RADIUS;
  private static final int EDGE_CLEARANCE = BADGE_RADIUS + 3;

  private TalentLayoutTest() {}

  public static void verify(Path categories) throws IOException {
    for (ClassTree tree : ClassTree.values()) {
      String bank = tree.category().getPath();
      verifyCanvas(tree.name(), List.of(read(categories, bank)));
    }
    for (ProfessionDiscipline section : ProfessionDiscipline.values()) {
      if (section.toolHandling()) continue;
      var branches = new ArrayList<Branch>();
      for (ProfessionDiscipline tree : ProfessionDiscipline.values()) {
        if (tree.section() == section) branches.add(read(categories, tree.skill()));
      }
      verifyCanvas(section.skill(), branches);
    }
  }

  private static Branch read(Path categories, String name) throws IOException {
    Path category = categories.resolve(name);
    var nodes = new HashMap<String, Point>();
    for (var skill : json(category.resolve("skills.json")).entrySet()) {
      var placed = skill.getValue().getAsJsonObject();
      nodes.put(skill.getKey(), new Point(placed.get("x").getAsInt(), placed.get("y").getAsInt()));
    }
    var links = new ArrayList<Link>();
    for (var kind : json(category.resolve("connections.json")).asMap().values()) {
      for (var direction : kind.getAsJsonObject().asMap().values()) {
        for (var connection : direction.getAsJsonArray()) {
          var endpoints = connection.getAsJsonArray();
          links.add(new Link(endpoints.get(0).getAsString(), endpoints.get(1).getAsString()));
        }
      }
    }
    return new Branch(name, nodes, links);
  }

  private static JsonObject json(Path path) throws IOException {
    try (var reader = Files.newBufferedReader(path)) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    }
  }

  private static void verifyCanvas(String canvas, List<Branch> branches) {
    var badges = new ArrayList<Badge>();
    var lines = new ArrayList<Connection>();
    int branchLeft = 0;
    for (Branch branch : branches) {
      var nodes = branch.nodes().values();
      var placement =
          branches.size() > 1
              ? TreeViewport.BranchPlacement.at(
                  branchLeft,
                  nodes.stream().mapToInt(Point::x).min().orElseThrow(),
                  nodes.stream().mapToInt(Point::x).max().orElseThrow(),
                  nodes.stream().mapToInt(Point::y).min().orElseThrow())
              : TreeViewport.BranchPlacement.UNCHANGED;
      var placed = new HashMap<String, Badge>();
      branch
          .nodes()
          .forEach(
              (id, point) ->
                  placed.put(
                      id,
                      new Badge(
                          branch.name() + "/" + id,
                          point.x() + placement.offsetX(),
                          point.y() + placement.offsetY())));
      badges.addAll(placed.values());
      for (Link link : branch.links()) {
        Badge from = placed.get(link.from());
        Badge to = placed.get(link.to());
        lines.add(new Connection(from, to, new Line2D.Double(from.x(), from.y(), to.x(), to.y())));
      }
      branchLeft = placement.nextLeft();
    }
    verifyBadges(canvas, badges, lines);
    verifyConnections(canvas, lines);
  }

  private static void verifyBadges(String canvas, List<Badge> badges, List<Connection> lines) {
    for (int index = 0; index < badges.size(); index++) {
      Badge badge = badges.get(index);
      for (int other = index + 1; other < badges.size(); other++) {
        Badge neighbor = badges.get(other);
        if (badge.bounds(BADGE_RADIUS).intersects(neighbor.bounds(BADGE_RADIUS)))
          throw new AssertionError(canvas + " overlaps " + badge.id() + " and " + neighbor.id());
      }
      for (Connection line : lines) {
        if (!line.has(badge) && badge.bounds(EDGE_CLEARANCE).intersectsLine(line.segment()))
          throw new AssertionError(canvas + " obscures " + line.label() + " behind " + badge.id());
      }
    }
  }

  private static void verifyConnections(String canvas, List<Connection> lines) {
    for (int index = 0; index < lines.size(); index++) {
      Connection a = lines.get(index);
      for (int other = index + 1; other < lines.size(); other++) {
        Connection b = lines.get(other);
        if (!a.has(b.from()) && !a.has(b.to()) && a.segment().intersectsLine(b.segment()))
          throw new AssertionError(canvas + " crosses " + a.label() + " and " + b.label());
      }
    }
  }

  private record Point(int x, int y) {}

  private record Link(String from, String to) {}

  private record Badge(String id, int x, int y) {
    Rectangle2D bounds(int radius) {
      return new Rectangle2D.Double(x - radius, y - radius, radius * 2, radius * 2);
    }
  }

  private record Connection(Badge from, Badge to, Line2D segment) {
    boolean has(Badge badge) {
      return from.equals(badge) || to.equals(badge);
    }

    String label() {
      return from.id() + " → " + to.id();
    }
  }

  private record Branch(String name, Map<String, Point> nodes, List<Link> links) {}
}
