package dev.aeroscapes.mixin.backup;

import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionStorage.class)
public interface BackupPoiStorageAccess {
  @Accessor("simpleRegionStorage")
  SimpleRegionStorage aeroscapes$backupStorage();
}
