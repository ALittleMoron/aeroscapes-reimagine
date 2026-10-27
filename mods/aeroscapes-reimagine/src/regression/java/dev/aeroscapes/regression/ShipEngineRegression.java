package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import java.util.List;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;

/** Runs the loaded engine recipe through real powered saw and press ticks. */
public final class ShipEngineRegression {
  private enum Phase {
    SAW,
    PRESS,
    NEW_SHEET
  }

  private final ServerLevel level;
  private final ChunkPos chunk;
  private final BlockPos saw;
  private final BlockPos press;
  private final boolean addedTicket;
  private final ItemStack firstSheet;
  private int attempts = 1;
  private int cycles;
  private long phaseStart;
  private Phase phase;
  private ItemEntity pressInput;
  private ItemStack beforePress = ItemStack.EMPTY;
  private boolean finished;

  private ShipEngineRegression(ServerLevel level, ItemStack firstSheet) {
    this.level = level;
    this.firstSheet = firstSheet;
    chunk = new ChunkPos(level.getSharedSpawnPos());
    int y = Math.min(level.getSharedSpawnPos().getY() + 20, level.getMaxBuildHeight() - 6);
    saw = new BlockPos(chunk.getMiddleBlockX() - 4, y, chunk.getMiddleBlockZ() - 5);
    press = saw.east(7);
    addedTicket = level.setChunkForced(chunk.x, chunk.z, true);
  }

  public static void start(ServerLevel level, ItemStack firstSheet) {
    ShipEngineRegression fixture = new ShipEngineRegression(level, firstSheet);
    fixture.place();
    NeoForge.EVENT_BUS.addListener(fixture::tick);
    NeoForge.EVENT_BUS.addListener(fixture::stopping);
  }

