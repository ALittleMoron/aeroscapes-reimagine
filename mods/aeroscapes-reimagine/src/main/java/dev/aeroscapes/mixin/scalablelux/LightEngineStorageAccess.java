package dev.aeroscapes.mixin.scalablelux;

import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightEngine.class)
public interface LightEngineStorageAccess {
  @Accessor("storage")
  LayerLightSectionStorage<?> aeroscapes$getStorage();
}
