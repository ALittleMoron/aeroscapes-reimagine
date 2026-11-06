package dev.aeroscapes.mixin.backup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.backup.BackupSaveFailures;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = SubLevelStorage.class, remap = false)
public abstract class SableSaveFailureMixin {
  @Shadow @Final private Path folder;

  @WrapOperation(
      method = {
        "attemptSaveHoldingChunk(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunk;)V",
        "attemptRemoveHoldingChunk(Lnet/minecraft/world/level/ChunkPos;)V",
        "attemptSaveSubLevel(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;",
        "attemptSaveSubLevel(Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)V"
      },
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
      require = 4,
      allow = 4)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Includes both pinned attemptSaveSubLevel overloads.
  private void aeroscapes$recordFailedSubLevelSave(
      Logger logger, String message, Object chunk, Object exception, Operation<Void> original) {
    if (exception instanceof Throwable failure) BackupSaveFailures.record(folder, failure);
    original.call(logger, message, chunk, exception);
  }

  @WrapMethod(method = {"flush", "pruneCache"})
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Sable's caller catches both storage failures.
  private void aeroscapes$recordFailedSubLevelFlush(Operation<Void> original) throws IOException {
    try {
      original.call();
    } catch (Exception | Error failure) {
      BackupSaveFailures.record(folder, failure);
      throw failure;
    }
  }
}
