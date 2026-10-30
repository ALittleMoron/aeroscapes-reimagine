package dev.aeroscapes.testship;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Confirms that Sable has finished creating all ship parts on the actual client. */
public final class TestShipNetwork {
  private TestShipNetwork() {}

  public static void register(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("1");
    registrar.playToClient(
        Check.TYPE,
        Check.CODEC,
        (message, context) -> {
          var container = SubLevelContainer.getContainer(context.player().level());
          if (container != null
              && message.members().stream()
                  .allMatch(
                      id ->
                          container.getSubLevel(id) instanceof ClientSubLevel part
                              && part.isFinalized())) {
            context.reply(new Ready(message.token()));
          }
        });
    registrar.playToServer(
        Ready.TYPE,
        Ready.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player) {
            var container = SubLevelContainer.getContainer(player.serverLevel());
            if (container instanceof TestShipContainer fixture)
              fixture.aeroscapes$testShips().boarding().confirm(player, message.token());
          }
        });
  }

  public record Check(UUID token, List<UUID> members) implements CustomPacketPayload {
    public static final Type<Check> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "test_ship_check"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Check> CODEC =
        StreamCodec.of(
            (buffer, message) -> {
              buffer.writeUUID(message.token());
              buffer.writeCollection(message.members(), (output, id) -> output.writeUUID(id));
            },
            buffer -> new Check(buffer.readUUID(), buffer.readList(input -> input.readUUID())));

    @Override
    public Type<Check> type() {
      return TYPE;
    }
  }

  public record Ready(UUID token) implements CustomPacketPayload {
    public static final Type<Ready> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "test_ship_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Ready> CODEC =
        StreamCodec.of(
            (buffer, message) -> buffer.writeUUID(message.token()),
            buffer -> new Ready(buffer.readUUID()));

    @Override
    public Type<Ready> type() {
      return TYPE;
    }
  }
}
