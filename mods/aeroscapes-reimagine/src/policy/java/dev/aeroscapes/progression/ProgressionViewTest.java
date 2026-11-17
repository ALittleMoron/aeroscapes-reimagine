package dev.aeroscapes.progression;

import dev.aeroscapes.progression.client.InventoryButtonLayout;
import dev.aeroscapes.progression.client.ScrollViewport;
import dev.aeroscapes.progression.client.TreeViewport;
import java.util.stream.IntStream;
import net.minecraft.client.gui.navigation.ScreenRectangle;

public final class ProgressionViewTest {
  private ProgressionViewTest() {}

  public static void main(String[] args) {
    var buttons = IntStream.range(0, 3).mapToObj(InventoryButtonLayout::button).toList();
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
    System.out.println(
        "Progression views: compact inventory rail, short overflow, final row, thumb, search and camera passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
