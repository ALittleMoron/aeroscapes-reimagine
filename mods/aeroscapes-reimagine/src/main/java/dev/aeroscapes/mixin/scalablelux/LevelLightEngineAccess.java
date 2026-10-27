package dev.aeroscapes.mixin.scalablelux;

import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelLightEngine.class)
public interface LevelLightEngineAccess {
  @Mutable
  @Accessor("blockEngine")
  void aeroscapes$setBlockEngine(LightEngine<?, ?> engine);

  @Mutable
  @Accessor("skyEngine")
  void aeroscapes$setSkyEngine(LightEngine<?, ?> engine);
}