  private static ItemStack item(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.ITEM.containsKey(key))
      throw new IllegalStateException("Missing item " + id);
    return new ItemStack(BuiltInRegistries.ITEM.get(key));
  }

  private static BlockState block(String id) {
    ResourceLocation key = ResourceLocation.parse(id);
    if (!BuiltInRegistries.BLOCK.containsKey(key))
      throw new IllegalStateException("Missing block " + id);
    return BuiltInRegistries.BLOCK.get(key).defaultBlockState();
  }

  private void place() {
    BlockState sawState =
        block("create:mechanical_saw").setValue(BlockStateProperties.FACING, Direction.UP);
    Property<?> axisProperty =
        sawState.getBlock().getStateDefinition().getProperty("axis_along_first");
    if (!(axisProperty instanceof BooleanProperty axis))
      throw new IllegalStateException("Saw axis property is unavailable");
    sawState = sawState.setValue(axis, false);
    level.setBlockAndUpdate(saw, sawState);
    drive(saw.north(), Direction.SOUTH);
    for (int x = -1; x <= 1; x++)
      for (int z = -1; z <= 1; z++)
        level.setBlockAndUpdate(press.below(2).offset(x, 0, z), Blocks.STONE.defaultBlockState());
    level.setBlockAndUpdate(press.below(), Blocks.AIR.defaultBlockState());
    level.setBlockAndUpdate(
        press,
        block("create:mechanical_press")
            .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
    drive(press.west(), Direction.EAST);
    if (!firstSheet.is(item("create:iron_sheet").getItem()) || firstSheet.getCount() != 1)
      throw new IllegalStateException(
          "Engine input must be the iron sheet produced by the previous press");
    insertSaw(firstSheet);
  }

  private void drive(BlockPos position, Direction facing) {
    level.setBlockAndUpdate(
        position, block("create:creative_motor").setValue(BlockStateProperties.FACING, facing));
    var motor = level.getBlockEntity(position);
    if (motor == null) throw new IllegalStateException("Missing engine fixture motor");
    var data = motor.saveCustomOnly(level.registryAccess());
    data.putInt("ScrollValue", 128);
    motor.loadCustomOnly(data, level.registryAccess());
  }

  private void insertSaw(ItemStack input) {
    IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, saw, Direction.UP);
    if (handler == null || !handler.insertItem(0, input, false).isEmpty())
      throw new IllegalStateException(
          "Could not insert engine input into mechanical saw: " + input);
    phase = Phase.SAW;
    phaseStart = level.getServer().getTickCount();
  }

  private void startPress(ItemStack input, Phase nextPhase) {
    beforePress = input.copy();
    pressInput =
        new ItemEntity(level, press.getX() + 0.5, press.getY() - 0.95, press.getZ() + 0.5, input);
    pressInput.setDeltaMovement(0, 0, 0);
    pressInput.setDefaultPickUpDelay();
    if (!level.addFreshEntity(pressInput))
      throw new IllegalStateException("Could not place engine press input");
    phase = nextPhase;
    phaseStart = level.getServer().getTickCount();
  }

  private void tick(ServerTickEvent.Post event) {
    if (finished || event.getServer() != level.getServer()) return;
    long ticks = event.getServer().getTickCount() - phaseStart;
    if (ticks > 600) {
      release();
      throw new IllegalStateException(
          "Engine fixture timed out in " + phase + ", attempt " + attempts + ", cycle " + cycles);
    }
    if (phase == Phase.SAW) {
      List<ItemEntity> outputs =
          level.getEntitiesOfClass(
              ItemEntity.class,
              new AABB(saw).inflate(3),
              entity ->
                  entity.getItem().is(item("simulated:incomplete_engine_assembly").getItem()));
      if (outputs.isEmpty()) return;
      if (outputs.size() != 1 || outputs.getFirst().getItem().getCount() != 1)
        throw new IllegalStateException("Unexpected saw output: " + outputs);
      ItemEntity output = outputs.getFirst();
      ItemStack unfinished = output.getItem().copy();
      requireStep(unfinished, cycles * 2 + 1);
      output.discard();
      startPress(unfinished, Phase.PRESS);
      return;
    }
    if (pressInput == null || pressInput.isRemoved())
      throw new IllegalStateException("Engine press input disappeared");
    if (ItemStack.matches(beforePress, pressInput.getItem())) return;
    ItemStack result = pressInput.getItem().copy();
    pressInput.discard();
    if (result.getCount() != 1)
      throw new IllegalStateException("Unexpected engine output count: " + result);
    if (phase == Phase.NEW_SHEET) {
      if (!result.is(item("create:iron_sheet").getItem()))
        throw new IllegalStateException("Could not press replacement iron sheet: " + result);
      insertSaw(result);
      return;
    }
    cycles++;
    if (result.is(item("simulated:engine_assembly").getItem())) {
      if (cycles != 8)
        throw new IllegalStateException("Engine finished after unexpected cycle " + cycles);
      release();
      LogUtils.getLogger()
          .info("Ship progression engine assembly passed: attempt={}, cycles={}", attempts, cycles);
      ShipAssemblyRegression.start(level, result);
      return;
    }
    if (result.is(item("simulated:incomplete_engine_assembly").getItem())) {
      if (cycles >= 8)
        throw new IllegalStateException("Engine still incomplete after eight saw/press cycles");
      requireStep(result, cycles * 2);
      insertSaw(result);
      return;
    }
    if (cycles != 8)
      throw new IllegalStateException(
          "Unexpected engine result at cycle " + cycles + ": " + result);
    if (!result.is(item("create:iron_sheet").getItem())
        && !result.is(Items.IRON_NUGGET)
        && !result.is(item("create:industrial_iron_block").getItem())
        && !result.is(Items.IRON_BARS)
        && !result.is(Items.IRON_HELMET))
      throw new IllegalStateException("Unexpected weighted engine result: " + result);
    if (++attempts > 64)
      throw new IllegalStateException("No engine after 64 real attempts; last result " + result);
    LogUtils.getLogger()
        .info(
            "Engine attempt {} yielded {}; pressing a new supplied iron ingot",
            attempts - 1,
            result);
    cycles = 0;
    startPress(new ItemStack(Items.IRON_INGOT), Phase.NEW_SHEET);
  }

  private static void requireStep(ItemStack stack, int expected) {
    var componentType =
        BuiltInRegistries.DATA_COMPONENT_TYPE.get(
            ResourceLocation.parse("create:sequenced_assembly"));
    Object component = stack.get(componentType);
    if (component == null) throw new IllegalStateException("Missing engine sequence component");
    try {
      Object id = component.getClass().getMethod("id").invoke(component);
      Object step = component.getClass().getMethod("step").invoke(component);
      if (!ResourceLocation.parse("simulated:sequenced_assembly/engine_assembly").equals(id)
          || !Integer.valueOf(expected).equals(step))
        throw new IllegalStateException(
            "Unexpected engine sequence at step " + expected + ": " + component);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Could not inspect engine sequence component", exception);
    }
  }

  private void stopping(ServerStoppingEvent event) {
    if (event.getServer() == level.getServer()) release();
  }

  private void release() {
    if (!finished && addedTicket) level.setChunkForced(chunk.x, chunk.z, false);
    finished = true;
  }
}
