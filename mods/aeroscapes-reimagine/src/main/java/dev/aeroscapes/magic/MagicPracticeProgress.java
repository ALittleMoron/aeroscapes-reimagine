package dev.aeroscapes.magic;

/** Readiness and finite talent rewards advance from the same credited practice. */
public final class MagicPracticeProgress {
  public static final int MAX_LEVEL = 50;

  private MagicPracticeProgress() {}

  public static long nextLevelCost(int level) {
    if (level < 5) return 80L + 10L * Math.max(0, level);
    if (level < 15) return 200L + 40L * (level - 5);
    if (level < 30) return 700L + 60L * (level - 15);
    if (level < 40) return 1800L + 80L * (level - 30);
    return 3000;
  }

  public static long minimumXp(int level) {
    long total = 0;
    for (int current = 0; current < Math.clamp(level, 0, MAX_LEVEL); current++)
      total += nextLevelCost(current);
    return level > 0 ? total + 1 : 0;
  }

  public static int level(long credit) {
    long remainder = Math.max(0, credit);
    int level = 0;
    while (level < MAX_LEVEL && remainder > nextLevelCost(level)) {
      remainder -= nextLevelCost(level);
      level++;
    }
    return level;
  }

  public static long remainder(long credit) {
    int level = level(credit);
    return level == MAX_LEVEL ? 0 : Math.max(0, credit - Math.max(0, minimumXp(level) - 1));
  }

  public static long xpForPoints(int points) {
    if (points <= 1) return 0;
    if (points <= 6) return (points - 1L) * 100;
    if (points <= 15) return 500 + (points - 6L) * 500;
    if (points <= 25) return 5000 + (points - 15L) * 1500;
    return 20000 + (points - 25L) * 3000;
  }

  public static int points(long credit, int cap) {
    long earned;
    if (credit < 500) earned = 1 + Math.max(0, credit) / 100;
    else if (credit < 5000) earned = 6 + (credit - 500) / 500;
    else if (credit < 20000) earned = 15 + (credit - 5000) / 1500;
    else earned = 25 + (credit - 20000) / 3000;
    return Math.clamp(earned, 0, Math.max(0, cap));
  }

  public static long nextPointXp(long credit, int cap) {
    int points = points(credit, cap);
    return points >= cap ? 0 : xpForPoints(points + 1);
  }

  public static long fromNative(int level, long remainder, long nativeNext) {
    int bounded = Math.clamp(level, 0, MAX_LEVEL);
    long base = minimumXp(bounded);
    if (bounded == MAX_LEVEL) return base + Math.max(0, remainder - 1);
    double fraction = Math.clamp((double) remainder / Math.max(1, nativeNext), 0, 1);
    long withinLevel = (long) (fraction * nextLevelCost(bounded));
    return Math.max(0, base - 1)
        + Math.clamp(withinLevel, bounded > 0 ? 1 : 0, nextLevelCost(bounded));
  }

  public static long nativeRemainder(long credit, long nativeNext) {
    int level = level(credit);
    if (level == MAX_LEVEL) return 1;
    long value = (long) ((double) remainder(credit) / nextLevelCost(level) * nativeNext);
    return Math.clamp(value, level > 0 ? 1 : 0, Math.max(1, nativeNext));
  }
}
