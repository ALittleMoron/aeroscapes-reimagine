package dev.aeroscapes.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicateType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** One bounded preflight before the scoped native 100-block downward bases. */
public final class GroundedColumnPredicate implements BlockPredicate {
  private static final GroundedColumnPredicate INSTANCE = new GroundedColumnPredicate();
  public static final MapCodec<GroundedColumnPredicate> CODEC = MapCodec.unit(INSTANCE);
  private static final DeferredRegister<BlockPredicateType<?>> TYPES =
      DeferredRegister.create(Registries.BLOCK_PREDICATE_TYPE, "aeroscapes_reimagine");
  private static final DeferredHolder<
          BlockPredicateType<?>, BlockPredicateType<GroundedColumnPredicate>>
      TYPE = TYPES.register("grounded_column", () -> () -> CODEC);
  private static final int HEIGHT = 100;
  private static final TagKey<Block> PASSES_THROUGH =
      TagKey.create(
          Registries.BLOCK,
          ResourceLocation.fromNamespaceAndPath("nova_structures", "support_goes_through"));

  private GroundedColumnPredicate() {}

  public static void register(IEventBus modBus) {
    TYPES.register(modBus);
  }

  @Override
  public boolean test(WorldGenLevel level, BlockPos origin) {
    BlockPos.MutableBlockPos cursor = origin.mutable();
    // Native BlockColumnFeature checks origin..origin-99: direction DOWN and
    // allowed-placement offset UP cancel. A full 100-block column may also
    // rest on the block just below its last placed block, at origin-100.
    for (int distance = 0; distance <= HEIGHT; distance++) {
      if (level.isOutsideBuildHeight(cursor)) return false;
      BlockState state = level.getBlockState(cursor);
      if (distance == HEIGHT || !state.is(PASSES_THROUGH)) {
        return state.getFluidState().isEmpty()
            && Block.isFaceFull(state.getCollisionShape(level, cursor), Direction.UP);
      }
      cursor.move(Direction.DOWN);
    }
    return false;
  }

  @Override
  public BlockPredicateType<?> type() {
    return TYPE.get();
  }
}
