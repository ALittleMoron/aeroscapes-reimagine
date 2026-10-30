package dev.aeroscapes.testship;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Exact deletion receipts for fixture owners who were offline when their ship was removed. */
public final class RemovedTestShips extends SavedData {
  private final Map<UUID, UUID> removed = new HashMap<>();

  private RemovedTestShips() {}

  public static RemovedTestShips get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(
            new Factory<>(RemovedTestShips::new, RemovedTestShips::load),
            "aeroscapes_removed_test_ships");
  }

  private static RemovedTestShips load(CompoundTag tag, HolderLookup.Provider registries) {
    RemovedTestShips data = new RemovedTestShips();
    ListTag entries = tag.getList("removed", Tag.TAG_COMPOUND);
    for (int index = 0; index < entries.size(); index++) {
      CompoundTag entry = entries.getCompound(index);
      if (entry.hasUUID("owner") && entry.hasUUID("ship"))
        data.removed.put(entry.getUUID("owner"), entry.getUUID("ship"));
    }
    return data;
  }

  public void record(UUID owner, UUID ship) {
    removed.put(owner, ship);
    setDirty();
  }

  public boolean consume(UUID owner, UUID ship) {
    if (!ship.equals(removed.get(owner))) return false;
    removed.remove(owner);
    setDirty();
    return true;
  }

  @Override
  public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
    ListTag entries = new ListTag();
    removed.forEach(
        (owner, ship) -> {
          CompoundTag entry = new CompoundTag();
          entry.putUUID("owner", owner);
          entry.putUUID("ship", ship);
          entries.add(entry);
        });
    tag.put("removed", entries);
    return tag;
  }
}
