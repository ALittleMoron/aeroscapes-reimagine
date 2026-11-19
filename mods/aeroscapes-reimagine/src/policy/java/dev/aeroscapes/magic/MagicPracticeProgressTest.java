package dev.aeroscapes.magic;

public final class MagicPracticeProgressTest {
  private MagicPracticeProgressTest() {}

  public static void main(String[] arguments) {
    readiness();
    finiteRewards();
    nativeMigration();
    System.out.println("Magic practice readiness, frequent rewards and migration policies passed");
  }

  private static void readiness() {
    int[] levels = {5, 15, 30, 40, 45, 50};
    long[] thresholds = {501, 4301, 21101, 42701, 57701, 72701};
    for (int index = 0; index < levels.length; index++) {
      int level = levels[index];
      long threshold = thresholds[index];
      require(MagicPracticeProgress.minimumXp(level) == threshold, "Readiness milestone " + level);
      require(
          MagicPracticeProgress.level(threshold - 1) == level - 1,
          "Readiness retains the native strict boundary before level " + level);
      require(
          MagicPracticeProgress.level(threshold) == level,
          "The first earned XP above the threshold opens level " + level);
    }
    require(MagicPracticeProgress.level(-1) == 0, "Negative credit cannot remove readiness");
    require(MagicPracticeProgress.level(Long.MAX_VALUE) == 50, "Post-cap practice stays level 50");
    require(MagicPracticeProgress.remainder(72701) == 0, "Mastered readiness has no next level");
    require(
        MagicPracticeProgress.level(353000) == 50,
        "Earning the complete combat allocation never needs a higher readiness level");
  }

  private static void finiteRewards() {
    int cap = MagicTalents.pointCap();
    require(MagicPracticeProgress.points(0, cap) == 1, "The root is affordable before practice");
    require(MagicPracticeProgress.points(-1, cap) == 1, "No negative practice balance");
    require(MagicPracticeProgress.points(500, cap) == 6, "Five introductory practice rewards");
    require(MagicPracticeProgress.points(5000, cap) == 15, "Nine intermediate practice rewards");
    require(MagicPracticeProgress.points(20000, cap) == 25, "Ten developed practice rewards");
    require(MagicPracticeProgress.points(72701, cap) == 42, "Readiness 50 is not all talents");
    for (int points = 2; points <= cap; points++) {
      long threshold = MagicPracticeProgress.xpForPoints(points);
      require(
          MagicPracticeProgress.points(threshold - 1, cap) == points - 1,
          "Practice cannot grant a talent early: " + points);
      require(
          MagicPracticeProgress.points(threshold, cap) == points,
          "The exact practice milestone grants one talent: " + points);
      require(
          MagicPracticeProgress.nextPointXp(threshold - 1, cap) == threshold,
          "The next-reward display uses the same threshold: " + points);
    }
    require(
        MagicPracticeProgress.xpForPoints(cap) - MagicPracticeProgress.xpForPoints(cap - 1) == 3000,
        "The final choice does not wait for another five readiness levels");
    require(
        MagicPracticeProgress.points(Long.MAX_VALUE, cap) == cap, "Credit cannot exceed the cap");
    require(
        MagicPracticeProgress.nextPointXp(MagicPracticeProgress.xpForPoints(cap), cap) == 0,
        "No reward beyond the final point");
    require(MagicPracticeProgress.points(353000, 0) == 0, "An empty budget grants no points");
  }

  private static void nativeMigration() {
    for (int level = 0; level < 50; level++) {
      long nativeNext = 100 + 20L * level;
      for (long nativeRemainder : new long[] {0, nativeNext / 2, nativeNext}) {
        long migrated = MagicPracticeProgress.fromNative(level, nativeRemainder, nativeNext);
        require(
            MagicPracticeProgress.level(migrated) == level,
            "Changing the XP curve preserves readiness, including a full old bar: " + level);
        long projected = MagicPracticeProgress.nativeRemainder(migrated, nativeNext);
        require(
            projected >= 0 && projected <= nativeNext,
            "The native projection cannot accidentally award another level: " + level);
      }
    }
    require(
        MagicPracticeProgress.level(MagicPracticeProgress.fromNative(50, 0, 1100)) == 50,
        "A previously mastered mage keeps readiness 50");
    require(
        MagicPracticeProgress.nativeRemainder(353000, 1100) <= 1100,
        "Post-cap talent training cannot overflow native readiness");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
