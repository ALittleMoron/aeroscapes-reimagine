package dev.aeroscapes.mixin.construction;

import dev.aeroscapes.construction.LittleSablePayloads;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.api.SubLevelAssemblyHelper", remap = false)
public abstract class LittleSableAssemblyPayloadMixin {
  @Inject(
      method =
          "moveBlocks(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/SubLevelAssemblyHelper$AssemblyTransform;Ljava/lang/Iterable;)V",
      at = @At("RETURN"),
      require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Refresh after all BE payloads, before assembleBlocks reads COM.
  private static void aeroscapes$payloadsMoved(
      ServerLevel level,
      AssemblyTransform transform,
      Iterable<BlockPos> blocks,
      CallbackInfo callback) {
    LittleSablePayloads.afterMove(level, transform, blocks);
  }
}
