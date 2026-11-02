package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.ryanhcode.sable.physics.chunk.VoxelNeighborhoodState;
import dev.ryanhcode.sable.util.LevelAccelerator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/** Exercise the real offset hooks and Sable shape classifiers without permitting chunk reads. */
public final class SableOffsetRegression {
  private SableOffsetRegression() {}

  public static void verify(ServerLevel level) {
    require(level.getServer().isSameThread(), "Run on the server thread");
    require(level.getServer().isDedicatedServer(), "Disposable dedicated server only");
    require(level.getServer().getPlayerList().getPlayerCount() == 0, "Fixture has real players");
    verifyProviderBoundary(level);
    LevelAccelerator accelerator = new ReadRejectingAccelerator(level);
    int states = 0;
    for (BlockState state : Blocks.POINTED_DRIPSTONE.getStateDefinition().getPossibleStates()) {
      Vec3 expectedOffset = state.getOffset(level, BlockPos.ZERO);
      require(!expectedOffset.equals(Vec3.ZERO), "Dripstone no longer exercises the offset hook");
      require(
          expectedOffset.equals(state.getOffset(accelerator, BlockPos.ZERO)),
          "Accelerator changed the server offset for " + state);
      require(
          VoxelNeighborhoodState.isSolid(accelerator, BlockPos.ZERO, state)
              == !state.getCollisionShape(level, BlockPos.ZERO).isEmpty(),
          "Sable solid classification changed for " + state);
      require(
          VoxelNeighborhoodState.isFullBlock(accelerator, BlockPos.ZERO, state)
              == state.isCollisionShapeFullBlock(level, BlockPos.ZERO),
          "Sable full-block classification changed for " + state);
      states++;
    }
    verifyPlantOffsets(level, accelerator);
    LogUtils.getLogger()
        .info(
            "Sable offset regression passed: {} dripstone states, no accelerator block reads, native server offsets and planter correction",
            states);
  }

  private static void verifyProviderBoundary(ServerLevel level) {
    var planterId = ResourceLocation.fromNamespaceAndPath("supplementaries", "planter");
    require(BuiltInRegistries.BLOCK.containsKey(planterId), "Pinned planter block is missing");
    BlockState planter = BuiltInRegistries.BLOCK.get(planterId).defaultBlockState();
    var supportTag =
        TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("supplementaries", "prevents_offset_above"));
    require(planter.is(supportTag), "Planter offset tag is missing");
    PlanterGetter getter = new PlanterGetter(level, planter);
    for (BlockState state :
        new BlockState[] {
          Blocks.DANDELION.defaultBlockState(), Blocks.TALL_GRASS.defaultBlockState()
        }) {
      require(
          state.getOffset(getter, BlockPos.ZERO).equals(Vec3.ZERO),
          "Supplementaries' physical-client planter mixin is missing: load that exact provider mixin in the dedicated regression configuration");
    }
    require(getter.reads > 0, "Planter correction did not inspect its support");
  }

  private static void verifyPlantOffsets(ServerLevel level, LevelAccelerator accelerator) {
    for (BlockState state :
        new BlockState[] {
          Blocks.DANDELION.defaultBlockState(), Blocks.TALL_GRASS.defaultBlockState()
        }) {
      Vec3 expectedOffset = state.getOffset(level, BlockPos.ZERO);
      require(!expectedOffset.equals(Vec3.ZERO), "Plant no longer exercises the offset hook");
      require(
          expectedOffset.equals(state.getOffset(accelerator, BlockPos.ZERO)),
          "Accelerator changed a native server plant offset");
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  private static final class ReadRejectingAccelerator extends LevelAccelerator {
    private ReadRejectingAccelerator(ServerLevel level) {
      super(level);
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
      throw new IllegalStateException(
          "Offset classification attempted a chunk read at " + position);
    }
  }

  private static final class PlanterGetter implements BlockGetter {
    private final ServerLevel level;
    private final BlockState planter;
    private int reads;

    private PlanterGetter(ServerLevel level, BlockState planter) {
      this.level = level;
      this.planter = planter;
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(BlockPos position) {
      return null;
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
      reads++;
      return planter;
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
      return planter.getFluidState();
    }

    @Override
    public int getHeight() {
      return level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
      return level.getMinBuildHeight();
    }
  }
}
