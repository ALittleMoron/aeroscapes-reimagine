package dev.aeroscapes.regression;

import io.github.mortuusars.horseman.HorsemanServer;
import io.github.mortuusars.horseman.world.summoning.BoundData;
import io.github.mortuusars.horseman.world.summoning.CallResult;
import io.github.mortuusars.horseman.world.summoning.StoredBoundHorse;
import io.github.mortuusars.horseman.world.summoning.SummonableHorse;
import io.github.mortuusars.horseman.world.summoning.Summoning;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.Instruments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Actual native bind/call regression; run before applying the recall synchronization adapter. */
public final class HorseRecallRegression {
  private HorseRecallRegression() {}

  public static void verify(ServerLevel level, ServerPlayer player) {
    Summoning summoning = HorsemanServer.getSummoning();
    var records = summoning.getStoredHorsesOf(player);
    require(records.isEmpty(), "Recall fixture requires its isolated test player");
    List<AbstractHorse> entityFixtures = new ArrayList<>();
    UUID unrelatedOwner = UUID.fromString("2d93c9a3-d8c2-4d3d-83df-760441c7c5f1");
    require(
        !summoning.getStorage().getBoundHorses().containsKey(unrelatedOwner),
        "Unrelated-owner fixture UUID is unused");
    var unrelated = EntityType.HORSE.create(level);
    require(unrelated != null, "Unrelated-owner native snapshot fixture exists");
    ((SummonableHorse) unrelated)
        .setHorsemanBoundData(new BoundData(unrelatedOwner, Instruments.PONDER_GOAT_HORN));
    StoredBoundHorse unrelatedRecord = new StoredBoundHorse(unrelated);
    CompoundTag unrelatedBefore = unrelatedRecord.save(new CompoundTag()).copy();
    var unrelatedRecords = summoning.getStoredHorsesOf(unrelatedOwner);
    unrelatedRecords.put(Instruments.PONDER_GOAT_HORN, unrelatedRecord);
    var initial = EntityType.DONKEY.create(level);
    require(initial != null, "Native donkey fixture exists");
    initial.moveTo(player.position().add(64, 0, 0));
    level.getChunkAt(initial.blockPosition());
    initial.tameWithName(player);
    require(initial.getSlot(499).set(new ItemStack(Items.CHEST)), "Native chest equip succeeds");
    require(initial.getSlot(500).set(new ItemStack(Items.DIAMOND, 7)), "Native cargo slot exists");
    require(level.addFreshEntity(initial), "Native initial donkey is accepted by the level");
    try {
      requireLoaded(level, initial, "Initial donkey before native bind/call");
      summoning.bind(level, initial, player, Instruments.PONDER_GOAT_HORN);
      require(
          initial.getSlot(500).set(new ItemStack(Items.EMERALD, 3)),
          "Fixture cargo mutation is accepted");
      requireCargo(initial, Items.EMERALD, 3, "Fixture really changed same-instrument cargo");
      requireLoaded(level, initial, "Changed donkey before same-instrument baseline recall");
      require(
          summoning.call(player, Instruments.PONDER_GOAT_HORN) == CallResult.SUCCESS,
          "Same-instrument recall succeeds");
      AbstractHorse current = current(level, summoning, player, Instruments.PONDER_GOAT_HORN);
      requireCargo(
          current, Items.EMERALD, 3, "A cloned horn of the same instrument keeps current cargo");

      summoning.bind(level, current, player, Instruments.SING_GOAT_HORN);
      current.getSlot(500).set(new ItemStack(Items.GOLD_INGOT, 5));
      current.moveTo(player.position().add(64, 0, 0));
      level.getChunkAt(current.blockPosition());
      requireLoaded(level, current, "Horse before older-instrument cargo recall");
      require(
          summoning.call(player, Instruments.PONDER_GOAT_HORN) == CallResult.SUCCESS,
          "Older-instrument recall succeeds");
      current = current(level, summoning, player, Instruments.PONDER_GOAT_HORN);
      requireCargo(
          current, Items.GOLD_INGOT, 5, "Older horn must not restore a stale cargo snapshot");
      requireAliases(summoning, player, current.getUUID());

      current.getSlot(500).set(new ItemStack(Items.IRON_INGOT, 11));
      current.moveTo(player.position().add(64, 0, 0));
      level.getChunkAt(current.blockPosition());
      requireLoaded(level, current, "Horse before alternating-instrument recall");
      require(
          summoning.call(player, Instruments.SING_GOAT_HORN) == CallResult.SUCCESS,
          "Second-instrument recall succeeds after UUID replacement");
      current = current(level, summoning, player, Instruments.SING_GOAT_HORN);
      requireCargo(
          current,
          Items.IRON_INGOT,
          11,
          "Another horn cannot resurrect the discarded horse's cargo");
      requireAliases(summoning, player, current.getUUID());

      int carriedIron = player.getInventory().countItem(Items.IRON_INGOT);
      ItemStack recovered = current.getSlot(500).get().copy();
      require(
          current.getSlot(500).set(ItemStack.EMPTY),
          "Cargo can be removed through the native slot");
      require(
          player.getInventory().add(recovered) && recovered.isEmpty(),
          "Removed cargo moves completely into the fixture player's inventory");
      current.moveTo(player.position().add(64, 0, 0));
      requireLoaded(level, current, "Empty-cargo horse before conservation recall");
      require(
          summoning.call(player, Instruments.PONDER_GOAT_HORN) == CallResult.SUCCESS,
          "Older horn recalls after the cargo was taken");
      current = current(level, summoning, player, Instruments.PONDER_GOAT_HORN);
      require(current.getSlot(500).get().isEmpty(), "Older horn cannot restore removed cargo");
      require(
          player.getInventory().countItem(Items.IRON_INGOT) == carriedIron + 11,
          "Recall conserves the cargo already transferred to the player");
      requireAliases(summoning, player, current.getUUID());
      long count =
          level
              .getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(96))
              .stream()
              .filter(
                  horse -> {
                    var data = ((SummonableHorse) horse).getHorsemanBoundData();
                    return data != null && data.isBoundTo(player);
                  })
              .count();
      require(count == 1, "Exactly one current bound horse remains");

      // Exercise the native snapshot serialization and unloaded recall route.
      current.getSlot(500).set(new ItemStack(Items.LAPIS_LAZULI, 13));
      summoning.onHorseUnloaded(level, current);
      records.replaceAll(
          (instrument, stored) -> {
            var restored = StoredBoundHorse.load(stored.save(new CompoundTag()));
            require(restored != null, "Native stored binding round-trip succeeds");
            return restored;
          });
      entityFixtures.add(current);
      current.discard();
      require(
          summoning.call(player, Instruments.PONDER_GOAT_HORN) == CallResult.SUCCESS,
          "Serialized unloaded binding recalls successfully");
      current = current(level, summoning, player, Instruments.PONDER_GOAT_HORN);
      requireCargo(
          current, Items.LAPIS_LAZULI, 13, "Unloaded aliases preserve the latest inventory");
      requireAliases(summoning, player, current.getUUID());

      // Reassign only instrument A; the original animal must still answer instrument B.
      var other = EntityType.HORSE.create(level);
      require(other != null, "Second native horse fixture exists");
      other.moveTo(player.position().add(64, 0, 0));
      other.tameWithName(player);
      level.addFreshEntity(other);
      requireLoaded(level, other, "Replacement horn target before rebinding");
      summoning.bind(level, other, player, Instruments.PONDER_GOAT_HORN);
      current.moveTo(player.position().add(64, 0, 0));
      requireLoaded(level, current, "Original horse before remaining-alias recall");
      require(
          summoning.call(player, Instruments.SING_GOAT_HORN) == CallResult.SUCCESS,
          "Rebinding one horn leaves the other horn usable");
      current = current(level, summoning, player, Instruments.SING_GOAT_HORN);
      requireCargo(current, Items.LAPIS_LAZULI, 13, "Rebinding a different horn cannot lose cargo");
      verifyEntityReloadAndRemoval(level, player, summoning, current, other, entityFixtures);
      require(
          unrelatedRecords.size() == 1
              && unrelatedRecords.get(Instruments.PONDER_GOAT_HORN) == unrelatedRecord
              && unrelatedRecord.save(new CompoundTag()).equals(unrelatedBefore),
          "Other owner's binding map, record identity and full saved snapshot remain unchanged");
    } finally {
      for (ServerLevel dimension : level.getServer().getAllLevels()) {
        for (var record : new ArrayList<>(records.values())) {
          var entity = dimension.getEntity(record.getEntityUuid());
          if (entity != null) entity.discard();
        }
      }
      initial.discard();
      for (AbstractHorse fixture : entityFixtures) fixture.discard();
      records.clear();
      summoning.getStorage().getBoundHorses().remove(unrelatedOwner);
      summoning
          .getStorage()
          .getHorsesToRemove()
          .removeIf(
              uuid ->
                  uuid.equals(initial.getUUID())
                      || entityFixtures.stream().anyMatch(horse -> horse.getUUID().equals(uuid)));
      summoning
          .getStorage()
          .getUnboundHorses()
          .removeIf(
              uuid ->
                  uuid.equals(initial.getUUID())
                      || entityFixtures.stream().anyMatch(horse -> horse.getUUID().equals(uuid)));
      summoning.getStorage().setDirty();
    }
  }

  private static void verifyEntityReloadAndRemoval(
      ServerLevel level,
      ServerPlayer player,
      Summoning summoning,
      AbstractHorse current,
      AbstractHorse other,
      List<AbstractHorse> entityFixtures) {
    entityFixtures.add(current);
    entityFixtures.add(other);
    summoning.bind(level, current, player, Instruments.SEEK_GOAT_HORN);
    CompoundTag oldWorldEntity = StoredBoundHorse.saveToTag(current).copy();
    UUID oldUuid = current.getUUID();
    summoning.onHorseUnloaded(level, current);
    current.discard();
    summoning.bind(level, other, player, Instruments.SEEK_GOAT_HORN);
    require(
        !summoning.getStorage().getUnboundHorses().contains(oldUuid),
        "Reassigning an unloaded horse's active horn retains its other binding");

    AbstractHorse reloaded = restoreEntity(level, oldWorldEntity, entityFixtures);
    require(
        ((SummonableHorse) reloaded)
            .getHorsemanBoundData()
            .instrument()
            .equals(Instruments.SEEK_GOAT_HORN),
        "Fixture really reloads old entity NBT with its reassigned active instrument");
    require(!summoning.onHorseLoaded(level, reloaded), "Remaining-alias entity reload is accepted");
    var restoredBinding = ((SummonableHorse) reloaded).getHorsemanBoundData();
    require(
        restoredBinding != null
            && restoredBinding.owner().equals(player.getUUID())
            && restoredBinding.instrument().equals(Instruments.SING_GOAT_HORN),
        "Old world entity NBT restores its surviving instrument without changing the owner");
    require(level.addFreshEntity(reloaded), "Reloaded native entity is added to the level");
    requireLoaded(level, reloaded, "Actual entity NBT reload after native level registration");
    requireCargo(reloaded, Items.LAPIS_LAZULI, 13, "Actual entity NBT reload retains cargo");

    reloaded.getSlot(500).set(new ItemStack(Items.COPPER_INGOT, 17));
    CompoundTag displacedWorldEntity = StoredBoundHorse.saveToTag(reloaded).copy();
    summoning.onHorseUnloaded(level, reloaded);
    reloaded.discard();
    require(
        summoning.call(player, Instruments.SING_GOAT_HORN) == CallResult.SUCCESS,
        "Unloaded horse can be recreated from its current snapshot");
    current = current(level, summoning, player, Instruments.SING_GOAT_HORN);
    entityFixtures.add(current);
    require(!current.getUUID().equals(oldUuid), "Recall replaces the unloaded entity UUID");
    require(
        summoning.getStorage().getHorsesToRemove().contains(oldUuid),
        "Native recall records a removal marker for the displaced unloaded UUID");
    AbstractHorse displaced = restoreEntity(level, displacedWorldEntity, entityFixtures);
    require(
        summoning.onHorseLoaded(level, displaced),
        "Reload of an old discarded UUID is rejected by the native removal marker");
    require(
        !summoning.getStorage().getHorsesToRemove().contains(oldUuid)
            && level.getEntity(oldUuid) == null,
        "Rejected old UUID consumes its removal marker and adds no world entity");
    requireCargo(
        current, Items.COPPER_INGOT, 17, "Rejected old entity cannot replace current cargo");

    UUID finalUuid = current.getUUID();
    summoning.unbindExistingHorse(level, player, Instruments.SING_GOAT_HORN);
    require(
        ((SummonableHorse) current).getHorsemanBoundData() == null
            && summoning.getBoundHorse(player, Instruments.SING_GOAT_HORN) == null,
        "Removing the final loaded alias really unbinds the animal");
    require(
        summoning.call(player, Instruments.SING_GOAT_HORN) == CallResult.NO_BOUND_HORSE
            && level.getEntity(finalUuid) == current,
        "Final-alias unbind prevents recall without removing the living horse");

    summoning.bind(level, current, player, Instruments.SING_GOAT_HORN);
    CompoundTag beforeFinalUnbind = StoredBoundHorse.saveToTag(current).copy();
    summoning.onHorseUnloaded(level, current);
    current.discard();
    summoning.unbindExistingHorse(level, player, Instruments.SING_GOAT_HORN);
    require(
        summoning.getStorage().getUnboundHorses().contains(finalUuid),
        "Final unloaded-alias unbind leaves the native unbind marker");
    AbstractHorse unboundReload = restoreEntity(level, beforeFinalUnbind, entityFixtures);
    require(
        !summoning.onHorseLoaded(level, unboundReload)
            && ((SummonableHorse) unboundReload).getHorsemanBoundData() == null,
        "Final-unbound entity NBT reloads as an ordinary unbound animal");
    require(
        !summoning.getStorage().getUnboundHorses().contains(finalUuid),
        "Native entity reload consumes the final-unbind marker");
    require(level.addFreshEntity(unboundReload), "Ordinary unbound horse reload is accepted");
    requireLoaded(level, unboundReload, "Final-unbound entity after native level registration");

    summoning.bind(level, unboundReload, player, Instruments.SING_GOAT_HORN);
    summoning.bind(level, unboundReload, player, Instruments.SEEK_GOAT_HORN);
    unboundReload.setHealth(0.0F);
    summoning.onHorseUnloaded(level, unboundReload);
    UUID deadUuid = unboundReload.getUUID();
    int entitiesBeforeDeadRecall =
        level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(96)).size();
    for (var instrument : List.of(Instruments.SING_GOAT_HORN, Instruments.SEEK_GOAT_HORN)) {
      StoredBoundHorse dead = summoning.getBoundHorse(player, instrument);
      require(
          dead != null && dead.isDead() && dead.getEntityUuid().equals(deadUuid),
          "Death snapshot reaches every remaining instrument alias");
      require(
          summoning.call(player, instrument) == CallResult.HORSE_IS_DEAD,
          "A dead stored animal cannot be recalled or resurrected");
      require(
          summoning.getBoundHorse(player, instrument).getEntityUuid().equals(deadUuid),
          "Rejected dead recall creates no replacement UUID");
      require(
          unboundReload.getHealth() == 0.0F
              && level
                      .getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(96))
                      .size()
                  == entitiesBeforeDeadRecall,
          "Dead recall neither heals the animal nor creates an additional entity");
    }
  }

  private static AbstractHorse restoreEntity(
      ServerLevel level, CompoundTag saved, List<AbstractHorse> entityFixtures) {
    var type = EntityType.by(saved);
    require(type.isPresent(), "Saved native entity type is present");
    var entity = type.get().create(level);
    require(entity instanceof AbstractHorse, "Saved native horse entity can be recreated");
    AbstractHorse horse = (AbstractHorse) entity;
    horse.load(saved.copy());
    entityFixtures.add(horse);
    return horse;
  }

  private static AbstractHorse current(
      ServerLevel level,
      Summoning summoning,
      ServerPlayer player,
      net.minecraft.resources.ResourceKey<net.minecraft.world.item.Instrument> instrument) {
    var record = summoning.getBoundHorse(player, instrument);
    require(record != null, "Native binding remains present");
    var entity = level.getEntity(record.getEntityUuid());
    require(entity instanceof AbstractHorse, "Native recalled entity is loaded");
    AbstractHorse horse = (AbstractHorse) entity;
    requireLoaded(level, horse, "Native recall result in the fixture player chunk");
    return horse;
  }

  private static void requireLoaded(ServerLevel level, AbstractHorse horse, String phase) {
    long chunk = net.minecraft.world.level.ChunkPos.asLong(horse.blockPosition());
    require(
        level.areEntitiesLoaded(chunk)
            && level.isPositionEntityTicking(horse.blockPosition())
            && level.getEntity(horse.getUUID()) == horse,
        "Fixture readiness failure: "
            + phase
            + "; entitiesLoaded="
            + level.areEntitiesLoaded(chunk)
            + "; entityTicking="
            + level.isPositionEntityTicking(horse.blockPosition())
            + "; UUIDVisible="
            + (level.getEntity(horse.getUUID()) == horse));
  }

  private static void requireAliases(Summoning summoning, ServerPlayer player, UUID current) {
    require(
        summoning.getStoredHorsesOf(player).values().stream()
            .allMatch(record -> record.getEntityUuid().equals(current)),
        "Every horn instrument must follow the current horse UUID");
  }

  private static void requireCargo(
      AbstractHorse horse, net.minecraft.world.item.Item item, int count, String message) {
    ItemStack cargo = horse.getSlot(500).get();
    require(
        cargo.is(item) && cargo.getCount() == count,
        message + "; actual=" + cargo.getItem() + " x" + cargo.getCount());
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
