package dev.aeroscapes.mixin.backup;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.backup.BackupSaveFailures;
import java.nio.file.Path;
import net.minecraft.server.PlayerAdvancements;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerAdvancements.class)
public abstract class AdvancementSaveFailureMixin {
  @Shadow @Final private Path playerSavePath;

  @WrapOperation(
      method = "save",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Observes the native catch and retains its logging.
  private void aeroscapes$recordFailedAdvancementSave(
      Logger logger, String message, Object path, Object exception, Operation<Void> original) {
    if (exception instanceof Throwable failure) {
      BackupSaveFailures.record(playerSavePath.toAbsolutePath().getParent(), failure);
    }
    original.call(logger, message, path, exception);
  }
}
