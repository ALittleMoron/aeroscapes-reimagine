package dev.aeroscapes.backup;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class BackupScheduleData extends SavedData {
  private long activeTicks;
  private long retryTicks;

  public static BackupScheduleData get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(
            new SavedData.Factory<>(BackupScheduleData::new, BackupScheduleData::load),
            "aeroscapes_reimagine_backup_schedule");
  }

  private static BackupScheduleData load(CompoundTag tag, HolderLookup.Provider registries) {
    BackupScheduleData data = new BackupScheduleData();
    data.activeTicks =
        Math.min(Short.MAX_VALUE * 60L * 20, Math.max(0, tag.getLong("active_ticks")));
    data.retryTicks = Math.min(5L * 60 * 20, Math.max(0, tag.getLong("retry_ticks")));
    return data;
  }

  public boolean advance(long intervalTicks) {
    activeTicks = Math.min(Math.max(1, intervalTicks), activeTicks + 20);
    retryTicks = Math.max(0, retryTicks - 20);
    setDirty();
    return isDue(intervalTicks);
  }

  public boolean isDue(long intervalTicks) {
    return activeTicks >= intervalTicks && retryTicks == 0;
  }

  public void completed() {
    activeTicks = 0;
    retryTicks = 0;
    setDirty();
  }

  public void failed() {
    retryTicks = 5L * 60 * 20;
    setDirty();
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    tag.putLong("active_ticks", activeTicks);
    tag.putLong("retry_ticks", retryTicks);
    return tag;
  }
}
