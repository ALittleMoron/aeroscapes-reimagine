package dev.aeroscapes.ecology;

import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** The state already belongs to this generator; Dodo's native search otherwise omits it. */
public interface SpawnIslandGenerator {
  @Nullable
  ChunkGenerator aeroscapes$spawnGenerator();

  void aeroscapes$spawnGenerator(ChunkGenerator generator);
}
