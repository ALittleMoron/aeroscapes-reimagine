package dev.aeroscapes.mixin;

import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Retain the server identity needed by Supplementaries' offset lookup guard. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class SableServerOffsetMixin {
  @ModifyVariable(
      method = "getOffset",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0,
      require = 1,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin substitutes this method argument.
  private BlockGetter aeroscapes$restoreServerOffsetContext(BlockGetter getter) {
    if (getter instanceof LevelAccelerator accelerator) {
      var level = ((SableLevelAcceleratorAccess) accelerator).aeroscapes$getLevel();
      if (!level.isClientSide()) return level;
    }
    return getter;
  }
}
