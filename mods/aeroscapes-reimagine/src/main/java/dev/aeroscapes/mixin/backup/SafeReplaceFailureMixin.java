package dev.aeroscapes.mixin.backup;

import dev.aeroscapes.backup.BackupSaveFailures;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Util.class)
public abstract class SafeReplaceFailureMixin {
  @Inject(method = "safeReplaceOrMoveFile", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // safeReplaceFile discards this failure result.
  private static void aeroscapes$recordFailedReplacement(
      Path target,
      Path replacement,
      Path previous,
      boolean move,
      CallbackInfoReturnable<Boolean> callback) {
    if (!callback.getReturnValueZ()) {
      BackupSaveFailures.record(
          target.toAbsolutePath().getParent(),
          new IOException("Native file replacement failed: " + target));
    }
  }
}
