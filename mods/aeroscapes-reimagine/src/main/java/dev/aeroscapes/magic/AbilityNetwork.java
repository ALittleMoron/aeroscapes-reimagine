package dev.aeroscapes.magic;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

/** Client requests contain slot identities; the server supplies all knowledge and casting data. */
public final class AbilityNetwork {
  private AbilityNetwork() {}

  public static void register(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("4");
    registrar.playToServer(
        Request.TYPE,
        Request.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player) PreparedAbilities.sync(player, true);
        });
    registrar.playToServer(
        Edit.TYPE,
        Edit.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player)
            PreparedAbilities.edit(player, message.slot(), message.abilityId());
        });
    registrar.playToServer(
        Select.TYPE,
        Select.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player)
            PreparedAbilities.select(player, message.slot());
        });
    registrar.playToServer(
        Cast.TYPE,
        Cast.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player
              && !dev.aeroscapes.races.RaceIdentity.awaitingChoice(player))
            PreparedAbilities.cast(player);
        });
    registrar.playToClient(
        Snapshot.TYPE,
        Snapshot.CODEC,
        (message, context) -> dev.aeroscapes.magic.client.AbilityClient.accept(message));
  }

  public record Program(int inventorySlot, String customName) {}

  public record Entry(
      String id,
      String school,
      int level,
      int cap,
      int maxLevel,
      boolean usable,
      String reason,
      int cooldownTicks,
      int manaCost,
      @Nullable Program program) {
    public Entry(
        String id,
        String school,
        int level,
        int cap,
        int maxLevel,
        boolean usable,
        String reason,
        int cooldownTicks,
        int manaCost) {
      this(id, school, level, cap, maxLevel, usable, reason, cooldownTicks, manaCost, null);
    }

    public boolean innate() {
      return school.equals(InnateAbility.GROUP);
    }

    public boolean known() {
      return (granted() && !reason.equals("race") && !reason.equals("hex_missing")) || level > 0;
    }

    public boolean granted() {
      return innate()
          || school.equals(dev.aeroscapes.magic.travel.AmuletStep.GROUP)
          || school.equals(PreparedAbilities.DISCOVERY_GROUP)
          || program != null;
    }
  }

  public record Snapshot(
      List<Entry> entries,
      List<String> slots,
      int selected,
      boolean dwarfNightVision,
      List<String> talents,
      int practiceLevel,
      long practiceXp,
      long nextTalentXp)
      implements CustomPacketPayload {
    public static final Type<Snapshot> TYPE = AbilityNetwork.type("abilities");
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
        StreamCodec.of(
            (buffer, message) -> {
              buffer.writeCollection(
                  message.entries(),
                  (output, entry) -> {
                    output.writeUtf(entry.id(), 128);
                    output.writeUtf(entry.school(), 128);
                    output.writeVarInt(entry.level());
                    output.writeVarInt(entry.cap());
                    output.writeVarInt(entry.maxLevel());
                    output.writeBoolean(entry.usable());
                    output.writeUtf(entry.reason(), 128);
                    output.writeVarInt(entry.cooldownTicks());
                    output.writeVarInt(entry.manaCost());
                    output.writeNullable(
                        entry.program(),
                        (buf, program) -> {
                          buf.writeVarInt(program.inventorySlot());
                          buf.writeUtf(program.customName(), 128);
                        });
                  });
              buffer.writeCollection(message.slots(), (output, slot) -> output.writeUtf(slot, 128));
              buffer.writeVarInt(message.selected());
              buffer.writeBoolean(message.dwarfNightVision());
              buffer.writeCollection(
                  message.talents(), (output, talent) -> output.writeUtf(talent, 128));
              buffer.writeVarInt(message.practiceLevel());
              buffer.writeVarLong(message.practiceXp());
              buffer.writeVarLong(message.nextTalentXp());
            },
            buffer ->
                new Snapshot(
                    buffer.readList(
                        input ->
                            new Entry(
                                input.readUtf(128),
                                input.readUtf(128),
                                input.readVarInt(),
                                input.readVarInt(),
                                input.readVarInt(),
                                input.readBoolean(),
                                input.readUtf(128),
                                input.readVarInt(),
                                input.readVarInt(),
                                input.readNullable(
                                    buf -> new Program(buf.readVarInt(), buf.readUtf(128))))),
                    buffer.readList(input -> input.readUtf(128)),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readList(input -> input.readUtf(128)),
                    buffer.readVarInt(),
                    buffer.readVarLong(),
                    buffer.readVarLong()));

    @Override
    public Type<Snapshot> type() {
      return TYPE;
    }
  }

  public record Request() implements CustomPacketPayload {
    public static final Type<Request> TYPE = AbilityNetwork.type("abilities_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        StreamCodec.of((buffer, message) -> {}, buffer -> new Request());

    @Override
    public Type<Request> type() {
      return TYPE;
    }
  }

  public record Edit(int slot, String abilityId) implements CustomPacketPayload {
    public static final Type<Edit> TYPE = AbilityNetwork.type("abilities_edit");
    public static final StreamCodec<RegistryFriendlyByteBuf, Edit> CODEC =
        StreamCodec.of(
            (buffer, message) -> {
              buffer.writeVarInt(message.slot());
              buffer.writeUtf(message.abilityId(), 128);
            },
            buffer -> new Edit(buffer.readVarInt(), buffer.readUtf(128)));

    @Override
    public Type<Edit> type() {
      return TYPE;
    }
  }

  public record Select(int slot) implements CustomPacketPayload {
    public static final Type<Select> TYPE = AbilityNetwork.type("abilities_select");
    public static final StreamCodec<RegistryFriendlyByteBuf, Select> CODEC =
        StreamCodec.of(
            (buffer, message) -> buffer.writeVarInt(message.slot()),
            buffer -> new Select(buffer.readVarInt()));

    @Override
    public Type<Select> type() {
      return TYPE;
    }
  }

  public record Cast() implements CustomPacketPayload {
    public static final Type<Cast> TYPE = AbilityNetwork.type("abilities_cast");
    public static final StreamCodec<RegistryFriendlyByteBuf, Cast> CODEC =
        StreamCodec.of((buffer, message) -> {}, buffer -> new Cast());

    @Override
    public Type<Cast> type() {
      return TYPE;
    }
  }

  private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
    return new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", path));
  }
}
