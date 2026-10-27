package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Exercises a real Create press powered by a water wheel. */
public final class ShipMachineRegression {
  private final ServerLevel level;
  private final ChunkPos chunk;
  private final BlockPos powered;
  private final BlockPos control;
  private final BlockPos wheel;
  private final long startTick;
  private final boolean addedTicket;
  private final ItemEntity poweredInput;
  private final ItemEntity controlInput;
  private boolean finished;

  private ShipMachineRegression(ServerLevel level) {
    this.level = level;
    chunk = new ChunkPos(level.getSharedSpawnPos());
    int y = Math.min(level.getSharedSpawnPos().getY() + 20, level.getMaxBuildHeight() - 5);
    powered = new BlockPos(chunk.getMiddleBlockX() - 2, y, chunk.getMiddleBlockZ());
    control = powered.east(5);
    wheel = powered.west(3);
    poweredInput = inputAt(powered);
    controlInput = inputAt(control);
    startTick = level.getServer().getTickCount();
    addedTicket = level.setChunkForced(chunk.x, chunk.z, true);
  }

  public static void start(ServerLevel level) {
    ShipMachineRegression fixture = new ShipMachineRegression(level);
    fixture.place();
    NeoForge.EVENT_BUS.addListener(fixture::tick);
    NeoForge.EVENT_BUS.addListener(fixture::stopping);
  }

  private static BlockState block(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.BLOCK.containsKey(key))
      throw new IllegalStateException("Missing block " + id);
    return BuiltInRegistries.BLOCK.get(key).defaultBlockState();
  }

  private void place() {
    for (BlockPos press : new BlockPos[] {powered, control}) {
      for (int x = -1; x <= 1; x++)
        for (int z = -1; z <= 1; z++)
          level.setBlockAndUpdate(press.below(2).offset(x, 0, z), Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(press.below(), Blocks.AIR.defaultBlockState());
      level.setBlockAndUpdate(
          press,
          block("create:mechanical_press")
              .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
    }
    if (!level.addFreshEntity(poweredInput) || !level.addFreshEntity(controlInput))
      throw new IllegalStateException("Could not place press input");
    for (int z = -2; z <= 3; z++) {
      level.setBlockAndUpdate(wheel.below(2).offset(0, 0, z), Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(wheel.below().offset(-1, 0, z), Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(wheel.below().offset(1, 0, z), Blocks.STONE.defaultBlockState());
      level.setBlockAndUpdate(wheel.below().offset(0, 0, z), Blocks.AIR.defaultBlockState());
    }
    level.setBlockAndUpdate(wheel.below().north(2), Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(wheel.below().south(3), Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(wheel.below().north(), Blocks.WATER.defaultBlockState());
    level.setBlockAndUpdate(
        wheel, block("create:water_wheel").setValue(BlockStateProperties.FACING, Direction.EAST));
    level.setBlockAndUpdate(
        powered.west(2),
        block("create:shaft").setValue(BlockStateProperties.AXIS, Direction.Axis.X));
    level.setBlockAndUpdate(
        powered.west(),
        block("create:shaft").setValue(BlockStateProperties.AXIS, Direction.Axis.X));
  }

  private ItemEntity inputAt(BlockPos press) {
    ItemEntity input =
        new ItemEntity(
            level,
            press.getX() + 0.5,
            press.getY() - 0.95,
            press.getZ() + 0.5,
            new ItemStack(Items.IRON_INGOT));
    input.setDeltaMovement(0, 0, 0);
    input.setDefaultPickUpDelay();
    return input;
  }

  private void tick(ServerTickEvent.Post event) {
    if (finished || event.getServer() != level.getServer()) return;
    long ticks = event.getServer().getTickCount() - startTick;
    if (ticks % 100 == 0) {
      var pressEntity = level.getBlockEntity(powered);
      var motorEntity = level.getBlockEntity(wheel);
      var entities =
          level.getEntitiesOfClass(
              ItemEntity.class, new AABB(powered.below(2)).inflate(1.0, 2.0, 1.0));
      LogUtils.getLogger()
          .info(
              "Press fixture tick {}: press={}, wheel={}, items={}",
              ticks,
              pressEntity == null
                  ? "missing"
                  : pressEntity.saveWithFullMetadata(level.registryAccess()),
              motorEntity == null
                  ? "missing"
                  : motorEntity.saveWithFullMetadata(level.registryAccess()),
              entities.stream()
                  .map(
                      entity ->
                          entity.getItem()
                              + "@"
                              + entity.position()
                              + " ground="
                              + entity.onGround())
                  .toList());
    }
    if (ticks > 1200) {
      release();
      throw new IllegalStateException(
          "Water-powered Create press did not process iron within 1200 server ticks");
    }
    if (ticks < 100 || !matches(poweredInput, powered, "create:iron_sheet")) return;
    if (!matches(controlInput, control, "minecraft:iron_ingot")) {
      release();
      throw new IllegalStateException("Unpowered press negative control failed");
    }
    ItemStack firstSheet = poweredInput.getItem().copy();
    poweredInput.discard();
    release();
    LogUtils.getLogger().info("Ship progression water-powered press passed with unpowered control");
    ShipHeatRegression.start(level, firstSheet);
  }

  private boolean matches(ItemEntity input, BlockPos press, String id) {
    var key = ResourceLocation.parse(id);
    var item = BuiltInRegistries.ITEM.get(key);
    AABB area = new AABB(press.below(2)).inflate(1.0, 2.0, 1.0);
    return !input.isRemoved()
        && area.contains(input.position())
        && input.getItem().is(item)
        && input.getItem().getCount() == 1;
  }

  private void stopping(ServerStoppingEvent event) {
    if (event.getServer() == level.getServer()) release();
  }

  private void release() {
    if (!finished && addedTicket) level.setChunkForced(chunk.x, chunk.z, false);
    finished = true;
  }
}
