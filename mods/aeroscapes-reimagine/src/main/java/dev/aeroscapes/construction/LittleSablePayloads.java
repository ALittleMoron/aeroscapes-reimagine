package dev.aeroscapes.construction;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import team.creative.littletiles.common.block.entity.BETiles;
import team.creative.littletiles.common.block.mc.BlockTile;

public final class LittleSablePayloads {
  private LittleSablePayloads() {}

  public static void changed(BETiles tiles) {
    if (!(tiles.getLevel() instanceof ServerLevel level)) return;
    BlockPos position = tiles.getBlockPos().immutable();
    if (level.getServer().isSameThread()) mark(level, position);
    else level.getServer().execute(() -> mark(level, position));
  }

  private static @Nullable LittleSablePayloadBody mark(ServerLevel level, BlockPos position) {
    if (!(Sable.HELPER.getContaining(level, position) instanceof ServerSubLevel body)
        || body.isRemoved()) return null;
    if (!(body instanceof LittleSablePayloadBody pending))
      throw new IllegalStateException("Missing reviewed LittleTiles payload adapter on Sable body");
    pending.aeroscapes$payloadChanged(position);
    return pending;
  }

  public static void afterMove(
      ServerLevel source, AssemblyTransform transform, Iterable<BlockPos> blocks) {
    Set<LittleSablePayloadBody> pending = new HashSet<>();
    ServerLevel target = transform.getLevel();
    for (BlockPos original : blocks) {
      BlockPos moved = transform.apply(original);
      if (!(target.getBlockState(moved).getBlock() instanceof BlockTile)) continue;
      var destination = mark(target, moved);
      if (destination != null) pending.add(destination);
      var previous = mark(source, original);
      if (previous != null) pending.add(previous);
    }
    pending.forEach(LittleSablePayloadBody::aeroscapes$flushPayloads);
  }
}
