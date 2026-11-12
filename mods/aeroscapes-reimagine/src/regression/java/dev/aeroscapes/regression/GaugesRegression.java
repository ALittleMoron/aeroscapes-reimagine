package dev.aeroscapes.regression;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import java.util.LinkedHashMap;
import java.util.Map;
import net.liukrast.deployer.lib.registry.DeployerPanelConnections;
import net.liukrast.eg.content.logistics.board.CounterPanelBehaviour;
import net.liukrast.eg.content.logistics.board.comparator.ComparatorPanelBehaviour;
import net.liukrast.eg.content.logistics.board.comparator.ComparingOperator;
import net.liukrast.eg.content.logistics.link.PortBlock;
import net.liukrast.eg.content.logistics.link.RedstonePortBlock;
import net.liukrast.eg.registry.EGBlocks;
import net.liukrast.eg.registry.EGItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Exercises native counter, comparison, physical ports, and saved panel state. */
public final class GaugesRegression {
  private GaugesRegression() {}

  public static void run(ServerLevel level, BlockPos origin) {
    BlockPos counterPos = origin;
    BlockPos comparatorPos = origin.offset(2, 0, 0);
    BlockPos inputPos = origin.offset(0, 0, 2);
    BlockPos outputPos = origin.offset(2, 0, 2);
    BlockPos powerPos = inputPos.south();
    Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
    for (BlockPos location :
        new BlockPos[] {counterPos, comparatorPos, inputPos, outputPos, powerPos})
      previous.put(location, level.getBlockState(location));
    try {
      for (BlockPos location : previous.keySet())
        level.setBlockAndUpdate(location, Blocks.AIR.defaultBlockState());
      level.setBlock(counterPos, AllBlocks.FACTORY_GAUGE.getDefaultState(), 3);
      level.setBlock(comparatorPos, AllBlocks.FACTORY_GAUGE.getDefaultState(), 3);
      level.setBlock(
          inputPos,
          EGBlocks.REDSTONE_PORT.get().defaultBlockState().setValue(PortBlock.OUTPUT, true),
          3);
      level.setBlock(
          outputPos,
          EGBlocks.REDSTONE_PORT.get().defaultBlockState().setValue(PortBlock.OUTPUT, false),
          3);
      FactoryPanelBlockEntity counterEntity =
          (FactoryPanelBlockEntity) level.getBlockEntity(counterPos);
      FactoryPanelBlockEntity comparatorEntity =
          (FactoryPanelBlockEntity) level.getBlockEntity(comparatorPos);
      check(counterEntity != null && comparatorEntity != null, "Native factory board placement");
      FactoryPanelBlock.PanelSlot slot = FactoryPanelBlock.PanelSlot.BOTTOM_LEFT;
      check(
          EGItems.COUNTER_GAUGE.get().applyToSlot(counterEntity, slot, null),
          "Counter native item placement");
      check(
          EGItems.COMPARATOR_GAUGE.get().applyToSlot(comparatorEntity, slot, null),
          "Comparator native item placement");
      CounterPanelBehaviour counter = (CounterPanelBehaviour) counterEntity.panels.get(slot);
      ComparatorPanelBehaviour comparator =
          (ComparatorPanelBehaviour) comparatorEntity.panels.get(slot);
      counter.setValue(3);
      comparator.value = 2;
      comparator.comparatorMode = ComparingOperator.GREATER_EQUALS;
      counter.addConnection(new FactoryPanelPosition(inputPos, slot));
      comparator.addConnection(new FactoryPanelPosition(counterPos, slot));
      comparator.addConnection(new FactoryPanelPosition(outputPos, slot));
      // The ordinary native lazy tick establishes the unpowered input state.
      counter.lazyTick();
      comparator.lazyTick();
      check(count(counter) == 0 && !powered(level, outputPos), "Unpowered initial native graph");
      for (int pulse = 1; pulse <= 4; pulse++) {
        // Each iteration represents a fresh native behavior tick, resetting the loop guard.
        counter.tick();
        comparator.tick();
        level.setBlock(powerPos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        EGBlocks.REDSTONE_PORT.get().updateSignalIn(level.getBlockState(inputPos), level, inputPos);
        int expected = pulse == 4 ? 0 : pulse;
        check(count(counter) == expected, "Counter rising edge " + pulse);
        check(powered(level, outputPos) == (expected >= 2), "Numeric comparison output " + pulse);
        check(
            counter.getConnectionValue(DeployerPanelConnections.REDSTONE).orElseThrow()
                == (expected >= 3),
            "Counter threshold output " + pulse);
        counter.checkForRedstoneInput();
        check(count(counter) == expected, "Held input is not a second pulse " + pulse);
        level.setBlock(powerPos, Blocks.AIR.defaultBlockState(), 3);
        EGBlocks.REDSTONE_PORT.get().updateSignalIn(level.getBlockState(inputPos), level, inputPos);
        check(count(counter) == expected, "Falling edge preserves count " + pulse);
      }
      counter.tick();
      comparator.tick();
      level.setBlock(powerPos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
      EGBlocks.REDSTONE_PORT.get().updateSignalIn(level.getBlockState(inputPos), level, inputPos);
      CompoundTag saved = counterEntity.saveWithFullMetadata(level.registryAccess());
      level.setBlock(counterPos, Blocks.AIR.defaultBlockState(), 3);
      level.setBlock(counterPos, AllBlocks.FACTORY_GAUGE.getDefaultState(), 3);
      FactoryPanelBlockEntity restored = (FactoryPanelBlockEntity) level.getBlockEntity(counterPos);
      check(restored != null, "Board recreated for NBT reload");
      restored.loadWithComponents(saved, level.registryAccess());
      check(
          restored.panels.get(slot) instanceof CounterPanelBehaviour, "Native panel type restored");
      CounterPanelBehaviour restoredCounter = (CounterPanelBehaviour) restored.panels.get(slot);
      check(
          count(restoredCounter) == 1 && restoredCounter.getValue() == 3,
          "Saved count and threshold restored");
      check(
          restoredCounter.targetedByLinks.containsKey(inputPos),
          "Saved native input connection restored");
    } finally {
      previous.forEach(level::setBlockAndUpdate);
    }
  }

  private static int count(CounterPanelBehaviour counter) {
    return counter.getConnectionValue(DeployerPanelConnections.NUMBERS).orElseThrow().intValue();
  }

  private static boolean powered(ServerLevel level, BlockPos pos) {
    return level.getBlockState(pos).getValue(RedstonePortBlock.POWERED);
  }

  private static void check(boolean condition, String context) {
    if (!condition) throw new IllegalStateException(context);
  }
}
