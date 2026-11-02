package dev.aeroscapes.regression;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import dev.aeroscapes.pickup.PickupRequests;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.pickup.PickupUpgradeWrapper;
import team.creative.itemphysic.ItemPhysic;
import team.creative.itemphysic.common.packet.DropPacket;
import team.creative.itemphysic.common.packet.PickupPacket;
import team.creative.itemphysic.server.ItemEntityExtender;
import team.creative.itemphysic.server.ItemPhysicServer;

/** Native item packets, inventory conservation, automation and material rules. */
public final class PickupRegression {
  private PickupRegression() {}

  public static void verify(ServerLevel level, Path output) {
    Vec3 position = Vec3.atCenterOf(level.getSharedSpawnPos()).add(0, 40, 0);
    level.getChunkAt(BlockPos.containing(position));
    JsonObject report = new JsonObject();
    try (var session =
        TestShipPlayerSession.create(
            level,
            UUID.fromString("b36295a2-ea97-41f6-a83e-66e31331c3ac"),
            "PickupRegression",
            position,
            null)) {
      ServerPlayer player = session.player;
      require(ItemPhysic.CONFIG.pickup.customPickup, "Custom pickup must be active");
      verifyPickup(level, player, position);
      report.addProperty("deliberate_crouch_and_material_pickup", true);
      verifyRejectedRequests(level, player, position);
      report.addProperty("distance_wall_target_and_disabled_guards", true);
      verifyVisibleEdge(level, player, position);
      report.addProperty("view_ray_exposed_edge_and_covered_edge", true);
      verifyPickupDelays(level, player, position);
      report.addProperty("never_pickable_rejected_finite_delay_preserved", true);
      verifyPartialPickup(level, player, position);
      report.addProperty("partial_stack_actual_post_event_delta", true);
      verifyBackpack(level, player, position);
      report.addProperty("native_backpack_pickup_filter_partial_and_full", true);
      verifyThrows(level, player);
      report.addProperty("bounded_native_toss_and_listener_owned_cancellation", true);
      verifyAutomation(level, position);
      report.addProperty("hopper_and_create_depot_item_conservation", true);
      verifyMaterials(level, position);
      report.addProperty("buoyancy_fire_cactus_and_native_despawn", true);
      verifyShipCoordinates(level, player, position);
      report.addProperty("rotated_ship_plot_coordinates", true);
    }
    try {
      Files.createDirectories(output.toAbsolutePath().getParent());
      Files.writeString(
          output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot write pickup report", failure);
    }
  }

  private static void verifyPickup(ServerLevel level, ServerPlayer player, Vec3 position) {
    player.getInventory().clearContent();
    player.setShiftKeyDown(false);
    player.setPose(Pose.STANDING);
    ItemEntity manual = item(level, position.add(0, 0, 1), new ItemStack(Items.BONE, 7));
    try {
      manual.playerTouch(player);
      require(manual.getItem().getCount() == 7, "Ordinary contact collected a manual-only item");
      targetedPickup(player, manual);
      require(
          manual.isRemoved() && player.getInventory().countItem(Items.BONE) == 7,
          "Native targeted pickup did not transfer exactly seven items");
    } finally {
      manual.discard();
    }
    ItemEntity crouched = item(level, position.add(0, 0, 1), new ItemStack(Items.BONE, 5));
    try {
      player.setShiftKeyDown(true);
      player.setPose(Pose.CROUCHING);
      crouched.playerTouch(player);
      require(
          crouched.isRemoved() && player.getInventory().countItem(Items.BONE) == 12,
          "Crouching did not permit ordinary nearby pickup");
    } finally {
      player.setShiftKeyDown(false);
      player.setPose(Pose.STANDING);
      crouched.discard();
    }
    for (var material : new net.minecraft.world.item.Item[] {Items.IRON_INGOT, Items.OAK_LOG}) {
      ItemEntity automatic = item(level, position.add(0, 0, 1), new ItemStack(material, 3));
      try {
        automatic.playerTouch(player);
        require(
            automatic.isRemoved() && player.getInventory().countItem(material) == 3,
            "Mapped automatic pickup exception failed for " + material);
      } finally {
        automatic.discard();
      }
    }
  }

  private static void verifyRejectedRequests(
      ServerLevel level, ServerPlayer player, Vec3 position) {
    ItemEntity remote = item(level, position.add(0, 0, 20), new ItemStack(Items.BONE, 7));
    ItemEntity nearby = item(level, position.add(0, 1, 3), new ItemStack(Items.BONE, 4));
    BlockPos wall = BlockPos.containing(position.add(0, 1, 1));
    require(level.isEmptyBlock(wall), "Pickup wall fixture must start in air");
    try {
      targetedPickup(player, remote);
      require(remote.getItem().getCount() == 7, "Out-of-range UUID request collected remote items");
      level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
      targetedPickup(player, nearby);
      require(nearby.getItem().getCount() == 4, "Pickup crossed a solid wall");
      level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
      player.lookAt(EntityAnchorArgument.Anchor.EYES, position.add(0, 1, -3));
      new PickupPacket(nearby.getUUID(), true).executeServer(player);
      require(
          nearby.getItem().getCount() == 4, "A UUID packet collected a drop outside the view ray");
      nearby.setTarget(UUID.fromString("f8888df0-a997-4b8d-9bc1-ccca46ae5674"));
      targetedPickup(player, nearby);
      require(nearby.getItem().getCount() == 4, "Pickup crossed another player's target lock");
      nearby.setTarget(player.getUUID());
      boolean enabled = ItemPhysic.CONFIG.pickup.customPickup;
      try {
        ItemPhysic.CONFIG.pickup.customPickup = false;
        targetedPickup(player, nearby);
        require(nearby.getItem().getCount() == 4, "Disabled custom pickup accepted a packet");
      } finally {
        ItemPhysic.CONFIG.pickup.customPickup = enabled;
      }
      targetedPickup(player, nearby);
      require(nearby.isRemoved(), "Unobstructed in-range owner pickup failed");
    } finally {
      level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
      remote.discard();
      nearby.discard();
    }
  }

  private static void verifyPartialPickup(ServerLevel level, ServerPlayer player, Vec3 position) {
    for (int slot = 0; slot < 36; slot++)
      player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
    player.getInventory().setItem(0, new ItemStack(Items.BONE, 63));
    ItemEntity partial = item(level, position.add(0, 0, 2), new ItemStack(Items.BONE, 10));
    AtomicInteger received = new AtomicInteger();
    AtomicInteger posts = new AtomicInteger();
    StringBuilder eventCounts = new StringBuilder();
    Consumer<ItemEntityPickupEvent.Post> listener =
        event -> {
          if (event.getPlayer() == player) {
            posts.incrementAndGet();
            eventCounts
                .append(" original=")
                .append(event.getOriginalStack().getCount())
                .append(" remaining=")
                .append(event.getCurrentStack().getCount());
            received.addAndGet(
                event.getOriginalStack().getCount() - event.getCurrentStack().getCount());
          }
        };
    NeoForge.EVENT_BUS.addListener(listener);
    try {
      aimAt(player, partial);
      boolean allowed = PickupRequests.canPickup(player, partial);
      int statsBefore = player.getStats().getValue(Stats.ITEM_PICKED_UP.get(Items.BONE));
      new PickupPacket(partial.getUUID(), true).executeServer(player);
      require(
          player.getInventory().countItem(Items.BONE) == 64
              && partial.getItem().getCount() == 9
              && received.get() == 1,
          "Partial pickup must transfer and announce one item, leaving nine in the world: allowed="
              + allowed
              + ", inventory="
              + player.getInventory().countItem(Items.BONE)
              + ", remaining="
              + partial.getItem().getCount()
              + ", posts="
              + posts.get()
              + ", announced="
              + received.get()
              + ", statisticDelta="
              + (player.getStats().getValue(Stats.ITEM_PICKED_UP.get(Items.BONE)) - statsBefore)
              + ", eye="
              + player.getEyePosition()
              + ", view="
              + player.getViewVector(1)
              + ", itemBox="
              + partial.getBoundingBox()
              + ", custom="
              + ItemPhysic.CONFIG.pickup.customPickup
              + ", range="
              + ItemPhysic.CONFIG.pickup.maximumPickupRange
              + ", tolerance="
              + ItemPhysic.CONFIG.pickup.hitboxIncrease
              + ", eventStacks="
              + eventCounts);
      targetedPickup(player, partial);
      require(
          partial.getItem().getCount() == 9 && received.get() == 1,
          "Full inventory changed the remaining stack or repeated the pickup announcement");
    } finally {
      NeoForge.EVENT_BUS.unregister(listener);
      partial.discard();
      player.getInventory().clearContent();
    }
  }

  private static void verifyVisibleEdge(ServerLevel level, ServerPlayer player, Vec3 position) {
    player.getInventory().clearContent();
    Vec3 edgePosition = position.add(0.45, 0, 0);
    player.absMoveTo(edgePosition.x, edgePosition.y, edgePosition.z, 0, 0);
    Vec3 eye = player.getEyePosition();
    BlockPos wall = BlockPos.containing(eye.add(0, 0, 1));
    require(
        level.isEmptyBlock(wall) && level.isEmptyBlock(wall.east()),
        "Edge-visibility fixture must start in air");
    ItemEntity exposed =
        item(
            level,
            new Vec3(edgePosition.x, eye.y, edgePosition.z + 2),
            new ItemStack(Items.BONE, 3));
    ItemEntity covered =
        item(
            level,
            new Vec3(edgePosition.x, eye.y, edgePosition.z + 2),
            new ItemStack(Items.BONE, 5));
    try {
      level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
      Vec3 center = exposed.getBoundingBox().getCenter();
      require(
          level
                  .clip(
                      new ClipContext(
                          eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                  .getType()
              == HitResult.Type.BLOCK,
          "Visibility fixture must hide the item center behind the wall");
      Vec3 edge =
          center.add(
              exposed.getBbWidth() / 2 + ItemPhysic.CONFIG.pickup.hitboxIncrease - 0.02, 0, 0);
      player.lookAt(EntityAnchorArgument.Anchor.EYES, edge);
      new PickupPacket(exposed.getUUID(), true).executeServer(player);
      require(
          exposed.isRemoved() && player.getInventory().countItem(Items.BONE) == 3,
          "An exposed target-box edge must remain collectable when its center is hidden");
      level.setBlockAndUpdate(wall.east(), Blocks.STONE.defaultBlockState());
      player.lookAt(EntityAnchorArgument.Anchor.EYES, edge);
      new PickupPacket(covered.getUUID(), true).executeServer(player);
      require(
          covered.getItem().getCount() == 5 && player.getInventory().countItem(Items.BONE) == 3,
          "The same view ray must reject the drop when the exposed edge is covered");
    } finally {
      exposed.discard();
      covered.discard();
      level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
      level.setBlockAndUpdate(wall.east(), Blocks.AIR.defaultBlockState());
      player.absMoveTo(position.x, position.y, position.z, 0, 0);
      player.getInventory().clearContent();
    }
  }

  private static void verifyPickupDelays(ServerLevel level, ServerPlayer player, Vec3 position) {
    player.getInventory().clearContent();
    ItemEntity display = item(level, position.add(0, 0, 2), new ItemStack(Items.BONE, 6));
    ItemEntity delayed = item(level, position.add(0, 0, 2), new ItemStack(Items.BONE, 4));
    try {
      display.makeFakeItem();
      targetedPickup(player, display);
      require(
          display.getItem().getCount() == 6 && player.getInventory().countItem(Items.BONE) == 0,
          "Native never-pickable display copies must reject manual pickup");
      delayed.setPickUpDelay(40);
      targetedPickup(player, delayed);
      require(
          delayed.isRemoved() && player.getInventory().countItem(Items.BONE) == 4,
          "Native manual pickup must retain its intentional finite-delay bypass");
    } finally {
      display.discard();
      delayed.discard();
      player.getInventory().clearContent();
    }
  }

  private static void verifyBackpack(ServerLevel level, ServerPlayer player, Vec3 position) {
    player.getInventory().clearContent();
    ItemStack backpack =
        new ItemStack(
            BuiltInRegistries.ITEM.get(ResourceLocation.parse("sophisticatedbackpacks:backpack")));
    ItemStack upgrade =
        new ItemStack(
            BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("sophisticatedbackpacks:pickup_upgrade")));
    require(!backpack.isEmpty() && !upgrade.isEmpty(), "Pinned backpack/upgrade items are absent");
    IStorageWrapper wrapper;
    try {
      wrapper =
          (IStorageWrapper)
              Class.forName("net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper")
                  .getMethod("fromStack", ItemStack.class)
                  .invoke(null, backpack);
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException("Cannot open pinned backpack wrapper", failure);
    }
    wrapper.onInit(level);
    require(
        wrapper.getUpgradeHandler().insertItem(0, upgrade, false).isEmpty(),
        "Cannot install native pickup upgrade");
    PickupUpgradeWrapper pickup =
        (PickupUpgradeWrapper) wrapper.getUpgradeHandler().getSlotWrappers().get(0);
    pickup.getFilterLogic().setAllowList(false);
    player.setItemSlot(EquipmentSlot.CHEST, backpack);
    var inventory = wrapper.getInventoryHandler();
    ItemEntity first = item(level, position.add(0, 0, 2), new ItemStack(Items.BONE, 10));
    ItemEntity partial = item(level, position.add(0, 0, 2), new ItemStack(Items.BONE, 10));
    try {
      targetedPickup(player, first);
      require(
          first.getItem().isEmpty()
              && inventory.getStackInSlot(0).is(Items.BONE)
              && inventory.getStackInSlot(0).getCount() == 10
              && player.getInventory().countItem(Items.BONE) == 0,
          "Native backpack pickup hook did not receive the manual collection");
      for (int slot = 0; slot < inventory.getSlots(); slot++)
        inventory.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 64));
      inventory.setStackInSlot(0, new ItemStack(Items.BONE, 63));
      targetedPickup(player, partial);
      require(
          inventory.getStackInSlot(0).getCount() == 64
              && partial.getItem().getCount() == 9
              && player.getInventory().countItem(Items.BONE) == 0,
          "Partial backpack transfer lost or duplicated the remainder");
      targetedPickup(player, partial);
      require(
          partial.isRemoved() && player.getInventory().countItem(Items.BONE) == 9,
          "Full backpack blocked the player's remaining inventory capacity");
      for (int slot = 0; slot < inventory.getSlots(); slot++)
        inventory.setStackInSlot(slot, ItemStack.EMPTY);
      pickup.getFilterLogic().setAllowList(true);
      pickup.getFilterLogic().getFilterHandler().setStackInSlot(0, new ItemStack(Items.FEATHER));
      ItemEntity filtered = item(level, position.add(0, 0, 2), new ItemStack(Items.BONE, 3));
      try {
        targetedPickup(player, filtered);
        require(
            filtered.isRemoved()
                && inventory.getStackInSlot(0).isEmpty()
                && player.getInventory().countItem(Items.BONE) == 12,
            "Backpack filter must preserve ordinary inventory pickup");
      } finally {
        filtered.discard();
      }
    } finally {
      first.discard();
      partial.discard();
      player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
      player.getInventory().clearContent();
    }
  }

