package dev.aeroscapes.ecology;

import dev.aeroscapes.worldgen.IslandLayout;
import dev.aeroscapes.worldgen.IslandTerrain;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class EcologyNetwork {
  private EcologyNetwork() {}

  public static void register(IEventBus bus) {
    bus.addListener(EcologyNetwork::payloads);
    NeoForge.EVENT_BUS.addListener(EcologyNetwork::watch);
  }

  private static void payloads(RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToClient(
            Waterline.TYPE,
            Waterline.CODEC,
            (message, context) ->
                context.enqueueWork(
                    () -> {
                      var level = context.player().level();
                      if (level.dimension().location().equals(message.dimension())
                          && message.basin().waterline() >= level.getMinBuildHeight()
                          && message.basin().waterline() < level.getMaxBuildHeight()
                          && Double.isFinite(message.basin().radiusX())
                          && message.basin().radiusX() > 0
                          && Double.isFinite(message.basin().radiusZ())
                          && message.basin().radiusZ() > 0
                          && level instanceof ClientWaterlineAccess client)
                        client.aeroscapes$waterlines().put(message.basin());
                    }));
  }

  private static void watch(ChunkWatchEvent.Watch event) {
    var level = event.getLevel();
    if (level.dimension() != Level.OVERWORLD) return;
    IslandTerrain terrain = IslandTerrain.from(level.getChunkSource().randomState());
    if (terrain == null) return;
    var position = event.getPos();
    var basin = terrain.basin(position.getMiddleBlockX(), position.getMiddleBlockZ());
    if (basin == null) return;
    // The payload reads no client chunk. Watch sends it before chunk data and mesh/tint work.
    PacketDistributor.sendToPlayer(
        event.getPlayer(), new Waterline(level.dimension().location(), basin));
  }

  public record Waterline(ResourceLocation dimension, IslandLayout.Basin basin)
      implements CustomPacketPayload {
    public static final Type<Waterline> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "basin_waterline"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Waterline> CODEC =
        StreamCodec.of(
            (buffer, value) -> {
              buffer.writeResourceLocation(value.dimension());
              var basin = value.basin();
              buffer.writeInt(basin.centerX());
              buffer.writeInt(basin.centerZ());
              buffer.writeVarInt(basin.waterline());
              buffer.writeDouble(basin.radiusX());
              buffer.writeDouble(basin.radiusZ());
              buffer.writeVarInt(basin.depth());
              buffer.writeLong(basin.key());
            },
            buffer ->
                new Waterline(
                    buffer.readResourceLocation(),
                    new IslandLayout.Basin(
                        buffer.readInt(),
                        buffer.readInt(),
                        buffer.readVarInt(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readVarInt(),
                        buffer.readLong())));

    @Override
    public Type<Waterline> type() {
      return TYPE;
    }
  }
}
