package dev.aeroscapes.regression;

import com.mojang.logging.LogUtils;
import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerLevelRopeManager;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

final class AirborneMooringRegression {
  private AirborneMooringRegression() {}

  static void verifySaved(ServerLevel level, BlockPos winch, BlockPos connector) {
    if (!(level.getBlockEntity(winch) instanceof RopeWinchBlockEntity owner)
        || !(level.getBlockEntity(connector) instanceof RopeConnectorBlockEntity peer))
      throw new IllegalStateException(
          "Native mooring blocks missing at " + winch + " and " + connector);

    var saved = owner.saveCustomOnly(level.registryAccess());
    var counterpart = peer.saveCustomOnly(level.registryAccess());
    var live = owner.getRopeHolder().getOwnedStrand();
    if (live == null
        || !saved.getBoolean("OwnStrand")
        || counterpart.getBoolean("OwnStrand")
        || counterpart.contains("Strand")
        || !saved.hasUUID("HasRopeAttached")
        || !counterpart.hasUUID("HasRopeAttached")
        || !live.getUUID().equals(saved.getUUID("HasRopeAttached"))
        || !live.getUUID().equals(counterpart.getUUID("HasRopeAttached")))
      throw new IllegalStateException("Mooring owner and peer disagree at " + winch);

    var roundTrip =
        ServerRopeStrand.CODEC.parse(NbtOps.INSTANCE, saved.getCompound("Strand")).getOrThrow();
    var start = roundTrip.getAttachment(RopeAttachmentPoint.START);
    var end = roundTrip.getAttachment(RopeAttachmentPoint.END);
    double distance =
        owner
            .getAttachmentPoint(winch, owner.getBlockState())
            .distanceTo(peer.getAttachmentPoint(connector, peer.getBlockState()));
    if (!live.getUUID().equals(roundTrip.getUUID())
        || start == null
        || end == null
        || start.subLevelID() != null
        || end.subLevelID() != null
        || !start.blockAttachment().equals(winch)
        || !end.blockAttachment().equals(connector)
        || Math.abs(roundTrip.getCurrentExtension() - distance) > 0.1)
      throw new IllegalStateException("Mooring endpoints or length drifted at " + winch);
  }

  static void awaitLive(ServerLevel level, BlockPos winch, BlockPos connector, Runnable completed) {
    var forced = new ArrayList<ChunkPos>();
    var from = new ChunkPos(winch);
    var to = new ChunkPos(connector);
    for (int x = Math.min(from.x, to.x) - 1; x <= Math.max(from.x, to.x) + 1; x++) {
      for (int z = Math.min(from.z, to.z) - 1; z <= Math.max(from.z, to.z) + 1; z++) {
        if (level.setChunkForced(x, z, true)) forced.add(new ChunkPos(x, z));
      }
    }
    NeoForge.EVENT_BUS.addListener(
        new Consumer<ServerTickEvent.Post>() {
          private int ticks;

          @Override
          public void accept(ServerTickEvent.Post event) {
            if (event.getServer() != level.getServer()) return;
            var owner = (RopeWinchBlockEntity) level.getBlockEntity(winch);
            var peer = (RopeConnectorBlockEntity) level.getBlockEntity(connector);
            var strand = owner == null ? null : owner.getRopeHolder().getOwnedStrand();
            boolean active =
                strand != null
                    && strand.isActive()
                    && peer != null
                    && peer.getRopeHolder().getAttachedStrand() == strand
                    && ServerLevelRopeManager.getOrCreate(level).getStrand(strand.getUUID())
                        == strand;
            if (!active && ++ticks < 240) return;
            NeoForge.EVENT_BUS.unregister(this);
            forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            if (!active)
              throw new IllegalStateException("Native mooring did not activate at " + winch);
            LogUtils.getLogger()
                .info(
                    "Native mooring active seed={} winch={} connector={}",
                    level.getSeed(),
                    winch,
                    connector);
            completed.run();
          }
        });
  }
}
