// Aero Islands 1.1.1 by Isabel is a behavior reference; Java was reimplemented.
// GPL-3.0-or-later for 0.1.0; comparison provenance: metadata/worldgen_adoption.json.
// Published-artifact provenance and modifications: metadata/worldgen_adoption.json.
package dev.aeroscapes.worldgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.Vec3i;

public enum DistanceTestType {
  EUCLIDEAN(Vec3i::closerThan),
  MANHATTAN((origin, from, radius) -> (double) origin.distManhattan(from) < radius),
  CHEBYSHEV(
      (origin, from, radius) -> {
        int dx = Math.abs(origin.getX() - from.getX());
        int dy = Math.abs(origin.getY() - from.getY());
        int dz = Math.abs(origin.getZ() - from.getZ());
        return (double) Math.max(Math.max(dx, dy), dz) < radius;
      });

  public static final Codec<DistanceTestType> CODEC =
      Codec.STRING.xmap(DistanceTestType::valueOf, Enum::name);
  private final DistanceTester distanceTester;

  private DistanceTestType(DistanceTester distanceTester) {
    this.distanceTester = distanceTester;
  }

  public DistanceTester getDistanceTester() {
    return this.distanceTester;
  }
}
