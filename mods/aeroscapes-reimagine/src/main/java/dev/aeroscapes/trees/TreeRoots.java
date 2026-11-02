package dev.aeroscapes.trees;

import java.util.ArrayDeque;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Soil and roots supported by the selected vanilla and Nature's Spirit trees. */
final class TreeRoots {
  private TreeRoots() {}

  static boolean supported(
      ServerLevel level, BlockPos logPosition, BlockState log, BlockState soil) {
    if (log.is(BlockTags.CRIMSON_STEMS)) return soil.is(Blocks.CRIMSON_NYLIUM);
    if (log.is(BlockTags.WARPED_STEMS)) return soil.is(Blocks.WARPED_NYLIUM);
    if (soil.is(BlockTags.DIRT) || soil.is(Blocks.MOSS_BLOCK)) return true;
    if (log.is(BlockTags.MANGROVE_LOGS)) {
      if (soil.is(Blocks.MANGROVE_ROOTS) || soil.is(Blocks.MUDDY_MANGROVE_ROOTS)) {
        return rootedMangrove(level, logPosition.below());
      }
      return Blocks.MANGROVE_PROPAGULE.defaultBlockState().canSurvive(level, logPosition);
    }
    var name = BuiltInRegistries.BLOCK.getKey(log.getBlock());
    String path = name.getPath();
    if (path.startsWith("stripped_")) path = path.substring("stripped_".length());
    String wood =
        path.endsWith("_log")
            ? path.substring(0, path.length() - "_log".length())
            : path.endsWith("_wood") ? path.substring(0, path.length() - "_wood".length()) : path;
    if (name.getNamespace().equals("natures_spirit") && wood.equals("coconut")) {
      // Its native sprout also survives on buildings; recognize the natural sandy substrate.
      return soil.is(BlockTags.SAND);
    }
    var sapling =
        BuiltInRegistries.BLOCK.getOptional(
            ResourceLocation.fromNamespaceAndPath(name.getNamespace(), wood + "_sapling"));
    return sapling.isPresent()
        && sapling.orElseThrow().defaultBlockState().is(BlockTags.SAPLINGS)
        && sapling.orElseThrow().defaultBlockState().canSurvive(level, logPosition);
  }

  private static boolean rootedMangrove(ServerLevel level, BlockPos start) {
    var pending = new ArrayDeque<BlockPos>();
    var visited = new HashSet<BlockPos>();
    pending.add(start);
    while (!pending.isEmpty()) {
      BlockPos position = pending.removeFirst();
      // Vanilla's selected mangrove root placement bounds each branch to 15 blocks.
      if (start.distManhattan(position) > 15 || !visited.add(position)) continue;
      if (!level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4)
          || TreeTransactions.onShip(level, position)) return false;
      BlockState state = level.getBlockState(position);
      if (state.is(BlockTags.DIRT) || state.is(Blocks.MUD) || state.is(Blocks.CLAY)) return true;
      if (state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS)) {
        for (Direction direction : Direction.values()) pending.add(position.relative(direction));
      }
    }
    return false;
  }
}
