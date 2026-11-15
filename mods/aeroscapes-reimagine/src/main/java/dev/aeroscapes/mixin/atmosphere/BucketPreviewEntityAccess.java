package dev.aeroscapes.mixin.atmosphere;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface BucketPreviewEntityAccess {
  @Accessor(value = "forgeFluidTypeHeight", remap = false)
  Object2DoubleMap<FluidType> aeroscapes$bucketFluidHeight();

  @Accessor("firstTick")
  void aeroscapes$bucketInitialized(boolean firstTick);

  @Accessor("wasTouchingWater")
  void aeroscapes$bucketWaterPose(boolean touchingWater);
}
