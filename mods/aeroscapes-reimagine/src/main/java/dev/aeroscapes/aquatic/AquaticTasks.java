package dev.aeroscapes.aquatic;

import dev.aeroscapes.mixin.aquatic.AquaticConchAccess;
import dev.hybridlabs.aquatic.item.HAItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Persist the short conch delay so saving cannot consume the conch without its encounter. */
@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class AquaticTasks extends SavedData {
  private final List<Pending> pending = new ArrayList<>();

  private AquaticTasks() {}

  public static AquaticTasks get(ServerLevel level) {
    return level
        .getDataStorage()
        .computeIfAbsent(
            new SavedData.Factory<>(AquaticTasks::new, AquaticTasks::load),
            "aeroscapes_reimagine_shell_summons");
  }

  public static AquaticTasks load(CompoundTag tag, HolderLookup.Provider registries) {
    AquaticTasks data = new AquaticTasks();
    for (Tag entry : tag.getList("Pending", Tag.TAG_COMPOUND)) {
      if (!(entry instanceof CompoundTag row)
          || !row.contains("Position", Tag.TAG_LONG)
          || !row.contains("Due", Tag.TAG_LONG))
        throw new IllegalStateException("Invalid saved conch summon");
      data.pending.add(new Pending(BlockPos.of(row.getLong("Position")), row.getLong("Due")));
    }
    return data;
  }

  public void schedule(ServerLevel level, BlockPos position, int delay) {
    pending.add(new Pending(position.immutable(), level.getGameTime() + delay));
    setDirty();
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    ListTag entries = new ListTag();
    for (Pending summon : pending) {
      CompoundTag row = new CompoundTag();
      row.putLong("Position", summon.position().asLong());
      row.putLong("Due", summon.due());
      entries.add(row);
    }
    tag.put("Pending", entries);
    return tag;
  }

  @SubscribeEvent
  public static void tick(ServerTickEvent.Post event) {
    if (!ModList.get().isLoaded("hybrid_aquatic")) return;
    for (ServerLevel level : event.getServer().getAllLevels()) get(level).summonReady(level);
  }

  private void summonReady(ServerLevel level) {
    List<BlockPos> ready = new ArrayList<>();
    boolean changed =
        pending.removeIf(
            summon -> {
              if (level.getGameTime() < summon.due()
                  || level
                          .getChunkSource()
                          .getChunkNow(summon.position().getX() >> 4, summon.position().getZ() >> 4)
                      == null) return false;
              ready.add(summon.position());
              return true;
            });
    if (changed) setDirty();
    for (BlockPos position : ready) {
      ((AquaticConchAccess) HAItems.INSTANCE.getOMINOUS_CONCH().get())
          .aeroscapes$spawnAfterCooldown(level, position, level.getServer().getTickCount() - 140);
    }
  }

  private record Pending(BlockPos position, long due) {}
}
