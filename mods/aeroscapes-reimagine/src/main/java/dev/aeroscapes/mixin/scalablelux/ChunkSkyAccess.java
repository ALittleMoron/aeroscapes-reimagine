package dev.aeroscapes.mixin.scalablelux;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkAccess.class)
public interface ChunkSkyAccess {
  @Accessor("skyLightSources")
  void aeroscapes$setSkySources(ChunkSkyLightSources sources);

  @Accessor("skyLightSources")
  ChunkSkyLightSources aeroscapes$getSkySources();
}
