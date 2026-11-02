package dev.aeroscapes.worldgen;

/** Invocation-local layer selection; each native CARVERS task owns its context. */
public interface IslandCarvingContext {
  IslandCaveHosts.Range aeroscapes$layer();

  void aeroscapes$layer(IslandCaveHosts.Range layer);
}
