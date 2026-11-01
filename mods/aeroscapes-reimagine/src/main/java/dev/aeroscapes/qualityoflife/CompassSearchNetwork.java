package dev.aeroscapes.qualityoflife;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class CompassSearchNetwork {
  private CompassSearchNetwork() {}

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToServer(
            Request.TYPE,
            Request.CODEC,
            (request, context) ->
                context.enqueueWork(
                    () -> {
                      if (context.player() instanceof ServerPlayer player)
                        CompassSearch.request(player, request);
                    }));
  }

  public record Request(
      boolean biome, ResourceLocation target, boolean group, boolean next, boolean extended)
      implements CustomPacketPayload {
    public static final Type<Request> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "compass_search"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        StreamCodec.of(
            (buffer, message) -> {
              int flags =
                  (message.biome ? 1 : 0)
                      | (message.group ? 2 : 0)
                      | (message.next ? 4 : 0)
                      | (message.extended ? 8 : 0);
              buffer.writeByte(flags);
              buffer.writeUtf(message.target.toString(), 256);
            },
            buffer -> {
              int flags = buffer.readUnsignedByte();
              if ((flags & ~15) != 0) throw new DecoderException("Invalid compass request flags");
              ResourceLocation target = ResourceLocation.tryParse(buffer.readUtf(256));
              if (target == null) throw new DecoderException("Invalid compass target identifier");
              return new Request(
                  (flags & 1) != 0, target, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0);
            });

    @Override
    public Type<Request> type() {
      return TYPE;
    }
  }
}
