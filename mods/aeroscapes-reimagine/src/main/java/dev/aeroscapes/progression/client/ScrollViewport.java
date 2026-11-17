package dev.aeroscapes.progression.client;

/** One content-space offset shared by wheel scrolling and the thumb. */
public final class ScrollViewport {
  private int content;
  private int viewport;
  private double offset;

  public void resize(int contentHeight, int viewportHeight) {
    content = Math.max(0, contentHeight);
    viewport = Math.max(1, viewportHeight);
    offset = Math.clamp(offset, 0, maximum());
  }

  public int maximum() {
    return Math.max(0, content - viewport);
  }

  public double offset() {
    return offset;
  }

  public void reset() {
    offset = 0;
  }

  public void wheel(double amount) {
    offset = Math.clamp(offset - amount * 25, 0, maximum());
  }

  public int thumbSize() {
    return Math.min(viewport, Math.max(16, viewport * viewport / Math.max(1, content)));
  }

  public int thumbPosition() {
    return maximum() == 0 ? 0 : (int) Math.round(offset / maximum() * (viewport - thumbSize()));
  }

  public void drag(double thumbTop) {
    int travel = viewport - thumbSize();
    offset = travel <= 0 ? 0 : Math.clamp(thumbTop / travel * maximum(), 0, maximum());
  }
}
