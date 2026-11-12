package dev.aeroscapes.mixin.engineering.fuel;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import dev.aeroscapes.engineering.fuel.ConservedFuel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlazeBurnerBlock.class, remap = false)
public abstract class BurnerContainerMixin {
  @Inject(method = "tryInsert", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$transferContainer(
      BlockState state,
      Level level,
      BlockPos pos,
      ItemStack stack,
      boolean creative,
      boolean forceOverflow,
      boolean simulate,
      CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callback) {
    if (ConservedFuel.isFluidContainer(stack))
      callback.setReturnValue(ConservedFuel.insert(level, pos, stack, creative, simulate));
  }
}
