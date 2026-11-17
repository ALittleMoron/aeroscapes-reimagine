package dev.aeroscapes.progression.client;

/** Screen-space camera for talent pages, independent of provider node coordinates. */
public final class TreeViewport {
  private double centerX;
  private double centerY;
  private double scale = 1;

  public void fit(double minX, double minY, double maxX, double maxY, int width, int height) {
    center((minX + maxX) / 2, (minY + maxY) / 2);
    scale =
        Math.clamp(
            Math.min(
                (width - 60.0) / Math.max(1, maxX - minX),
                (height - 60.0) / Math.max(1, maxY - minY)),
            0.25,
            1.25);
  }

  public void center(double x, double y) {
    centerX = x;
    centerY = y;
  }

  public double scale() {
    return scale;
  }

  public double x(double worldX) {
    return (worldX - centerX) * scale;
  }

  public double y(double worldY) {
    return (worldY - centerY) * scale;
  }

  public void pan(double x, double y) {
    centerX -= x / scale;
    centerY -= y / scale;
  }

  public void zoom(double amount, double anchorX, double anchorY) {
    double before = scale;
    scale = Math.clamp(scale * Math.pow(1.2, amount), 0.25, 2.0);
    centerX += anchorX / before - anchorX / scale;
    centerY += anchorY / before - anchorY / scale;
  }
}
