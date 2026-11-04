package dev.aeroscapes.mixin.ecology;

import dev.aeroscapes.ecology.SpawnIslandGenerator;
import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkGeneratorStructureState.class)
public abstract class SpawnStructureStateMixin implements SpawnIslandGenerator {
  @Unique @Nullable private ChunkGenerator aeroscapes$spawnGenerator;

  @Override
  @Nullable
  public ChunkGenerator aeroscapes$spawnGenerator() {
    return aeroscapes$spawnGenerator;
  }

  @Override
  public void aeroscapes$spawnGenerator(ChunkGenerator generator) {
    aeroscapes$spawnGenerator = generator;
  }
}
