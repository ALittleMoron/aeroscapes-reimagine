package dev.aeroscapes.regression;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.aeroscapes.sky.cargo.LostCargoBalloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;

final class AirborneExpeditionRegression {
  private AirborneExpeditionRegression() {}

  static void verify(ServerLevel level) {
    var spawn = level.getSharedSpawnPos();
    var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    var serialization = StructurePieceSerializationContext.fromLevel(level);
    var generator = level.getChunkSource().getGenerator();
    Site tetheredSite = null;
    for (String name : List.of("airborne_derelict", "observation_balloon", "tethered_airship")) {
      var id = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", name);
      var holder = registry.getHolder(id).orElseThrow();
      var found =
          generator.findNearestMapStructure(level, HolderSet.direct(holder), spawn, 100, false);
      require(found != null && found.getSecond().equals(holder), spawn, "Search failed for " + id);
      var pos = found.getFirst();
      var start = level.getChunk(pos).getStartForStructure(holder.value());
      require(start != null && start.isValid(), pos, "Missing natural start for " + id);
      loadFootprint(level, start.getBoundingBox().inflatedBy(8));
      var tag = start.getPieces().getFirst().createTag(serialization);
      var anchor = BlockPos.of(tag.getLong("origin"));
      var direction = Direction.from2DDataValue(tag.getInt("facing"));
      var site = new Site(anchor, direction, name.equals("tethered_airship"));
      var observation = name.equals("observation_balloon");
      var barrel = observation ? site.ship(-1, 1, 2) : site.ship(-2, 1, 3);
      verifyBarrel(level, barrel, observation ? "observation_balloon" : "derelict_cargo");
      if (observation) {
        verifyLookout(level, site);
      } else {
        verifyCourier(level, site);
      }
      if (site.tethered()) {
        verifyTether(level, site);
        tetheredSite = site;
      }
      LogUtils.getLogger()
          .info(
              "Natural airborne witness seed={} id={} origin={} facing={} barrel={}",
              level.getSeed(),
              id,
              anchor,
              direction,
              barrel);
      if (observation) verifyBalloon(level, site.ship(0, 4, 8));
    }
    var camp =
        NaturalStructureWitness.find(
            level, ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "survey_camp"));
    var campTag = camp.getPieces().getFirst().createTag(serialization);
    var anchor = BlockPos.of(campTag.getLong("anchor"));
    var facing = Direction.from2DDataValue(campTag.getInt("facing"));
    var signalPos = new Site(anchor, facing, false).at(1, 1, 2);
    var signal = level.getBlockState(signalPos);
    require(
        signal.is(Blocks.CAMPFIRE)
            && signal.getValue(CampfireBlock.LIT)
            && signal.getValue(CampfireBlock.SIGNAL_FIRE)
            && level.getBlockState(signalPos.below()).is(Blocks.HAY_BLOCK),
        signalPos,
        "Survey camp missing its lit signal");
    if (tetheredSite == null) throw new IllegalStateException("No tethered courier found");
    AirborneMooringRegression.awaitLive(
        level,
        tetheredSite.winch(),
        tetheredSite.connector(),
        () ->
            LogUtils.getLogger()
                .info("Airborne expedition regression complete: seed={}", level.getSeed()));
  }

  private static void loadFootprint(ServerLevel level, BoundingBox bounds) {
    for (int x = bounds.minX() >> 4; x <= bounds.maxX() >> 4; x++) {
      for (int z = bounds.minZ() >> 4; z <= bounds.maxZ() >> 4; z++) {
        level.getChunk(x, z);
      }
    }
  }

  private static void verifyBarrel(ServerLevel level, BlockPos pos, String path) {
    require(level.getBlockEntity(pos) instanceof RandomizableContainer, pos, "Missing cache");
    var container = (RandomizableContainer) level.getBlockEntity(pos);
    require(
        container.getLootTable() != null
            && container
                .getLootTable()
                .location()
                .toString()
                .equals("aeroscapes_reimagine:chests/" + path),
        pos,
        "Cache has no shared loot table");
    container.unpackLootTable(null);
    int count = 0;
    for (int slot = 0; slot < container.getContainerSize(); slot++) {
      count += container.getItem(slot).getCount();
    }
    require(count >= 4 && container.getLootTable() == null, pos, "Shared cache did not roll");
    container.unpackLootTable(null);
    int rechecked = 0;
    for (int slot = 0; slot < container.getContainerSize(); slot++) {
      rechecked += container.getItem(slot).getCount();
    }
    require(rechecked == count, pos, "Shared cache rolled more than once");
  }

  private static void verifyCourier(ServerLevel level, Site site) {
    nativeBlock(level, site.ship(0, 17, 0), "aeronautics:light_blue_envelope");
    nativeBlock(level, site.ship(6, 12, 0), "aeronautics:white_envelope");
    nativeBlock(level, site.ship(0, 7, 0), "aeronautics:cyan_envelope");
    var middle = site.ship(0, 12, 0);
    require(level.getBlockState(middle).isAir(), middle, "Filled courier gas cell");
    require(
        level.getBlockState(site.ship(2, 0, -11)).isAir()
            && level.getBlockState(site.ship(4, -1, 0)).is(Blocks.SPRUCE_STAIRS)
            && level.getBlockState(site.ship(3, -2, 0)).is(Blocks.DARK_OAK_STAIRS),
        site.origin(),
        "Courier hull lost its taper or lower chine");
    nativeBlock(level, site.ship(0, 1, -5), "simulated:steering_wheel");
    nativeBlock(level, site.ship(-1, 1, -5), "simulated:throttle_lever");
    verifyBurner(level, site.ship(0, 1, -1), site.ship(0, 16, -1));
    nativeBlock(level, site.ship(0, 1, 5), "simulated:brown_portable_engine");
    nativeBlock(level, site.ship(0, 1, 6), "create:shaft");
    nativeBlock(level, site.ship(0, 1, 7), "create:gearbox");
    var missingCoupling = site.ship(0, 1, 8);
    require(
        level.getBlockState(missingCoupling).isAir(),
        missingCoupling,
        "Courier drive was completed");
    nativeBlock(level, site.ship(0, 1, 9), "create:andesite_encased_shaft");
    nativeBlock(level, site.ship(0, -2, 13), "aeronautics:propeller_bearing");
    for (int side : new int[] {-1, 1}) {
      for (int radius = 1; radius <= 2; radius++) {
        nativeBlock(level, site.ship(side * radius, -2, 14), "simulated:white_symmetric_sail");
        nativeBlock(level, site.ship(0, -2 + side * radius, 14), "simulated:white_symmetric_sail");
      }
      for (int z : new int[] {-5, 5}) {
        int x = side * 3;
        nativeBlock(level, site.ship(x, 1, z), "create:metal_girder");
        requireFloor(level, site.ship(x, 0, z), "Courier suspension foot lacks a full deck beam");
        verifyChain(level, site, x, z, 5);
        nativeBlock(level, site.ship(x, 6, z), "create:metal_girder");
        nativeBlock(level, site.ship(x, 7, z), "aeronautics:cyan_envelope");
      }
      for (int z : new int[] {0, 1}) {
        requireFloor(level, site.ship(side * 5, 0, z), "Missing boarding beam");
        for (int x = 5; x <= 9; x++) {
          for (int y = 1; y <= 2; y++) {
            var point = site.ship(side * x, y, z);
            require(level.getBlockState(point).isAir(), point, "Obstructed side boarding");
          }
        }
      }
    }
  }

  private static void verifyLookout(ServerLevel level, Site site) {
    nativeBlock(level, site.ship(5, 11, 0), "aeronautics:orange_envelope");
    nativeBlock(level, site.ship(4, 11, 3), "aeronautics:white_envelope");
    nativeBlock(level, site.ship(0, 6, 1), "aeronautics:yellow_envelope");
    var throat = site.ship(0, 6, 0);
    require(
        level.getBlockState(throat).isAir() && level.getBlockState(site.ship(5, 8, 0)).isAir(),
        throat,
        "Lookout burner throat is closed or the envelope lost its taper");
    verifyBurner(level, site.ship(0, 1, 0), site.ship(0, 16, 0));
    nativeBlock(level, site.ship(1, 1, 2), "create:brown_seat");
    for (int side : new int[] {-1, 1}) {
      nativeBlock(level, site.ship(side * 2, 1, 0), "create:hand_crank");
      nativeBlock(level, site.ship(side * 2, 0, 0), "create:gearbox");
      nativeBlock(level, site.ship(side * 3, 0, 0), "create:andesite_encased_shaft");
      nativeBlock(level, site.ship(side * 4, 0, 0), "aeronautics:wooden_propeller");
      for (int z : new int[] {-1, 1}) {
        int x = side * 2;
        requireFloor(level, site.ship(x, 0, z), "Lookout suspension foot lacks a full deck beam");
        requireFloor(level, site.ship(x, 1, z), "Lookout suspension lacks a copper shoe");
        verifyChain(level, site, x, z, 5);
        requireFloor(level, site.ship(x, 6, z), "Lookout suspension lacks a copper collar");
        nativeBlock(level, site.ship(x, 7, z), "aeronautics:orange_envelope");
      }
    }
  }

  private static void verifyChain(ServerLevel level, Site site, int x, int z, int top) {
    for (int y = 2; y <= top; y++) {
      var position = site.ship(x, y, z);
      require(
          level.getBlockState(position).is(Blocks.CHAIN), position, "Suspension chain is broken");
    }
  }

  private static void verifyBurner(ServerLevel level, BlockPos position, BlockPos interior) {
    require(
        level.getBlockEntity(position) instanceof HotAirBurnerBlockEntity,
        position,
        "Missing native burner");
    var burner = (HotAirBurnerBlockEntity) level.getBlockEntity(position);
    burner.doRaycast();
    require(
        !burner.canOutputGas() && interior.equals(burner.getCastPosition()),
        position,
        "Burner starts powered or its gas path misses the interior: " + burner.getCastPosition());
  }

  private static void requireFloor(ServerLevel level, BlockPos position, String reason) {
    require(
        level.getBlockState(position).isFaceSturdy(level, position, Direction.UP),
        position,
        reason);
  }

  private static void nativeBlock(ServerLevel level, BlockPos position, String id) {
    require(
        BuiltInRegistries.BLOCK
            .getKey(level.getBlockState(position).getBlock())
            .toString()
            .equals(id),
        position,
        "Expected " + id + ", got " + level.getBlockState(position));
  }

  private static void verifyTether(ServerLevel level, Site site) {
    for (int x : new int[] {-1, 1}) {
      for (int z : new int[] {-2, 1}) {
        requireFloor(level, site.at(x, 0, z), "Mooring landing has an unsupported corner");
      }
    }
    var marker = site.at(0, 2, -1);
    require(
        level.getBlockState(marker).is(Blocks.STRIPPED_SPRUCE_WOOD)
            && level.getBlockState(marker.below()).isFaceSturdy(level, marker.below(), Direction.UP)
            && level.getBlockState(marker.above()).is(Blocks.LANTERN)
            && level.getBlockState(site.at(0, 2, 0)).is(Blocks.CHAIN)
            && level.getBlockState(site.at(0, 2, 1)).is(Blocks.CHAIN),
        marker,
        "Torn cable remnant is disconnected from its post");
    var stand = site.at(1, 1, 1);
    require(
        level.getBlockState(stand).isFaceSturdy(level, stand, Direction.UP)
            && level.getBlockState(stand.above()).getCollisionShape(level, stand.above()).isEmpty()
            && level
                .getBlockState(stand.above(2))
                .getCollisionShape(level, stand.above(2))
                .isEmpty(),
        stand,
        "No standing room at the mooring deck");
    var grab = site.ship(1, 0, -11);
    var eye = Vec3.atBottomCenterOf(stand.above()).add(0, 1.62, 0);
    var target = Vec3.atCenterOf(grab);
    var hit =
        level.clip(
            new ClipContext(
                eye,
                target,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()));
    require(
        eye.distanceTo(target) < 11
            && hit.getType() == HitResult.Type.BLOCK
            && hit.getBlockPos().equals(grab)
            && level.getBlockState(grab).is(Blocks.STRIPPED_SPRUCE_LOG),
        grab,
        "Hook line missed the timber; first ray hit " + hit.getBlockPos());
    for (int y = -2; y <= 1; y++) {
      var position = site.ship(0, y, -11);
      var rung = level.getBlockState(position);
      require(
          rung.is(Blocks.LADDER) && rung.canSurvive(level, position),
          position,
          "Unsupported bow ladder rung");
    }
    require(
        level.getBlockState(site.ship(1, 1, -10)).isAir()
            && level.getBlockState(site.ship(1, -1, -11)).is(Blocks.WAXED_EXPOSED_CUT_COPPER),
        grab,
        "The bow ladder is unsupported or its deck exit is blocked");
    AirborneMooringRegression.verifySaved(level, site.winch(), site.connector());
    LogUtils.getLogger()
        .info(
            "Courier mooring seed={} winch={} connector={} stand={} hook={}",
            level.getSeed(),
            site.winch(),
            site.connector(),
            stand,
            grab);
  }

  private static void verifyBalloon(ServerLevel level, BlockPos position) {
    var origin = Vec3.atCenterOf(position);
    var chunk = new ChunkPos(position);
    ChunkPos.rangeClosed(chunk, 1).forEach(pos -> level.getChunk(pos.x, pos.z));
    var balloon = LostCargoBalloon.TYPE.get().create(level);
    require(balloon != null, position, "Lost cargo balloon was not registered");
    balloon.setPos(origin);
    require(level.addFreshEntity(balloon), position, "Balloon rejected by server");
    balloon.tick();
    require(
        balloon.position().distanceToSqr(origin) > 0 && balloon.getY() < origin.y,
        position,
        "Balloon did not descend and drift");
    var arrow = EntityType.ARROW.create(level);
    require(arrow != null, position, "Could not construct arrow fixture");
    var source = level.damageSources().arrow(arrow, null);
    var dropSpace = balloon.getBoundingBox().inflate(2);
    require(
        balloon.canBeHitByProjectile() && balloon.hurt(source, 3) && balloon.isRemoved(),
        position,
        "Arrow did not release the cargo");
    var drops = level.getEntitiesOfClass(ItemEntity.class, dropSpace);
    int first = drops.stream().mapToInt(drop -> drop.getItem().getCount()).sum();
    require(
        first >= 4 && !balloon.hurt(source, 3),
        position,
        "Cargo was missing or balloon accepted a second arrow");
    drops.forEach(ItemEntity::discard);

    var direct = LostCargoBalloon.TYPE.get().create(level);
    require(direct != null, position, "Could not create direct cargo fixture");
    direct.setPos(origin);
    var collector = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "CargoFixture"));
    try {
      collector.setPos(origin.add(1.5, 0, 0));
      require(
          direct.interact(collector, InteractionHand.MAIN_HAND).consumesAction()
              && direct.isRemoved()
              && collector.getInventory().items.stream().mapToInt(stack -> stack.getCount()).sum()
                  >= 4
              && direct.interact(collector, InteractionHand.MAIN_HAND) == InteractionResult.PASS,
          position,
          "Direct collection did not give one share of cargo");
    } finally {
      collector.getTextFilter().leave();
      collector.getAdvancements().stopListening();
    }

    var restored = LostCargoBalloon.TYPE.get().create(level);
    require(restored != null, position, "Could not create cargo persistence fixture");
    var saved = balloon.saveWithoutId(new CompoundTag());
    int elapsed = saved.getInt("DriftTicks");
    restored.load(saved);
    restored.tick();
    require(
        elapsed == 1
            && restored.saveWithoutId(new CompoundTag()).getInt("DriftTicks") == elapsed + 1,
        position,
        "Drift lifetime was reset when the balloon loaded");
  }

  private record Site(BlockPos origin, Direction forward, boolean tethered) {
    BlockPos at(int x, int y, int z) {
      return origin.relative(forward, z).relative(forward.getCounterClockWise(), x).above(y);
    }

    BlockPos ship(int x, int y, int z) {
      return tethered ? at(x, y + 8, z + 20) : at(x, y, z);
    }

    BlockPos winch() {
      return at(-1, 2, 1);
    }

    BlockPos connector() {
      return ship(-1, 1, -11);
    }
  }

  private static void require(boolean valid, BlockPos pos, String message) {
    if (!valid) throw new IllegalStateException(message + " at " + pos);
  }
}
