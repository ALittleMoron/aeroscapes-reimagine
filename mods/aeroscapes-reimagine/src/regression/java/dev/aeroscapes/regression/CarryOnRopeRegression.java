package dev.aeroscapes.regression;

import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.index.SimBlocks;
import dev.simulated_team.simulated.index.SimItems;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class CarryOnRopeRegression {
  private static final String CARRY_ON = "tschipp.carryon.common.carry.";
  private static final String MARKER = "aeroscapes:carry_fixture";

  private CarryOnRopeRegression() {}

  static void verify(ServerLevel level) {
    var origin = level.getSharedSpawnPos().offset(40, 0, 42).atY(level.getMaxBuildHeight() - 14);
    try (var session =
        TestShipPlayerSession.create(
            level, UUID.randomUUID(), "CarryOnRope", Vec3.atBottomCenterOf(origin.north()), null)) {
      var player = session.player;
      player.setGameMode(GameType.SURVIVAL);
      ProgressionRegression.listPlayer(level, player, true);
      try {
        Method carryData =
            Class.forName(CARRY_ON + "CarryOnDataManager").getMethod("getCarryData", Player.class);
        Method pickUp =
            Class.forName(CARRY_ON + "PickupHandler")
                .getMethod(
                    "tryPickUpBlock",
                    ServerPlayer.class,
                    BlockPos.class,
                    Level.class,
                    BiFunction.class);
        Method place =
            Class.forName(CARRY_ON + "PlacementHandler")
                .getMethod(
                    "tryPlaceBlock",
                    ServerPlayer.class,
                    BlockPos.class,
                    Direction.class,
                    BiFunction.class);
        verifyMove(player, origin, false, carryData, pickUp, place);
        verifyMove(player, origin.south(8), true, carryData, pickUp, place);
      } finally {
        ProgressionRegression.listPlayer(level, player, false);
      }
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException(
          "Cannot run the pinned Carry On rope pickup and placement", exception);
    }
  }

  private static void verifyMove(
      ServerPlayer player,
      BlockPos winchPosition,
      boolean carryWinch,
      Method carryData,
      Method pickUp,
      Method place)
      throws ReflectiveOperationException {
    var level = player.serverLevel();
    var connectorPosition = winchPosition.east(3);
    var relocated = winchPosition.south(2).east(carryWinch ? 0 : 3);
    var source = carryWinch ? winchPosition : connectorPosition;
    var other = carryWinch ? connectorPosition : winchPosition;
    var state =
        carryWinch
            ? SimBlocks.ROPE_WINCH.getDefaultState()
            : SimBlocks.ROPE_CONNECTOR.getDefaultState();
    level.setBlockAndUpdate(winchPosition, SimBlocks.ROPE_WINCH.getDefaultState());
    level.setBlockAndUpdate(connectorPosition, SimBlocks.ROPE_CONNECTOR.getDefaultState());
    level.setBlockAndUpdate(relocated.below(), Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(relocated, Blocks.AIR.defaultBlockState());
    var winch = holder(level.getBlockEntity(winchPosition));
    var connector = holder(level.getBlockEntity(connectorPosition));
    require(winch.createRope(connector, false), "Connect the native winch to its connector");
    var strand = winch.getOwnedStrand();
    require(
        strand != null && connector.getAttachedStrand() == strand,
        "Native rope is attached at both ends");
    var sourceEntity = level.getBlockEntity(source);
    require(sourceEntity != null, "The fixture source block entity exists");
    sourceEntity.getPersistentData().putString(MARKER, "preserved");
    var before = sourceEntity.saveWithId(level.registryAccess());
    var manager = ServerLevelRopeManager.getOrCreate(level);

    player.setPos(Vec3.atBottomCenterOf(source.north()));
    Object data = carryData.invoke(null, player);
    data.getClass().getMethod("setKeyPressed", boolean.class).invoke(data, true);
    player.tickCount++;
    BiFunction<BlockState, BlockPos, Boolean> denied = (block, position) -> false;
    require(
        !(boolean) pickUp.invoke(null, player, source, level, denied)
            && manager.getStrand(strand.getUUID()) == strand
            && holder(sourceEntity).isAttached(),
        "A refused pickup leaves the physical rope attached");
    int drops = couplingCount(level, source);
    require(
        (boolean) pickUp.invoke(null, player, source, level, null),
        "Carry On can pick up " + state);
    require(
        level.getBlockState(source).isAir()
            && !holder(level.getBlockEntity(other)).isAttached()
            && manager.getStrand(strand.getUUID()) == null,
        "Native rope removal detaches the far endpoint and removes the live strand");
    int expectedDrops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS) ? 1 : 0;
    require(
        couplingCount(level, source) == drops + expectedDrops,
        "The rope coupling follows the native block-drop rule exactly once");

    data = carryData.invoke(null, player);
    var savedCarry = (CompoundTag) data.getClass().getMethod("getNbt").invoke(data);
    var carriedTile = savedCarry.getCompound("tile");
    var previouslyCarried = savedCarry.copy();
    previouslyCarried.put("tile", before.copy());
    var legacy = data.getClass().getConstructor(CompoundTag.class).newInstance(previouslyCarried);
    var loaded =
        BlockEntity.class.cast(
            data.getClass()
                .getMethod("getBlockEntity", BlockPos.class, HolderLookup.Provider.class)
                .invoke(legacy, relocated, level.registryAccess()));
    require(
        loaded != null
            && !holder(loaded).isAttached()
            && "preserved".equals(loaded.getPersistentData().getString(MARKER)),
        "A holder already carried by the original build still places detached");
    for (String key : new String[] {"HasRopeAttached", "OwnStrand", "Strand"}) {
      require(!carriedTile.contains(key), "Carry On does not persist " + key);
      before.remove(key);
    }
    require(
        carriedTile.equals(before),
        "The carried tag preserves every unrelated native and NeoForge key");
    player.setPos(Vec3.atBottomCenterOf(relocated.north()));
    player.tickCount++;
    require(
        (boolean) place.invoke(null, player, relocated.below(), Direction.UP, null),
        "Carry On places the detached holder");
    var restored = level.getBlockEntity(relocated);
    require(
        restored != null
            && "preserved".equals(restored.getPersistentData().getString(MARKER))
            && level.getBlockState(relocated).is(state.getBlock())
            && !holder(restored).isAttached(),
        "Placement keeps the holder data without resurrecting a stale rope");
  }

  private static RopeStrandHolderBehavior holder(BlockEntity entity) {
    if (entity instanceof RopeStrandHolderBlockEntity holder) return holder.getBehavior();
    throw new IllegalStateException("Missing the native rope block entity");
  }

  private static int couplingCount(ServerLevel level, BlockPos center) {
    return level
        .getEntitiesOfClass(
            ItemEntity.class,
            new AABB(center).inflate(5),
            entity -> entity.getItem().is(SimItems.ROPE_COUPLING.get()))
        .stream()
        .mapToInt(entity -> entity.getItem().getCount())
        .sum();
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }
}
