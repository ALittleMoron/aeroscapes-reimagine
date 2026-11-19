package dev.aeroscapes.progression;

import dev.aeroscapes.progression.client.InventoryButtonLayout;
import dev.aeroscapes.progression.client.MagicTalentPath;
import dev.aeroscapes.progression.client.ScrollViewport;
import dev.aeroscapes.progression.client.TreeViewport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public final class ProgressionViewTest {
  private ProgressionViewTest() {}

  public static void main(String[] args) {
    checkMagicPaths();
    var buttons = IntStream.range(0, 4).mapToObj(InventoryButtonLayout::button).toList();
    var offhand = new ScreenRectangle(76, 61, 20, 20);
    require(
        buttons.stream().noneMatch(button -> button.overlaps(offhand)),
        "Every inventory button must clear the offhand slot and its border");
    var gap = new ScreenRectangle(78, 8, 20, 53);
    for (var button : buttons) {
      require(
          button.left() >= gap.left()
              && button.top() >= gap.top()
              && button.right() <= gap.right()
              && button.bottom() <= gap.bottom(),
          "Buttons must stay between the player preview and crafting grid, above the offhand");
    }
    for (int index = 0; index < buttons.size(); index++)
      for (int other = index + 1; other < buttons.size(); other++)
        require(!buttons.get(index).overlaps(buttons.get(other)), "Buttons must not overlap");

    var scroll = new ScrollViewport();
    scroll.resize(101, 100);
    scroll.wheel(-1);
    require(scroll.offset() == 1, "Even one pixel of overflow must scroll");
    scroll.resize(243, 131);
    scroll.wheel(-100);
    require(scroll.offset() == 112, "The final row is completely reachable");
    scroll.drag(0);
    require(scroll.offset() == 0, "Dragging the thumb reaches the first row");
    scroll.drag(131 - scroll.thumbSize());
    require(scroll.offset() == 112, "Dragging the thumb reaches the final row");
    scroll.resize(24, 131);
    require(scroll.offset() == 0 && scroll.maximum() == 0, "Filtering clamps the old offset");
    scroll.resize(243, 131);
    scroll.wheel(-2);
    scroll.reset();
    require(scroll.offset() == 0, "A new search starts at its first match");

    var camera = new TreeViewport();
    camera.fit(-34, 0, 238, 68, 400, 240);
    require(camera.x(102) == 0 && camera.y(34) == 0, "Fit centers the actual graph bounds");
    double worldX = 170;
    double anchor = camera.x(worldX);
    camera.zoom(1, anchor, 0);
    require(Math.abs(camera.x(worldX) - anchor) < 0.001, "Zoom preserves the cursor anchor");
    double before = camera.x(worldX);
    camera.pan(23, -10);
    require(Math.abs(camera.x(worldX) - before - 23) < 0.001, "Dragging moves in screen pixels");
    camera.zoom(-100, 0, 0);
    require(camera.scale() == 0.25, "Large trees have a bounded minimum zoom");
    camera.fit(-300, -64, 508, 1304, 498, 308);
    require(camera.scale() < 0.25, "The complete spell catalogue can fit below the old zoom floor");
    double overviewScale = camera.scale();
    camera.showRoot(40, -20, 64, 64, 498, 308);
    require(
        camera.scale() == 1 && camera.x(40) == 0 && camera.y(-20) == 0,
        "Magic opens on its actual root at native icon size when the first choices fit");
    camera.showRoot(40, -20, 64, 64, 116, 88);
    require(
        (64 + TreeViewport.NODE_OUTER_RADIUS) * camera.scale() <= 44 - 8 + 0.000001,
        "The root and its four immediate branches fit even on the smallest canvas");
    camera.zoom(-100, 0, 0);
    require(camera.scale() == overviewScale, "Opening on the root preserves full-tree zoom");
    camera.focus(250, 800);
    require(
        camera.x(250) == 0 && camera.y(800) == 0 && camera.scale() == 1,
        "Search centers the result at readable scale, even from an overview");
    camera.zoom(2, 0, 0);
    double closeUp = camera.scale();
    camera.focus(400, 200);
    require(camera.scale() == closeUp, "Searching again preserves a closer manual zoom");
    for (int height : new int[] {94, 308, 700}) {
      camera.fit(-300, -64, 508, 1304, 498, height);
      double border = TreeViewport.NODE_OUTER_RADIUS * camera.scale();
      require(
          Math.abs(camera.x(-300)) + border <= 249 - 8 + 0.000001
              && Math.abs(camera.x(508)) + border <= 249 - 8 + 0.000001
              && Math.abs(camera.y(-64)) + border <= height / 2.0 - 8 + 0.000001
              && Math.abs(camera.y(1304)) + border <= height / 2.0 - 8 + 0.000001,
          "Center includes outer highlights at every accepted canvas height");
      for (int step = 0; step < 4; step++) {
        double first = camera.x(0);
        double second = camera.x(32);
        require(
            second - first > 2 * TreeViewport.NODE_OUTER_RADIUS * camera.scale(),
            "Dense adjacent nodes keep separate highlights at every zoom");
        require(
            camera.hits(0, 0, first, camera.y(0)) && camera.hits(32, 0, second, camera.y(0)),
            "Clicks on rendered centers select their own node");
        double nodeGap = (first + second) / 2;
        require(
            !camera.hits(0, 0, nodeGap, camera.y(0)) && !camera.hits(32, 0, nodeGap, camera.y(0)),
            "The visible gap does not select either neighboring frame");
        double anchorX = camera.x(170);
        double anchorY = camera.y(260);
        camera.zoom(3, anchorX, anchorY);
        require(
            Math.abs(camera.x(170) - anchorX) < 0.001 && Math.abs(camera.y(260) - anchorY) < 0.001,
            "Dense-tree zoom preserves both cursor coordinates");
      }
    }
    System.out.println(
        "Progression views: compact inventory rail, short overflow, final row, thumb, search and"
            + " camera passed");
  }

  private static void checkMagicPaths() {
    var root = new MagicTalentPath.Node(1, 0, true, List.of(), List.of());
    var bridge = new MagicTalentPath.Node(1, 2, false, List.of("root"), List.of());
    var previousMastery = new MagicTalentPath.Node(1, 5, false, List.of("bridge"), List.of("root"));
    var passive = new MagicTalentPath.Node(1, 5, false, List.of("previous"), List.of());
    var expensive = new MagicTalentPath.Node(3, 5, false, List.of("root"), List.of());
    var harder = new MagicTalentPath.Node(1, 30, false, List.of("previous"), List.of());
    var goal =
        new MagicTalentPath.Node(
            2,
            15,
            false,
            List.of("z", "b", "a", "expensive", "harder"),
            List.of("root", "previous"));
    var graph =
        Map.of(
            "root", root,
            "bridge", bridge,
            "previous", previousMastery,
            "a", passive,
            "b", passive,
            "z", passive,
            "harder", harder,
            "expensive", expensive,
            "goal", goal);
    var fresh = MagicTalentPath.find(graph, Set.of(), "goal");
    require(
        fresh.unpaid().equals(Set.of("root", "bridge", "previous", "a", "goal"))
            && fresh.cost() == 6
            && fresh.practice() == 15,
        "The path includes the goal, charges shared guards once and breaks equal alternatives by"
            + " ID");
    require(
        fresh
            .edges()
            .equals(
                Set.of(
                    new MagicTalentPath.Edge("root", "bridge"),
                    new MagicTalentPath.Edge("bridge", "previous"),
                    new MagicTalentPath.Edge("previous", "a"),
                    new MagicTalentPath.Edge("a", "goal"))),
        "Only the chosen OR branch and the actual purchase edges are highlighted");
    var orphan = MagicTalentPath.find(graph, Set.of("b"), "goal");
    require(
        orphan.cost() == 5
            && orphan.unpaid().equals(Set.of("root", "bridge", "previous", "goal"))
            && orphan.edges().contains(new MagicTalentPath.Edge("b", "goal")),
        "An owned predecessor pays for the OR edge but cannot bypass missing mastery guards");
    var owned = MagicTalentPath.find(graph, Set.of("goal"), "goal");
    require(
        owned.cost() == 0 && owned.unpaid().isEmpty() && owned.edges().isEmpty(),
        "An already owned goal never repurchases a retired path");
    var uneven =
        Map.of(
            "root", root,
            "expensive", expensive,
            "bridge", bridge,
            "extra", new MagicTalentPath.Node(1, 30, false, List.of("bridge"), List.of()),
            "goal",
                new MagicTalentPath.Node(2, 0, false, List.of("expensive", "extra"), List.of()));
    var cheaper = MagicTalentPath.find(uneven, Set.of("root"), "goal");
    require(
        cheaper.cost() == 4
            && cheaper.practice() == 30
            && cheaper.unpaid().equals(Set.of("bridge", "extra", "goal")),
        "Minimum points take priority over hop count and include the highest practice on the"
            + " route");
    var blocked = Map.of("goal", new MagicTalentPath.Node(1, 0, false, List.of(), List.of()));
    require(
        MagicTalentPath.find(blocked, Set.of(), "goal") == null, "Unreachable goals have no route");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
