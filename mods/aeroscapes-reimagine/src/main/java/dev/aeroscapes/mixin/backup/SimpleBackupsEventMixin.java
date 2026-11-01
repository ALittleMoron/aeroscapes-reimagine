package dev.aeroscapes.mixin.backup;

import de.melanx.simplebackups.EventListener;
import dev.aeroscapes.backup.SimpleBackupsCoordinator;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EventListener.class, remap = false)
public abstract class SimpleBackupsEventMixin {
  @Inject(method = "onServerTick", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the native backup tick injection.
  private void aeroscapes$activePlaySchedule(LevelTickEvent.Post event, CallbackInfo callback) {
    SimpleBackupsCoordinator.tick(event);
    callback.cancel();
  }
}
