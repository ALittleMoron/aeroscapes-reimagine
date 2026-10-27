package dev.aeroscapes.mixin.scalablelux;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.simibubi.create.content.decoration.copycat.CopycatBlockEntity", remap = false)
public abstract class PlotCopycatMixin {
  @Invoker("updateLight")
  protected abstract void aeroscapes$updateLight();

  @Inject(method = "setMaterial", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin observes Create material changes.
  private void aeroscapes$materialLight(BlockState material, CallbackInfo ci) {
    BlockEntity self = (BlockEntity) (Object) this;
    if (!(self.getLevel() instanceof ServerLevel level)) return;
    SubLevelContainer plots = SubLevelContainer.getContainer(level);
    // Create updates this auxiliary value on load and on the client, but not on server material
    // changes.
    if (plots != null && plots.getPlot(new ChunkPos(self.getBlockPos())) != null)
      aeroscapes$updateLight();
  }
}
