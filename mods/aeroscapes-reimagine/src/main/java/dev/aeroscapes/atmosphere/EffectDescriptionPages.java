package dev.aeroscapes.atmosphere;

/** Pagination state for the space available in an EMIffect information card. */
public final class EffectDescriptionPages {
  private final int lines;
  private final int capacity;
  private int page;

  public EffectDescriptionPages(int lines, int height, boolean hasIngredients) {
    if (lines < 0) throw new IllegalArgumentException("Negative description length");
    this.lines = lines;
    capacity = Math.max(1, (height - (hasIngredients ? 52 : 34)) / 9);
  }

  public int capacity() {
    return capacity;
  }

  public int pageCount() {
    return Math.max(1, (lines + capacity - 1) / capacity);
  }

  public int page() {
    return page;
  }

  public void select(int target) {
    page = Math.clamp(target, 0, pageCount() - 1);
  }

  public int lineIndex(int visibleLine) {
    int index = page * capacity + visibleLine;
    return visibleLine >= 0 && visibleLine < capacity && index < lines ? index : -1;
  }
}
