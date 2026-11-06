package dev.aeroscapes.mixin.backup;

import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.backup.BackupSaveFailures;
import java.io.File;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerDataStorage.class)
public abstract class PlayerSaveFailureMixin {
  @Shadow @Final private File playerDir;

  @Inject(
      method = "save",
      at =
          @At(
              value = "INVOKE",
              target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The native catch does not pass its exception to SLF4J.
  private void aeroscapes$recordFailedPlayerSave(
      Player player, CallbackInfo callback, @Local Exception failure) {
    BackupSaveFailures.record(playerDir.toPath(), failure);
  }
}
