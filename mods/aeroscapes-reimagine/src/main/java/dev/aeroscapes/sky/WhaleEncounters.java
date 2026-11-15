package dev.aeroscapes.sky;

import dev.dacite.skywhales.config.SWConfig;
import dev.dacite.skywhales.entity.SkyWhale;
import dev.dacite.skywhales.registry.SWEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import org.jetbrains.annotations.Nullable;

/** Bounded placements in the player's forward view, within existing loaded sky. */
public final class WhaleEncounters {
  /**
   * Whale voice volume. Entity sounds reach 16 blocks times a volume of at least one, so natural
   * whales met 56-72 blocks away (and up to 24 blocks higher) stay audible; gain stays capped.
   */
  public static final float VOICE_VOLUME = 6.0F;

  private WhaleEncounters() {}

  public static double angle(double randomAngle, float playerYaw) {
    return Math.toRadians(playerYaw + 90.0) + (randomAngle - Math.PI) / 3.0;
  }

  public static boolean trySpawnNear(ServerLevel level, ServerPlayer player, RandomSource random) {
    return spawnNear(level, player, random) != null;
  }

  public static @Nullable SkyWhale spawnNear(
      ServerLevel level, ServerPlayer player, RandomSource random) {
    if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD || player.isSpectator())
      return null;
    for (int attempt = 0; attempt < 4; attempt++) {
      double angle = angle(random.nextDouble() * Math.TAU, player.getYRot());
      int distance = sample(random, SWConfig.spawnDistanceMin(), SWConfig.spawnDistanceMax());
      int height = sample(random, SWConfig.spawnHeightMin(), SWConfig.spawnHeightMax());
      double x = player.getX() + Math.cos(angle) * distance;
      double y = player.getY() + height;
      double z = player.getZ() + Math.sin(angle) * distance;
      var position = BlockPos.containing(x, y, z);
      if (!level.isLoaded(position)) continue;
      var whale = SWEntities.SKY_WHALE.get().create(level);
      if (whale == null) return null;
      float yaw = (float) Math.toDegrees(angle) - 90.0F;
      whale.moveTo(x, y, z, yaw, 0.0F);
      whale.yBodyRot = yaw;
      whale.yBodyRotO = yaw;
      WhaleVariety variety = WhaleHabitats.at(level, position, player.getOnPos());
      if (!WhaleSpawnClearance.clear(level, whale, variety)) continue;
      whale.finalizeSpawn(
          level, level.getCurrentDifficultyAt(position), MobSpawnType.NATURAL, null);
      ((WhaleVariantAccess) whale).aeroscapes$variety(variety);
      return level.addFreshEntity(whale) ? whale : null;
    }
    return null;
  }

  private static int sample(RandomSource random, int minimum, int maximum) {
    return minimum + (maximum > minimum ? random.nextInt(maximum - minimum + 1) : 0);
  }
}
