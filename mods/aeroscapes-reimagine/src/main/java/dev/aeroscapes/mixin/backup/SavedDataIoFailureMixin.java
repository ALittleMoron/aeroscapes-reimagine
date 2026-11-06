package dev.aeroscapes.mixin.backup;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.backup.BackupSaveFailures;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.IOUtilities;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = IOUtilities.class, remap = false)
public abstract class SavedDataIoFailureMixin {
  @WrapMethod(method = "writeNbtCompressed")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Retains failures before SavedData's async catch.
  private static void aeroscapes$recordFailedDataWrite(
      CompoundTag tag, Path path, Operation<Void> original) throws IOException {
    try {
      original.call(tag, path);
    } catch (Exception | Error failure) {
      BackupSaveFailures.record(path.toAbsolutePath().getParent(), failure);
      throw failure;
    }
  }
}
