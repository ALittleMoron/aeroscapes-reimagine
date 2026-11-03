package dev.aeroscapes.mixin.aquatic;

import dev.aeroscapes.aquatic.AquaticTasks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.hybridlabs.aquatic.item.OminousConchItem", remap = false)
public abstract class AquaticConchMixin {
  @Inject(method = "spawnAfterCooldown", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The native queue retries without allowing a tick.
  private void aeroscapes$delayEncounter(
      ServerLevel level, BlockPos position, int startTick, CallbackInfo callback) {
    int deadline = startTick + 140;
    if (level.getServer().getTickCount() >= deadline) return;
    BlockPos origin = position.immutable();
    AquaticTasks.get(level).schedule(level, origin, deadline - level.getServer().getTickCount());
    callback.cancel();
  }
}
