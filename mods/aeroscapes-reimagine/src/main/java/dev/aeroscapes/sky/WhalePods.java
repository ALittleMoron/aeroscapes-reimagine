package dev.aeroscapes.sky;

import dev.dacite.skywhales.entity.SkyWhale;
import dev.dacite.skywhales.registry.SWEntities;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rare pods: a natural encounter sometimes brings one or two companions of the same form, placed
 * beside and slightly behind the leader under the same full-body clearance rules.
 */
public final class WhalePods {
  public static final float POD_CHANCE = 0.08F;
  public static final String LEADER = "aeroscapes_reimagine:pod_leader";
  public static final double MINIMUM_SIDE = 28.0;
  public static final double MAXIMUM_SIDE = 40.0;
  private static final int PLACEMENT_ATTEMPTS = 4;

  private WhalePods() {}

  public static int afterNaturalEncounter(ServerLevel level, SkyWhale leader, RandomSource random) {
    if (random.nextFloat() >= POD_CHANCE) return 0;
    return addCompanions(level, leader, random, 1 + random.nextInt(2));
  }

  public static int addCompanions(
      ServerLevel level, SkyWhale leader, RandomSource random, int companions) {
    var variety = ((WhaleVariantAccess) leader).aeroscapes$variety();
    Vec3 forward = Vec3.directionFromRotation(0.0F, leader.yBodyRot);
    double firstSide = random.nextBoolean() ? 1.0 : -1.0;
    int placed = 0;
    for (int index = 0; index < companions; index++) {
      double sideSign = index % 2 == 0 ? firstSide : -firstSide;
      for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
        double side =
            sideSign * (MINIMUM_SIDE + random.nextDouble() * (MAXIMUM_SIDE - MINIMUM_SIDE));
        double back = 4.0 + random.nextDouble() * 12.0;
        double x = leader.getX() - forward.x * back - forward.z * side;
        double y = leader.getY() + random.nextInt(9) - 4;
        double z = leader.getZ() - forward.z * back + forward.x * side;
        var companion = SWEntities.SKY_WHALE.get().create(level);
        if (companion == null) return placed;
        companion.moveTo(x, y, z, leader.yBodyRot, 0.0F);
        companion.yBodyRot = leader.yBodyRot;
        companion.yBodyRotO = leader.yBodyRot;
        if (!WhaleSpawnClearance.clear(level, companion, variety)) continue;
        companion.finalizeSpawn(
            level,
            level.getCurrentDifficultyAt(companion.blockPosition()),
            MobSpawnType.NATURAL,
            null);
        ((WhaleVariantAccess) companion).aeroscapes$variety(variety);
        companion.getPersistentData().putUUID(LEADER, leader.getUUID());
        if (level.addFreshEntity(companion)) placed++;
        break;
      }
    }
    return placed;
  }

  public static @Nullable SkyWhale leader(ServerLevel level, SkyWhale whale) {
    var data = whale.getPersistentData();
    if (!data.hasUUID(LEADER)) return null;
    UUID id = data.getUUID(LEADER);
    return level.getEntity(id) instanceof SkyWhale leader && leader.isAlive() ? leader : null;
  }
}
