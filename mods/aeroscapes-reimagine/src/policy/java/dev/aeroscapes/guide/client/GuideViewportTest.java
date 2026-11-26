package dev.aeroscapes.guide.client;

public final class GuideViewportTest {
  private GuideViewportTest() {}

  public static void main(String[] args) {
    var viewport = new GuideViewport(-140, 258, 655);
    viewport.resize(318, 220);
    require(viewport.x(-130) >= 0 && viewport.x(248) <= 318, "Both branches fit on open");
    require(viewport.y(10) >= 0 && viewport.y(645) <= 220, "Both ends fit on open");

    viewport.zoomAt(159, 110, 7);
    viewport.drag(10_000, -10_000);
    require(viewport.y(645) >= 0 && viewport.y(645) <= 220, "Last row stays in view");
    viewport.drag(-10_000, 10_000);
    require(viewport.y(10) >= 0 && viewport.y(34) <= 220, "First row can be reached again");
    viewport.drag(0, -39);
    double before = viewport.y(140);
    viewport.zoomAt(159, before, 2);
    require(Math.abs(viewport.y(140) - before) < 0.01, "Zoom keeps an interior target anchored");

    viewport.resize(480, 290);
    require(viewport.y(34) <= 290 && viewport.y(10) >= 0, "Resize restores the first row");
    var shortChapter = new GuideViewport(-10, 128, 75);
    shortChapter.resize(318, 220);
    var top = shortChapter.y(10);
    shortChapter.drag(2, -100);
    require(shortChapter.y(10) == top, "A complete short chapter cannot be dragged away");

    verifyNodeInput();
  }

  private static void verifyNodeInput() {
    var viewport = new GuideViewport(-74, 240, 182);
    viewport.resize(246, 180);
    var node = new Object();
    int size = FieldGuideChapter.NODE_SIZE;
    var pointer = new GuidePointer<Object>();
    double firstX = viewport.x(0) + 5;
    double firstY = viewport.y(10) + 5;
    require(viewport.hitsNode(firstX, firstY, 0, 10, size), "Hover hits a visible square");
    require(
        !viewport.hitsNode(firstX + size, firstY, 0, 10, size),
        "The old label area is outside the node");

    pointer.press(firstX, firstY, node);
    require(pointer.release(firstX + 1, firstY, node) == node, "A short click opens the node");
    pointer.press(firstX, firstY, node);
    require(pointer.release(firstX + 10, firstY, node) == null, "A coalesced drag cannot open it");

    pointer.press(firstX, firstY, node);
    pointer.drag(firstX, firstY - 45, 0, -45, viewport);
    require(pointer.isDragging(), "Moving while pressed drags the map");
    require(pointer.release(firstX, firstY - 45, node) == null, "Dragging never opens a node");
    require(!pointer.isDragging(), "Hover resumes after a drag");
    viewport.zoomAt(firstX, viewport.y(78) + 5, 1);
    require(
        viewport.hitsNode(viewport.x(0) + 5, viewport.y(78) + 5, 0, 78, size),
        "The input box is aligned with a square after pan and zoom");
  }

  private static void require(boolean result, String message) {
    if (!result) throw new AssertionError(message);
  }
}
