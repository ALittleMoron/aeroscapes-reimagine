package dev.aeroscapes.mixin.backup;

import de.melanx.simplebackups.BackupThread;
import dev.aeroscapes.backup.SimpleBackupsCoordinator;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BackupThread.class, remap = false)
public abstract class SimpleBackupsTriggerMixin {
  @Inject(method = "tryCreateBackup", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the native scheduled trigger.
  private static void aeroscapes$scheduled(
      MinecraftServer server, CallbackInfoReturnable<Boolean> callback) {
    callback.setReturnValue(SimpleBackupsCoordinator.scheduled(server));
  }

  @Inject(method = "createBackup", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the native manual trigger.
  private static void aeroscapes$manual(
      MinecraftServer server, boolean quiet, CallbackInfo callback) {
    SimpleBackupsCoordinator.manual(server, quiet);
    callback.cancel();
  }
}
