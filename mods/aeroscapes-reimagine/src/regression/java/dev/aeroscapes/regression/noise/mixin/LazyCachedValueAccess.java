package dev.aeroscapes.regression.noise.mixin;

import net.neoforged.neoforge.common.util.Lazy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Lazy.class, remap = false)
public interface LazyCachedValueAccess {
  @Accessor("cachedValue")
  Object aeroscapesRegression$cachedValue();
}
