package dev.aeroscapes.regression.noise.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Uses native blending wrappers in the regression without changing production access. */
@Mixin(Blender.class)
public interface BlenderConstructor {
  @Invoker("<init>")
  static Blender aeroscapesRegression$create(
      Long2ObjectOpenHashMap<BlendingData> heights,
      Long2ObjectOpenHashMap<BlendingData> densities) {
    throw new UnsupportedOperationException("Mixin constructor invoker");
  }
}
