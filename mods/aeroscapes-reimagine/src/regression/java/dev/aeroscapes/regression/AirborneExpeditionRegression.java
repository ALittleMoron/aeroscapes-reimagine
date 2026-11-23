package dev.aeroscapes.regression;

import com.chaosthedude.explorerscompass.util.StructureUtils;
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
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
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
    var compassTargets = StructureUtils.getAllowedStructureKeys(level);
    Site tetheredSite = null;
    for (String name :
        List.of(
            "airborne_derelict",
            "observation_balloon",
            "tethered_airship",
            "cargo_barge",
            "cliff_skiff",
            "salvage_tug",
            "survey_catamaran",
            "mail_cutter",
            "passenger_ferry",
            "weather_ship",
            "glasshouse_carrier")) {
      var id = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", name);
      var holder = registry.getHolder(id).orElseThrow();
      require(compassTargets.contains(id), spawn, "Explorer's Compass hides " + id);
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
      verifyFittings(level, site, start.getBoundingBox(), name);
      if (!site.tethered()
          && !name.equals("airborne_derelict")
          && !name.equals("observation_balloon")) {
        require(name.equals(tag.getString("kind")), pos, "Variant lost its saved kind");
        verifyShip(level, site, name);
        verifyNoDebris(level, start.getBoundingBox());
        LogUtils.getLogger()
            .info(
                "Natural airborne witness seed={} id={} origin={} facing={}",
                level.getSeed(),
                id,
                anchor,
                direction);
        continue;
      }
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

  private static void verifyShip(ServerLevel level, Site site, String name) {
    switch (name) {
      case "cargo_barge" -> {
        approach(level, site.ship(0, -1, -12), site.forward().getOpposite());
        stand(level, site.ship(0, -1, -12));
        step(level, site.ship(0, 0, -11), site.forward());
        for (int z = -10; z <= -1; z++) stand(level, site.ship(0, 0, z));
        step(level, site.ship(0, 0, 0), site.forward().getOpposite());
        for (int z = 1; z <= 3; z++) stand(level, site.ship(0, -1, z));
        for (int z = 6; z <= 9; z++) stand(level, site.ship(0, 0, z));
        nativeBlock(level, site.ship(0, 1, 10), "minecraft:dark_oak_planks");
        verifyBarrel(level, site.ship(1, 0, 3), "derelict_cargo");
        suspension(level, site, 4, 0, -5, 5);
      }
      case "cliff_skiff" -> {
        approach(level, site.ship(4, -1, 0), site.forward().getCounterClockWise());
        stand(level, site.ship(4, -1, 0));
        step(level, site.ship(3, 0, 0), site.forward().getClockWise());
        for (int x = 1; x <= 2; x++) stand(level, site.ship(x, 0, 0));
        for (int z = 1; z <= 4; z++) stand(level, site.ship(1, 0, z));
        verifyBarrel(level, site.ship(-1, 1, 4), "observation_balloon");
        suspension(level, site, 2, 0, -4, 3);
      }
      case "salvage_tug" -> {
        approach(level, site.ship(0, -1, 12), site.forward());
        stand(level, site.ship(0, -1, 12));
        step(level, site.ship(0, 0, 11), site.forward().getOpposite());
        for (int z = 5; z <= 10; z++) stand(level, site.ship(0, 0, z));
        for (int x = -2; x <= -1; x++) stand(level, site.ship(x, 0, 5));
        for (int z = -7; z <= -2; z++) stand(level, site.ship(0, 0, z));
        nativeBlock(level, site.ship(-1, 1, -6), "simulated:throttle_lever");
        verifyBarrel(level, site.ship(-3, 1, 5), "derelict_cargo");
        suspension(level, site, 3, 0, -6, 2);
        nativeBlock(level, site.ship(2, 0, 5), "create:gearbox");
        nativeBlock(level, site.ship(2, 5, 5), "create:gearbox");
        requireFloor(level, site.ship(2, 6, 5), "The crane boom has no mast shoe");
        for (int z = 6; z <= 9; z++) {
          nativeBlock(level, site.ship(2, 5, z), "create:metal_girder_encased_shaft");
          nativeBlock(level, site.ship(2, 6, z), "create:metal_girder");
        }
        nativeBlock(level, site.ship(2, 5, 10), "create:rope_pulley");
        requireFloor(level, site.ship(2, 6, 10), "The crane boom has no pulley shoe");
        nativeBlock(level, site.ship(2, 4, 10), "minecraft:chain");
        nativeBlock(level, site.ship(2, 3, 10), "minecraft:chain");
        nativeBlock(level, site.ship(2, 2, 10), "minecraft:lightning_rod");
      }
      case "survey_catamaran" -> {
        approach(level, site.ship(0, -1, 6), site.forward());
        for (int z = 4; z <= 6; z++) stand(level, site.ship(0, -1, z));
        step(level, site.ship(0, 0, 3), site.forward().getOpposite());
        for (int z = 0; z <= 2; z++) stand(level, site.ship(0, 0, z));
        verifyBarrel(level, site.ship(1, 1, 0), "observation_balloon");
        suspension(level, site, 4, 0, -6, 6);
      }
      case "mail_cutter" -> {
        approach(level, site.ship(-4, -1, 3), site.forward().getClockWise());
        stand(level, site.ship(-4, -1, 3));
        step(level, site.ship(-3, 0, 3), site.forward().getCounterClockWise());
        stand(level, site.ship(-2, 0, 3));
        for (int z = 1; z <= 3; z++) stand(level, site.ship(-1, 0, z));
        for (int z = -5; z <= 4; z++) stand(level, site.ship(0, 0, z));
        verifyBarrel(level, site.ship(1, 1, 1), "observation_balloon");
        suspension(level, site, 2, 4, -5, 4);
      }
      case "passenger_ferry" -> {
        approach(level, site.ship(7, -1, 0), site.forward().getCounterClockWise());
        stand(level, site.ship(7, -1, 0));
        step(level, site.ship(6, 0, 0), site.forward().getClockWise());
        for (int x = 0; x <= 5; x++) stand(level, site.ship(x, 0, 0));
        for (int z = -5; z <= 5; z++) stand(level, site.ship(0, 0, z));
        verifyBarrel(level, site.ship(-1, 1, 3), "derelict_cargo");
        suspension(level, site, 4, 4, -4, 4);
      }
      case "weather_ship" -> {
        ladderApproach(level, site, 6, 4, 8);
        for (int z = 1; z <= 4; z++) stand(level, site.ship(0, 0, z));
        stand(level, site.ship(-1, 0, 1));
        verifyBarrel(level, site.ship(-2, 1, 1), "observation_balloon");
        verifyBurner(level, site.ship(0, 1, 0), site.ship(0, 20, 0));
        suspension(level, site, 2, 0, -2, 2);
      }
      case "glasshouse_carrier" -> {
        ladderApproach(level, site, 10, 8, 11);
        for (int z = 4; z <= 8; z++) stand(level, site.ship(0, 0, z));
        stand(level, site.ship(0, 0, -5));
        for (int side : new int[] {-1, 1}) {
          for (int z = -4; z <= 4; z++) {
            if (z == 0) continue;
            nativeBlock(level, site.ship(side * 3, 3, z), "create:framed_glass");
            nativeBlock(level, site.ship(side * 2, 3, z), "create:framed_glass");
            nativeBlock(level, site.ship(side * 2, 4, z), "create:framed_glass");
            nativeBlock(level, site.ship(side, 4, z), "create:framed_glass");
            nativeBlock(level, site.ship(side, 5, z), "create:framed_glass");
          }
        }
        verifyBarrel(level, site.ship(-1, 1, 4), "airborne_glasshouse_stock");
        suspension(level, site, 5, 0, -4, 4);
      }
      default -> throw new IllegalArgumentException("Unexpected flying ship " + name);
    }
  }

  private static void verifyFittings(
      ServerLevel level, Site site, BoundingBox bounds, String name) {
    int helms = 0;
    int lanterns = 0;
    int panes = 0;
    var wheel =
        BuiltInRegistries.BLOCK
            .getOptional(ResourceLocation.parse("simulated:steering_wheel"))
            .orElseThrow();
    for (var pos :
        BlockPos.betweenClosed(
            bounds.minX(),
            bounds.minY(),
            bounds.minZ(),
            bounds.maxX(),
            bounds.maxY(),
            bounds.maxZ())) {
      var state = level.getBlockState(pos);
      if (state.is(wheel)) {
        helms++;
        require(
            state.getValue(BlockStateProperties.HORIZONTAL_FACING) == site.forward(),
            pos,
            "Wheel faces away from its pilot");
        requireFloor(level, pos.below(), "Wheel has no deck support");
        stand(level, pos.below().relative(site.forward()));
      }
      if (state.is(Blocks.LANTERN)) {
        lanterns++;
        require(state.canSurvive(level, pos), pos, "Lantern lacks physical support");
      }
      if (state.getBlock() instanceof IronBarsBlock) {
        panes++;
        var above = pos.above();
        var abovePane = level.getBlockState(above);
        require(
            abovePane.getBlock() instanceof IronBarsBlock
                || abovePane.isFaceSturdy(level, above, Direction.DOWN),
            pos,
            "Cabin pane has no contact with its header");
        for (Direction direction : Direction.Plane.HORIZONTAL) {
          var adjacent = pos.relative(direction);
          var neighbor = level.getBlockState(adjacent);
          boolean connects =
              neighbor.getBlock() instanceof IronBarsBlock
                  || neighbor.isFaceSturdy(level, adjacent, direction.getOpposite());
          require(
              state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction)) == connects,
              pos,
              "Cabin pane connection does not meet its neighbor " + direction);
        }
      }
    }
    int expectedHelms =
        switch (name) {
          case "observation_balloon", "weather_ship" -> 0;
          default -> 1;
        };
    int expectedLanterns =
        switch (name) {
          case "tethered_airship" -> 3;
          case "airborne_derelict", "cargo_barge", "salvage_tug", "passenger_ferry" -> 2;
          default -> 1;
        };
    boolean glazed =
        switch (name) {
          case "cargo_barge", "salvage_tug", "mail_cutter", "passenger_ferry" -> true;
          default -> false;
        };
    require(helms == expectedHelms, site.origin(), "Missing or duplicated helm");
    require(lanterns == expectedLanterns, site.origin(), "Missing or duplicated lantern");
    require((panes > 0) == glazed, site.origin(), "Unexpected cabin glazing");
  }

  private static void stand(ServerLevel level, BlockPos floor) {
    requireFloor(level, floor, "Boarding floor is incomplete");
    headroom(level, floor);
  }

  private static void approach(ServerLevel level, BlockPos landing, Direction outside) {
    for (int distance = 1; distance <= 2; distance++) {
      for (int y = 1; y <= 2; y++) {
        var air = landing.relative(outside, distance).above(y);
        require(level.isEmptyBlock(air), air, "Flight approach is obstructed");
      }
    }
  }

  private static void verifyNoDebris(ServerLevel level, BoundingBox bounds) {
    for (var pos :
        BlockPos.betweenClosed(
            bounds.minX(),
            bounds.minY(),
            bounds.minZ(),
            bounds.maxX(),
            bounds.maxY() + 1,
            bounds.maxZ())) {
      var id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
      require(!id.getNamespace().equals("rocks"), pos, "Natural debris generated on the airframe");
    }
  }

  private static void step(ServerLevel level, BlockPos pos, Direction climb) {
    var block = level.getBlockState(pos);
    require(
        block.getBlock() instanceof StairBlock
            && block.getValue(StairBlock.FACING) == climb
            && block.getValue(StairBlock.HALF) == Half.BOTTOM,
        pos,
        "Boarding stair does not climb toward deck");
    headroom(level, pos);
  }

  private static void headroom(ServerLevel level, BlockPos floor) {
    for (int y = 1; y <= 2; y++) {
      var space = floor.above(y);
      require(
          level.getBlockState(space).getCollisionShape(level, space).isEmpty(),
          space,
          "Boarding path has no headroom");
    }
  }

  private static void ladderApproach(ServerLevel level, Site site, int ladder, int deck, int end) {
    approach(level, site.ship(0, -3, end), site.forward());
    for (int z = ladder + 1; z <= end; z++) stand(level, site.ship(0, -3, z));
    for (int y = -2; y <= 1; y++) {
      var pos = site.ship(0, y, ladder);
      var state = level.getBlockState(pos);
      require(
          state.is(Blocks.LADDER) && state.canSurvive(level, pos),
          pos,
          "Missing or unsupported boarding rung");
    }
    for (int z = deck; z <= ladder; z++) stand(level, site.ship(1, 0, z));
  }

  private static void suspension(ServerLevel level, Site site, int width, int base, int... beams) {
    for (int side : new int[] {-1, 1}) {
      for (int z : beams) {
        int x = side * width;
        requireFloor(level, site.ship(x, base, z), "Lift rig lacks a full mounting surface");
        nativeBlock(level, site.ship(x, base + 1, z), "create:metal_girder");
        int y = base + 2;
        while (y < 20) {
          var pos = site.ship(x, y, z);
          var state = level.getBlockState(pos);
          if (!state.is(Blocks.CHAIN)) break;
          require(
              state.getValue(ChainBlock.AXIS) == Direction.Axis.Y,
              pos,
              "Suspension chain is sideways");
          y++;
        }
        var collar = site.ship(x, y, z);
        require(y > base + 2, collar, "No cable in suspension rig");
        requireFloor(level, collar, "Suspension chain lacks collar");
        var cell = collar.above();
        var skin = BuiltInRegistries.BLOCK.getKey(level.getBlockState(cell).getBlock());
        require(
            skin.getNamespace().equals("aeronautics") && skin.getPath().endsWith("_envelope"),
            cell,
            "Collar fails to meet the envelope");
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
            && level.isEmptyBlock(site.at(0, 2, 0))
            && level.isEmptyBlock(site.at(0, 2, 1)),
        marker,
        "Mooring lantern or clear rope deck is missing");
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
