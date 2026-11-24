package dev.aeroscapes.mixin.workshop;

import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResultSlot.class)
public interface CraftingResultInputAccess {
  @Accessor("craftSlots")
  CraftingContainer aeroscapes$craftInput();
}