  private static void verifyThrows(ServerLevel level, ServerPlayer player) {
    player.getInventory().clearContent();
    player.getInventory().setItem(player.getInventory().selected, new ItemStack(Items.BONE, 8));
    for (int power : new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
      new DropPacket(false, power).executeServer(player);
      require(player.getInventory().countItem(Items.BONE) == 8, "Invalid toss removed inventory");
    }
    boolean enabled = ItemPhysic.CONFIG.throwConfig.enabled;
    try {
      ItemPhysic.CONFIG.throwConfig.enabled = false;
      new DropPacket(false, 1).executeServer(player);
      require(player.getInventory().countItem(Items.BONE) == 8, "Disabled toss removed inventory");
    } finally {
      ItemPhysic.CONFIG.throwConfig.enabled = enabled;
    }
    Consumer<ItemTossEvent> listener =
        event -> {
          if (event.getPlayer() == player) {
            ItemStack returned = event.getEntity().getItem().copy();
            require(player.getInventory().add(returned), "Cannot return canceled toss fixture");
            event.setCanceled(true);
          }
        };
    NeoForge.EVENT_BUS.addListener(listener);
    try {
      require(
          !player.drop(true) && player.getInventory().countItem(Items.BONE) == 8,
          "Native Q-drop cancellation leaves item restoration to the listener");
      new DropPacket(true, 6).executeServer(player);
      require(
          player.getInventory().countItem(Items.BONE) == 8,
          "Native cancellation listener must return the removed stack exactly once");
    } finally {
      NeoForge.EVENT_BUS.unregister(listener);
    }
    var before = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4));
    new DropPacket(false, 6).executeServer(player);
    var dropped =
        level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4)).stream()
            .filter(entity -> !before.contains(entity))
            .toList();
    try {
      require(
          player.getInventory().countItem(Items.BONE) == 7
              && dropped.size() == 1
              && dropped.getFirst().getItem().getCount() == 1
              && dropped.getFirst().getDeltaMovement().length() < 3,
          "Valid charged throw must conserve one item and use bounded native velocity");
    } finally {
      dropped.forEach(ItemEntity::discard);
      player.getInventory().clearContent();
    }
  }

  private static void verifyAutomation(ServerLevel level, Vec3 position) {
    BlockPos hopperPosition = BlockPos.containing(position.add(5, 0, 0));
    BlockPos depotPosition = hopperPosition.east(2);
    require(
        level.isEmptyBlock(hopperPosition) && level.isEmptyBlock(depotPosition),
        "Automation fixture must start in air");
    level.setBlockAndUpdate(hopperPosition, Blocks.HOPPER.defaultBlockState());
    level.setBlockAndUpdate(depotPosition, AllBlocks.DEPOT.getDefaultState());
    ItemEntity hopperDrop =
        item(level, Vec3.atCenterOf(hopperPosition).add(0, 0.6, 0), new ItemStack(Items.BONE, 9));
    ItemEntity depotDrop =
        item(level, Vec3.atCenterOf(depotPosition).add(0, 0.4, 0), new ItemStack(Items.BONE, 11));
    try {
      var hopper = (HopperBlockEntity) level.getBlockEntity(hopperPosition);
      require(hopper != null, "Native hopper block entity is missing");
      require(
          HopperBlockEntity.suckInItems(level, hopper), "Hopper could not collect manual drops");
      require(
          hopperDrop.isRemoved() && hopper.countItem(Items.BONE) == 9,
          "Hopper lost or duplicated items under deliberate player pickup");
      AllBlocks.DEPOT.get().updateEntityAfterFallOn(level, depotDrop);
      var depot = (DepotBlockEntity) level.getBlockEntity(depotPosition);
      require(
          depot != null && depotDrop.isRemoved() && depot.getHeldItem().getCount() == 11,
          "Create depot landing did not conserve the complete drop");
      hopper.clearContent();
      depot.clearContent();
    } finally {
      hopperDrop.discard();
      depotDrop.discard();
      level.setBlockAndUpdate(hopperPosition, Blocks.AIR.defaultBlockState());
      level.setBlockAndUpdate(depotPosition, Blocks.AIR.defaultBlockState());
    }
  }

  private static void verifyMaterials(ServerLevel level, Vec3 position) {
    BlockPos water = BlockPos.containing(position.add(8, 0, 0));
    require(level.isEmptyBlock(water), "Liquid fixture must start in air");
    level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
    ItemEntity wood = item(level, Vec3.atCenterOf(water), new ItemStack(Items.OAK_LOG));
    ItemEntity iron = item(level, Vec3.atCenterOf(water), new ItemStack(Items.IRON_INGOT));
    ItemEntity flint = item(level, position.add(9, 0, 0), new ItemStack(Items.FLINT));
    try {
      wood.setDeltaMovement(Vec3.ZERO);
      iron.setDeltaMovement(Vec3.ZERO);
      ItemPhysicServer.updatePre(wood, wood.getRandom());
      ItemPhysicServer.updatePre(iron, iron.getRandom());
      require(
          wood.getDeltaMovement().y > 0 && iron.getDeltaMovement().y < 0,
          "Wood must rise and metal sink in native ItemPhysic water physics");
      require(
          ((ItemEntityExtender) flint).canBurn() && !((ItemEntityExtender) iron).canBurn(),
          "Reference flint combustion or protected metal mapping is missing");
      require(!iron.hurt(level.damageSources().cactus(), 1), "Cactus damaged protected drops");
      require(!iron.hurt(level.damageSources().onFire(), 1), "Fire damaged protected metal");
      CompoundTag saved = iron.saveWithoutId(new CompoundTag());
      saved.putShort("Age", (short) (iron.lifespan - 1));
      iron.load(saved);
      iron.tick();
      require(iron.isRemoved(), "Material protection disabled ordinary item despawning");
    } finally {
      wood.discard();
      iron.discard();
      flint.discard();
      level.setBlockAndUpdate(water, Blocks.AIR.defaultBlockState());
    }
  }

  private static void verifyShipCoordinates(ServerLevel level, ServerPlayer player, Vec3 position) {
    var container = SubLevelContainer.getContainer(level);
    Pose3d pose = new Pose3d();
    pose.position().set(position.x + 20, position.y, position.z);
    pose.orientation().rotateY(Math.PI / 3);
    ServerSubLevel ship = (ServerSubLevel) container.allocateNewSubLevel(pose);
    try {
      BlockPos local = ship.getPlot().getCenterBlock();
      ship.getPlot().newEmptyChunk(new ChunkPos(local));
      Vec3 plotPosition = Vec3.atCenterOf(local);
      Vec3 world = ship.logicalPose().transformPosition(plotPosition);
      ItemEntity item =
          new ItemEntity(
              level, plotPosition.x, plotPosition.y, plotPosition.z, new ItemStack(Items.BONE));
      player.absMoveTo(world.x, world.y, world.z - 2, 0, 0);
      aimAt(player, item);
      require(
          player.position().distanceToSqr(item.position()) > 1_000_000
              && PickupRequests.canPickup(player, item),
          "Server range/visibility validation rejected a nearby rotated ship-space drop");
      player.absMoveTo(world.x, world.y, world.z - 20, 0, 0);
      aimAt(player, item);
      require(!PickupRequests.canPickup(player, item), "Ship projection bypassed pickup reach");
    } finally {
      player.absMoveTo(position.x, position.y, position.z, 0, 0);
      container.removeSubLevel(ship, SubLevelRemovalReason.REMOVED);
    }
  }

  private static ItemEntity item(ServerLevel level, Vec3 position, ItemStack stack) {
    level.getChunkAt(BlockPos.containing(position));
    ItemEntity entity = new ItemEntity(level, position.x, position.y, position.z, stack);
    entity.setNoPickUpDelay();
    require(level.addFreshEntity(entity), "Cannot register dropped-item fixture");
    return entity;
  }

  private static void targetedPickup(ServerPlayer player, ItemEntity item) {
    aimAt(player, item);
    new PickupPacket(item.getUUID(), true).executeServer(player);
  }

  private static void aimAt(ServerPlayer player, ItemEntity item) {
    player.lookAt(
        EntityAnchorArgument.Anchor.EYES,
        PickupRequests.worldPosition(player.level(), item.getBoundingBox().getCenter()));
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
