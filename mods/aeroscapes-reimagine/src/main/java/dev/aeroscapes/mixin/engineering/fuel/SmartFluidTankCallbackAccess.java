package dev.aeroscapes.mixin.engineering.fuel;

import com.simibubi.create.foundation.fluid.SmartFluidTank;
import java.util.function.Consumer;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SmartFluidTank.class, remap = false)
public interface SmartFluidTankCallbackAccess {
  @Accessor("updateCallback")
  void aeroscapes$setCallback(Consumer<FluidStack> callback);
}
