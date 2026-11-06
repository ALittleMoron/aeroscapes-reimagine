package dev.aeroscapes.mixin.backup;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.backup.BackupSaveFailures;
import java.nio.file.Path;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelStorageSource.LevelStorageAccess.class)
public abstract class LevelSaveFailureMixin {
  @WrapOperation(
      method = "saveLevelData",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Observes the native catch without changing autosaves.
  private void aeroscapes$recordFailedLevelSave(
      Logger logger, String message, Object world, Object exception, Operation<Void> original) {
    if (world instanceof Path directory && exception instanceof Throwable failure)
      BackupSaveFailures.record(directory, failure);
    original.call(logger, message, world, exception);
  }
}
