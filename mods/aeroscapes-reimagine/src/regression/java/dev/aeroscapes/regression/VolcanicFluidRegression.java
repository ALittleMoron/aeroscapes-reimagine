package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.aeroscapes.worldgen.IslandLayout;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Checks a natural lava liner against a dry cavity, then proves the same probe detects a breach.
 */
public final class VolcanicFluidRegression {
  private final ServerLevel level;
  private final BlockPos source;
  private final int floor;
  private final int window;
  private final ChunkPos chunk;
  private final Consumer<ServerTickEvent.Post> listener = this::tick;
  private int remaining;
  private boolean breached;
  private boolean forced;

  private VolcanicFluidRegression(ServerLevel level, IslandLayout.Basin basin) {
    this.level = level;
    source = new BlockPos(basin.centerX(), basin.waterline(), basin.centerZ());
    floor = (int) Math.floor(basin.floor(source.getX(), source.getZ()));
    window = (source.getY() - floor + 22) * Fluids.LAVA.getTickDelay(level) + 20;
    remaining = window;
    chunk = new ChunkPos(source);
  }

  public static void start(ServerLevel level, IslandLayout.Basin basin) {
    var fixture = new VolcanicFluidRegression(level, basin);
    if (!level.getFluidState(fixture.source).is(Fluids.LAVA))
      throw new IllegalStateException("Natural lava source missing for fluid control");
    if (fixture.floor - 16 < level.getMinBuildHeight())
      throw new IllegalStateException("Volcanic control cavity would exceed build bounds");
    for (BlockPos position :
        BlockPos.betweenClosed(
            fixture.source.getX() - 2,
            fixture.floor - 17,
            fixture.source.getZ() - 2,
            fixture.source.getX() + 2,
            fixture.floor - 12,
            fixture.source.getZ() + 2)) {
      var state = level.getBlockState(position);
      if (!state.isSolidRender(level, position) || !state.getFluidState().isEmpty())
        throw new IllegalStateException(
            "Natural volcanic control cavity is not enclosed at " + position);
    }
    for (BlockPos position : fixture.cavity())
      level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
    level.scheduleTick(fixture.source, Fluids.LAVA, 1);
    NeoForge.EVENT_BUS.addListener(fixture.listener);
    fixture.forced = level.setChunkForced(fixture.chunk.x, fixture.chunk.z, true);
    LogUtils.getLogger()
        .info(
            "Volcanic fluid control started: source={} floor={} ticksPerPhase={}",
            fixture.source,
            fixture.floor,
            fixture.window);
  }

  private Iterable<BlockPos> cavity() {
    return BlockPos.betweenClosed(
        source.getX() - 1,
        floor - 16,
        source.getZ() - 1,
        source.getX() + 1,
        floor - 13,
        source.getZ() + 1);
  }

  private void tick(ServerTickEvent.Post event) {
    if (--remaining > 0) return;
    boolean continueTest = false;
    try {
      boolean lava = false;
      for (BlockPos position : cavity()) lava |= level.getFluidState(position).is(FluidTags.LAVA);
      if (!breached) {
        if (lava || !level.getFluidState(source).is(Fluids.LAVA))
          throw new IllegalStateException("Natural lava liner leaked into its control cavity");
        for (int y = floor - 12; y < source.getY(); y++)
          level.setBlock(
              new BlockPos(source.getX(), y, source.getZ()), Blocks.AIR.defaultBlockState(), 3);
        level.scheduleTick(source, Fluids.LAVA, 1);
        breached = true;
        remaining = window;
        continueTest = true;
        return;
      }
      if (!lava)
        throw new IllegalStateException(
            "Volcanic probe failed to detect the deliberate liner breach");
      LogUtils.getLogger()
          .info("Volcanic fluid regression passed: natural liner dry, deliberate breach detected");
    } finally {
      if (!continueTest) {
        NeoForge.EVENT_BUS.unregister(listener);
        if (forced) level.setChunkForced(chunk.x, chunk.z, false);
      }
    }
  }
}
