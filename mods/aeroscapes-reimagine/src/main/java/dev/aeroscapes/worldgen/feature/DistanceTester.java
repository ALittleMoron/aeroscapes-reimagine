// Aero Islands 1.1.1 by Isabel is a behavior reference; Java was reimplemented.
// GPL-3.0-or-later for 0.1.0; comparison provenance: metadata/worldgen_adoption.json.
// Published-artifact provenance and modifications: metadata/worldgen_adoption.json.
package dev.aeroscapes.worldgen.feature;

import net.minecraft.core.BlockPos;

@FunctionalInterface
public interface DistanceTester {
  boolean withinDistance(BlockPos origin, BlockPos position, double radius);
}
