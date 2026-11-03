package dev.aeroscapes.worldgen;

import net.minecraft.world.level.biome.BiomeSource;

/** Exact provider adapters expose their retained source without replacing their own behavior. */
public interface BiomeSourceDelegate {
  BiomeSource aeroscapes$delegate();
}
