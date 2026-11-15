package dev.aeroscapes.sky;

import dev.dacite.skywhales.config.SWConfig;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Regional whale density with the configured mean chance. A quarter of 1024-block cells are whale
 * waters with twice the chance; the others keep two thirds, so the average stays unchanged.
 */
public final class WhaleWaters {
  public static final int CELL_BLOCKS = 1024;
  public static final double HOTSPOT_SHARE = 0.25;
  public static final double HOTSPOT_FACTOR = 2.0;
  public static final double ORDINARY_FACTOR =
      (1.0 - HOTSPOT_SHARE * HOTSPOT_FACTOR) / (1.0 - HOTSPOT_SHARE);

  private WhaleWaters() {}

  public static boolean hotspot(long seed, double x, double z) {
    long cellX = Math.floorDiv(Mth.floor(x), CELL_BLOCKS);
    long cellZ = Math.floorDiv(Mth.floor(z), CELL_BLOCKS);
    long mixed = seed ^ 0x5EA_7A1E5L;
    mixed ^= cellX * 0x9E3779B97F4A7C15L;
    mixed = Long.rotateLeft(mixed, 29) ^ cellZ * 0xC2B2AE3D27D4EB4FL;
    mixed ^= mixed >>> 33;
    mixed *= 0xFF51AFD7ED558CCDL;
    mixed ^= mixed >>> 33;
    return (mixed >>> 11) * 0x1.0p-53 < HOTSPOT_SHARE;
  }

  public static double factor(long seed, double x, double z) {
    return hotspot(seed, x, z) ? HOTSPOT_FACTOR : ORDINARY_FACTOR;
  }

  /** A configured certainty stays deterministic, as the native spawner treats it. */
  public static boolean allows(long seed, double x, double z, RandomSource random) {
    double chance = SWConfig.spawnChance();
    if (chance >= 1.0) return true;
    return random.nextDouble() < chance(chance, hotspot(seed, x, z));
  }

  /**
   * Regional chance with the configured mean. Above a mean of one half, hotspots saturate at
   * certainty and ordinary regions take up the remainder.
   */
  public static double chance(double mean, boolean hotspot) {
    double hot = Math.min(1.0, mean * HOTSPOT_FACTOR);
    return hotspot ? hot : (mean - HOTSPOT_SHARE * hot) / (1.0 - HOTSPOT_SHARE);
  }
}
