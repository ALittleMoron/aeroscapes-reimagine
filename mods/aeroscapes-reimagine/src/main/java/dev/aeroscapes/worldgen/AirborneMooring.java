package dev.aeroscapes.worldgen;

import dev.simulated_team.simulated.content.blocks.rope.rope_connector.RopeConnectorBlock;
import dev.simulated_team.simulated.content.blocks.rope.rope_winch.RopeWinchBlock;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachment;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.RopeAttachmentPoint;
import dev.simulated_team.simulated.content.blocks.rope.strand.server.ServerRopeStrand;
import dev.simulated_team.simulated.index.SimBlocks;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

final class AirborneMooring {
  private AirborneMooring() {}

  static void place(
      WorldGenLevel level,
      BoundingBox chunk,
      BlockPos winch,
      Direction winchFacing,
      BlockPos connector,
      Direction connectorFacing) {
    boolean placeWinch = chunk.isInside(winch);
    boolean placeConnector = chunk.isInside(connector);
    if (!placeWinch && !placeConnector) return;

    // Both chunks must write the same ID without accessing each other's block entities.
    var id =
        UUID.nameUUIDFromBytes(
            ("aeroscapes_reimagine:mooring/" + winch.asLong() + "/" + connector.asLong())
                .getBytes(StandardCharsets.UTF_8));
    if (placeConnector) {
      var state =
          SimBlocks.ROPE_CONNECTOR
              .get()
              .defaultBlockState()
              .setValue(RopeConnectorBlock.FACING, connectorFacing);
      writeEndpoint(level, connector, state, endpointData(id, false));
    }
    if (placeWinch) {
      var state =
          SimBlocks.ROPE_WINCH
              .get()
              .defaultBlockState()
              .setValue(RopeWinchBlock.FACING, winchFacing);
      var data = endpointData(id, true);
      data.put("Strand", encodeStrand(id, winch, connector, connectorFacing));
      writeEndpoint(level, winch, state, data);
    }
  }

  private static CompoundTag endpointData(UUID id, boolean owner) {
    var data = new CompoundTag();
    data.putBoolean("OwnStrand", owner);
    data.putUUID("HasRopeAttached", id);
    return data;
  }

  private static Tag encodeStrand(
      UUID id, BlockPos winch, BlockPos connector, Direction connectorFacing) {
    var start = Vec3.atCenterOf(winch);
    // The native connector's physics point sits three pixels behind its block center.
    var end =
        Vec3.atCenterOf(connector)
            .subtract(Vec3.atLowerCornerOf(connectorFacing.getNormal()).scale(3.0 / 16.0));
    double distance = start.distanceTo(end);
    int fullSegments = Mth.ceil(distance / ServerRopeStrand.SEGMENT_LENGTH) - 1;
    double firstSegment = distance - fullSegments * ServerRopeStrand.SEGMENT_LENGTH;
    var direction = end.subtract(start).normalize();
    var points = new ArrayList<Vector3d>(fullSegments + 2);
    points.add(new Vector3d(start.x, start.y, start.z));
    for (int index = 0; index <= fullSegments; index++) {
      var point =
          start.add(direction.scale(firstSegment + index * ServerRopeStrand.SEGMENT_LENGTH));
      points.add(new Vector3d(point.x, point.y, point.z));
    }
    var strand = new ServerRopeStrand(id, points);
    strand.updateFirstSegmentExtension(firstSegment);
    // Native attachment recording needs a level only after regular block ticks activate the strand.
    strand.addAttachment(
        null,
        RopeAttachmentPoint.START,
        new RopeAttachment(RopeAttachmentPoint.START, (UUID) null, winch));
    strand.addAttachment(
        null,
        RopeAttachmentPoint.END,
        new RopeAttachment(RopeAttachmentPoint.END, (UUID) null, connector));
    return ServerRopeStrand.CODEC.encodeStart(NbtOps.INSTANCE, strand).getOrThrow();
  }

  private static void writeEndpoint(
      WorldGenLevel level, BlockPos position, BlockState state, CompoundTag data) {
    if (!level.setBlock(position, state, Block.UPDATE_CLIENTS)) return;
    var endpoint = level.getBlockEntity(position);
    if (endpoint != null) {
      endpoint.loadCustomOnly(data, level.registryAccess());
      endpoint.setChanged();
    }
  }
}
