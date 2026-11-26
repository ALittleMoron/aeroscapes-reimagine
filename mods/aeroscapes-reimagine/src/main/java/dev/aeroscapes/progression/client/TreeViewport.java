package dev.aeroscapes.progression.client;

/** Screen-space camera for talent pages, independent of provider node coordinates. */
public final class TreeViewport {
  public static final int NODE_RADIUS = 13;
  public static final int NODE_OUTER_RADIUS = 15;
  public static final int BRANCH_LABEL_Y = 0;
  public static final int BRANCH_LABEL_HALF_WIDTH = 33;
  private static final int BRANCH_LABEL_GAP = 36;
  private static final int BRANCH_GAP = 42;

  public record BranchPlacement(int offsetX, int offsetY, int labelX, int nextLeft) {
    public static final BranchPlacement UNCHANGED = new BranchPlacement(0, 0, 0, 0);

    public static BranchPlacement at(int left, int minX, int maxX, int minY) {
      int width = Math.max(2 * BRANCH_LABEL_HALF_WIDTH, maxX - minX);
      int labelX = left + width / 2;
      return new BranchPlacement(
          labelX - (minX + maxX) / 2, BRANCH_LABEL_GAP - minY, labelX, left + width + BRANCH_GAP);
    }
  }

  private double centerX;
  private double centerY;
  private double scale = 1;
  private double minimumScale = 0.25;

  public void fit(double minX, double minY, double maxX, double maxY, int width, int height) {
    center((minX + maxX) / 2, (minY + maxY) / 2);
    double fitted = fittedScale(maxX - minX, maxY - minY, width, height);
    minimumScale = Math.min(0.25, fitted);
    scale = Math.min(1.25, fitted);
  }

  public void showRoot(double x, double y, double reachX, double reachY, int width, int height) {
    center(x, y);
    scale = Math.min(1, fittedScale(2 * reachX, 2 * reachY, width, height));
  }

  private static double fittedScale(double spanX, double spanY, int width, int height) {
    return Math.min(
        Math.max(1, width - 16.0) / Math.max(1, spanX + 2 * NODE_OUTER_RADIUS),
        Math.max(1, height - 16.0) / Math.max(1, spanY + 2 * NODE_OUTER_RADIUS));
  }

  public void focus(double x, double y) {
    center(x, y);
    scale = Math.max(1, scale);
  }

  private void center(double x, double y) {
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

  public boolean hits(double worldX, double worldY, double screenX, double screenY) {
    return Math.abs(x(worldX) - screenX) <= NODE_RADIUS * scale
        && Math.abs(y(worldY) - screenY) <= NODE_RADIUS * scale;
  }

  public void pan(double x, double y) {
    centerX -= x / scale;
    centerY -= y / scale;
  }

  public void zoom(double amount, double anchorX, double anchorY) {
    double before = scale;
    scale = Math.clamp(scale * Math.pow(1.2, amount), minimumScale, 2.0);
    centerX += anchorX / before - anchorX / scale;
    centerY += anchorY / before - anchorY / scale;
  }
}
