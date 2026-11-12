package dev.aeroscapes.mixin.engineering.fuel;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = BlazeBurnerBlockEntity.class, remap = false)
public interface BurnerFuelAccess {
  @Accessor("activeFuel")
  void aeroscapes$setActiveFuel(BlazeBurnerBlockEntity.FuelType fuel);

  @Accessor("remainingBurnTime")
  void aeroscapes$setRemainingBurnTime(int ticks);

  @Invoker("setBlockHeat")
  void aeroscapes$setBlockHeat(BlazeBurnerBlock.HeatLevel heat);
}
