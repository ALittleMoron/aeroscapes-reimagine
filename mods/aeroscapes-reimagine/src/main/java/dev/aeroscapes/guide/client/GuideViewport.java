package dev.aeroscapes.guide.client;

final class GuideViewport {
  private final int left;
  private final int right;
  private final int bottom;
  private int width;
  private int height;
  private double zoom;
  private double offsetX;
  private double offsetY;

  GuideViewport(int left, int right, int bottom) {
    this.left = left;
    this.right = right;
    this.bottom = bottom;
  }

  void resize(int width, int height) {
    if (this.width == width && this.height == height) return;
    this.width = width;
    this.height = height;
    zoom = Math.min(1.0, Math.min((double) width / (right - left), (double) height / bottom));
    offsetX = -left * zoom;
    offsetY = 0;
    clamp();
  }

  void drag(double x, double y) {
    offsetX += x;
    offsetY += y;
    clamp();
  }

  void zoomAt(double x, double y, double delta) {
    double newZoom = Math.clamp(zoom * Math.pow(1.15, delta), Math.min(0.60, zoom), 1.4);
    double ratio = newZoom / zoom;
    offsetX = x - (x - offsetX) * ratio;
    offsetY = y - (y - offsetY) * ratio;
    zoom = newZoom;
    clamp();
  }

  private void clamp() {
    double minX = width - right * zoom;
    double maxX = -left * zoom;
    offsetX = minX >= maxX ? (minX + maxX) / 2 : Math.clamp(offsetX, minX, maxX);
    double minY = height - bottom * zoom;
    offsetY = minY >= 0 ? minY / 2 : Math.clamp(offsetY, minY, 0);
  }

  double x(double x) {
    return offsetX + x * zoom;
  }

  double y(double y) {
    return offsetY + y * zoom;
  }

  double zoom() {
    return zoom;
  }

  boolean hitsNode(double mouseX, double mouseY, int nodeX, int nodeY, int size) {
    return mouseX >= x(nodeX)
        && mouseX < x(nodeX + size)
        && mouseY >= y(nodeY)
        && mouseY < y(nodeY + size);
  }
}
