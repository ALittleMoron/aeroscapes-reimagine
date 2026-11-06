package dev.aeroscapes.mixin.backup;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.backup.BackupSaveFailures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityStorage.class)
public abstract class EntitySerializationFailureMixin {
  @WrapOperation(
      method = "lambda$storeEntities$1",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native serialization continues after omitting the entity.
  private static void aeroscapes$recordOmittedEntity(
      Logger logger,
      String message,
      Object entity,
      Object exception,
      Operation<Void> original,
      @Local(argsOnly = true) Entity savedEntity) {
    if (exception instanceof Throwable failure
        && savedEntity.level() instanceof ServerLevel level) {
      BackupSaveFailures.record(level.getServer().getWorldPath(LevelResource.ROOT), failure);
    }
    original.call(logger, message, entity, exception);
  }
}
