package dev.aeroscapes.mixin.scalablelux;

import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LayerLightSectionStorage.class)
public interface LightStorageAccess {
  @Invoker("lightOnInSection")
  boolean aeroscapes$lightOnInSection(long section);
}
