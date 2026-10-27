package dev.aeroscapes.regression.biome;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.biome.Climate;

/** Observes the existing samples at the three cells that differ between iteration orders. */
public final class BiomeSampleEvidence {
  private static final boolean ENABLED = Boolean.getBoolean("aeroscapes.regression.biomes");

  private BiomeSampleEvidence() {}

  public static void sampled(int quartX, int quartY, int quartZ, Climate.TargetPoint point) {
    if (!ENABLED || !tracked(quartX, quartY, quartZ)) return;
    LogUtils.getLogger()
        .info(
            "Biome sample evidence: quart=({},{},{}), block=({},{},{}), target={}, thread={}",
            quartX,
            quartY,
            quartZ,
            quartX * 4,
            quartY * 4,
            quartZ * 4,
            point,
            Thread.currentThread().getName());
  }

  private static boolean tracked(int quartX, int quartY, int quartZ) {
    return quartX == 3056 && quartY == 9 && quartZ == 3063
        || quartX == 3057 && quartY == 10 && quartZ == 3075
        || quartX == 3084 && quartY == 0 && quartZ == 3066;
  }
}
