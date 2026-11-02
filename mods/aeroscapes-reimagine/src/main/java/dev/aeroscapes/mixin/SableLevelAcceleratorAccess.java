package dev.aeroscapes.mixin;

import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LevelAccelerator.class, remap = false)
public interface SableLevelAcceleratorAccess {
  @Accessor("level")
  Level aeroscapes$getLevel();
}
