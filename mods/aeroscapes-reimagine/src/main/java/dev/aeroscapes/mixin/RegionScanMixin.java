package dev.aeroscapes.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Avoids creating and flushing empty region files during native metadata scans. */
@Mixin(RegionFileStorage.class)
public abstract class RegionScanMixin {
  @Shadow @Final private Path folder;
  @Shadow @Final private Long2ObjectLinkedOpenHashMap<RegionFile> regionCache;

  @Inject(method = "scanChunk", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked at the pinned native Mixin target above.
  private void aeroscapesReimagine$skipEmptyScan(
      ChunkPos chunk, StreamTagVisitor visitor, CallbackInfo callback) {
    // IOWorker checks pending data and deletion before calling this serialized storage method.
    int regionX = chunk.getRegionX();
    int regionZ = chunk.getRegionZ();
    if (regionCache.containsKey(ChunkPos.asLong(regionX, regionZ))) return;
    Path region = folder.resolve("r." + regionX + "." + regionZ + ".mca");
    try {
      if (aeroscapesReimagine$hasNoStoredBytes(region, folder)) callback.cancel();
    } catch (IOException | SecurityException exception) {
      // Unknown attributes and access failures retain native I/O and its error reporting.
      return;
    }
  }

  @Unique
  private static boolean aeroscapesReimagine$hasNoStoredBytes(Path region, Path directory)
      throws IOException {
    try {
      BasicFileAttributes attributes =
          Files.readAttributes(region, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      // Native opens existing regions READ+WRITE, even for this metadata-only operation.
      return attributes.isRegularFile()
          && attributes.size() == 0
          && Files.isReadable(region)
          && Files.isWritable(region);
    } catch (NoSuchFileException missing) {
      // A dangling ancestor link must not be mistaken for an ordinary absent region.
      Path ancestor = directory;
      while (ancestor != null) {
        try {
          return Files.readAttributes(
                  ancestor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
              .isDirectory();
        } catch (NoSuchFileException missingAncestor) {
          ancestor = ancestor.getParent();
        }
      }
      return false;
    }
  }
}
