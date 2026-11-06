package dev.aeroscapes.atmosphere;

/** Checks that long descriptions can be read without covering ingredient slots. */
public final class EffectDescriptionPagesTest {
  private EffectDescriptionPagesTest() {}

  public static void main(String[] arguments) {
    var pages = new EffectDescriptionPages(25, 110, true);
    require(pages.capacity() == 6, "Reserve space for controls and one ingredient row");
    require(pages.pageCount() == 5, "All text fits across pages");
    for (int i = 0; i < 25; i++) {
      pages.select(i / pages.capacity());
      require(pages.lineIndex(i % pages.capacity()) == i, "Every line remains reachable");
    }
    require(pages.lineIndex(1) == -1, "Final page does not repeat text");
    pages.select(100);
    require(pages.page() == 4, "Upper page bound");
    pages.select(-1);
    require(pages.page() == 0, "Lower page bound");
    var shortText = new EffectDescriptionPages(2, 110, false);
    require(shortText.pageCount() == 1, "Short descriptions do not gain extra pages");
    System.out.println("Effect description pages: bounds, coverage and reserved layout passed");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
