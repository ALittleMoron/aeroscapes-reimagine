package dev.aeroscapes.magic;

/** Permanent study keeps over-level and duplicate scrolls intact. */
public final class SpellProgression {
  private SpellProgression() {}

  public static String studyReason(int known, int scroll, int cap) {
    if (known < 1) return "unlearned";
    if (scroll < 1 || scroll > cap) return "mastery";
    return scroll <= known ? "no_improvement" : "";
  }
}
