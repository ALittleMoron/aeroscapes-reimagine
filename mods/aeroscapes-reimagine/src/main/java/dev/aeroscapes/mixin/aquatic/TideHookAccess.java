package dev.aeroscapes.mixin.aquatic;

import com.li64.tide.data.fishing.CrateData;
import com.li64.tide.data.fishing.mediums.FishingMedium;
import com.li64.tide.registries.entities.misc.fishing.TideFishingHook;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TideFishingHook.class, remap = false)
public interface TideHookAccess {
  @Accessor("nibble")
  void aeroscapes$nibble(int ticks);

  @Accessor("crateData")
  void aeroscapes$crateData(CrateData data);

  @Accessor("catchType")
  void aeroscapes$catchType(TideFishingHook.CatchType type);

  @Accessor("hookedItems")
  void aeroscapes$hookedItems(List<ItemStack> items);

  @Accessor("fluidState")
  void aeroscapes$fluidState(FluidState state);

  @Accessor("medium")
  void aeroscapes$medium(FishingMedium medium);
}
