package dev.aeroscapes.preflight;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class PreflightNetwork {
  static final int MAX_FUELS = 16;

  private PreflightNetwork() {}

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent event) {
    if (!ModList.get().isLoaded("aeronautics") || !ModList.get().isLoaded("sable")) return;
    var registrar = event.registrar("1");
    registrar.playToServer(
        Request.TYPE,
        Request.CODEC,
        (request, context) ->
            context.enqueueWork(
                () -> {
                  if (context.player() instanceof ServerPlayer player)
                    PreflightSurvey.request(player, request.sequence());
                }));
    registrar.playToClient(
        Snapshot.TYPE,
        Snapshot.CODEC,
        (snapshot, context) -> context.enqueueWork(() -> PreflightClient.receive(snapshot)));
  }

  public record Request(int sequence) implements CustomPacketPayload {
    public static final Type<Request> TYPE = PreflightNetwork.type("preflight_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        StreamCodec.of(
            (buffer, request) -> buffer.writeInt(request.sequence()),
            buffer -> new Request(buffer.readInt()));

    @Override
    public Type<Request> type() {
      return TYPE;
    }
  }

  public record Fuel(ResourceLocation item, int count, int burningTicks, boolean infinite) {}

  public record Snapshot(
      int sequence,
      ResourceLocation dimension,
      UUID body,
      String name,
      List<UUID> members,
      int balloons,
      double mass,
      double support,
      double filled,
      double target,
      double capacity,
      double change,
      boolean leaking,
      boolean partial,
      boolean consistentGravity,
      List<Fuel> fuels)
      implements CustomPacketPayload {
    public static final Type<Snapshot> TYPE = PreflightNetwork.type("preflight_snapshot");
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
        StreamCodec.of(Snapshot::write, Snapshot::read);

    public int bodies() {
      return members.size();
    }

    private static void write(RegistryFriendlyByteBuf buffer, Snapshot snapshot) {
      buffer.writeInt(snapshot.sequence());
      buffer.writeUtf(snapshot.dimension().toString(), 256);
      buffer.writeUUID(snapshot.body());
      buffer.writeUtf(snapshot.name(), 96);
      buffer.writeByte(snapshot.members().size());
      for (UUID member : snapshot.members()) buffer.writeUUID(member);
      buffer.writeVarInt(snapshot.balloons());
      buffer.writeDouble(snapshot.mass());
      buffer.writeDouble(snapshot.support());
      buffer.writeDouble(snapshot.filled());
      buffer.writeDouble(snapshot.target());
      buffer.writeDouble(snapshot.capacity());
      buffer.writeDouble(snapshot.change());
      buffer.writeByte(
          (snapshot.leaking() ? 1 : 0)
              | (snapshot.partial() ? 2 : 0)
              | (snapshot.consistentGravity() ? 4 : 0));
      buffer.writeByte(snapshot.fuels().size());
      for (Fuel fuel : snapshot.fuels()) {
        buffer.writeUtf(fuel.item().toString(), 256);
        buffer.writeVarInt(fuel.count());
        buffer.writeVarInt(fuel.burningTicks());
        buffer.writeBoolean(fuel.infinite());
      }
    }

    private static Snapshot read(RegistryFriendlyByteBuf buffer) {
      int sequence = buffer.readInt();
      ResourceLocation dimension = location(buffer);
      UUID body = buffer.readUUID();
      String name = buffer.readUtf(96);
      int bodies = buffer.readUnsignedByte();
      if (bodies > 64) throw new DecoderException("Invalid preflight bounds");
      List<UUID> members = new ArrayList<>();
      for (int index = 0; index < bodies; index++) members.add(buffer.readUUID());
      if (new HashSet<>(members).size() != members.size()
          || (!members.isEmpty() && !members.contains(body)))
        throw new DecoderException("Invalid preflight body members");
      int balloons = buffer.readVarInt();
      double mass = buffer.readDouble(), support = buffer.readDouble();
      double filled = buffer.readDouble(), target = buffer.readDouble();
      double capacity = buffer.readDouble(), change = buffer.readDouble();
      int flags = buffer.readUnsignedByte(), count = buffer.readUnsignedByte();
      if (balloons < 0 || balloons > 4096 || flags > 7 || count > MAX_FUELS)
        throw new DecoderException("Invalid preflight bounds");
      List<Fuel> fuels = new ArrayList<>();
      for (int index = 0; index < count; index++) {
        ResourceLocation item = location(buffer);
        int amount = buffer.readVarInt(), burningTicks = buffer.readVarInt();
        boolean infinite = buffer.readBoolean();
        if (amount < 0 || burningTicks < 0) throw new DecoderException("Invalid preflight fuel");
        fuels.add(new Fuel(item, amount, burningTicks, infinite));
      }
      return new Snapshot(
          sequence,
          dimension,
          body,
          name,
          List.copyOf(members),
          balloons,
          mass,
          support,
          filled,
          target,
          capacity,
          change,
          (flags & 1) != 0,
          (flags & 2) != 0,
          (flags & 4) != 0,
          List.copyOf(fuels));
    }

    @Override
    public Type<Snapshot> type() {
      return TYPE;
    }
  }

  private static ResourceLocation location(RegistryFriendlyByteBuf buffer) {
    ResourceLocation value = ResourceLocation.tryParse(buffer.readUtf(256));
    if (value == null) throw new DecoderException("Invalid preflight identifier");
    return value;
  }

  private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
    return new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", path));
  }
}
