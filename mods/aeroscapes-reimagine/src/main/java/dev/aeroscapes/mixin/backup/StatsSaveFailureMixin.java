package dev.aeroscapes.mixin.backup;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.backup.BackupSaveFailures;
import java.io.File;
import net.minecraft.stats.ServerStatsCounter;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerStatsCounter.class)
public abstract class StatsSaveFailureMixin {
  @Shadow @Final private File file;

  @WrapOperation(
      method = "save",
      at =
          @At(
              value = "INVOKE",
              target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Throwable;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Observes the native catch and retains its logging.
  private void aeroscapes$recordFailedStatsSave(
      Logger logger, String message, Throwable failure, Operation<Void> original) {
    BackupSaveFailures.record(file.toPath().toAbsolutePath().getParent(), failure);
    original.call(logger, message, failure);
  }
}
