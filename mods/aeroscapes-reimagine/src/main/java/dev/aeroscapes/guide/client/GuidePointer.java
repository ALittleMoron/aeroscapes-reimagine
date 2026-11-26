package dev.aeroscapes.guide.client;

final class GuidePointer<T> {
  private static final int DRAG_THRESHOLD = 3;
  private boolean pressed;
  private boolean dragging;
  private double startX;
  private double startY;
  private T target;

  void press(double x, double y, T target) {
    pressed = true;
    dragging = false;
    startX = x;
    startY = y;
    this.target = target;
  }

  boolean drag(double x, double y, double dx, double dy, GuideViewport viewport) {
    if (!pressed) return false;
    if (!dragging) {
      if (!pastThreshold(x, y)) return true;
      dragging = true;
      dx = x - startX;
      dy = y - startY;
    }
    viewport.drag(dx, dy);
    return true;
  }

  T release(double x, double y, T hovered) {
    T selected = pressed && !dragging && !pastThreshold(x, y) && target == hovered ? target : null;
    cancel();
    return selected;
  }

  void cancel() {
    pressed = false;
    dragging = false;
    target = null;
  }

  boolean isPressed() {
    return pressed;
  }

  boolean isDragging() {
    return dragging;
  }

  private boolean pastThreshold(double x, double y) {
    return Math.hypot(x - startX, y - startY) >= DRAG_THRESHOLD;
  }
}
