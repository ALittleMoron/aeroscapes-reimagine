package dev.aeroscapes.races;

import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.progression.TalentRefunds;
import dev.aeroscapes.races.client.ChangeClient;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ChangeNetwork {
  private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
  private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> NODES =
      ByteBufCodecs.stringUtf8(80).apply(ByteBufCodecs.list(256));

  private ChangeNetwork() {}

  private static final class Session {
    final long id;
    final boolean identity;
    final InteractionHand hand;
    final RaceNetwork.Snapshot original;
    long ticket;
    Predicate<ServerPlayer> operation;

    Session(ServerPlayer player, InteractionHand hand, boolean identity) {
      this.id = token(player);
      this.hand = hand;
      this.identity = identity;
      this.original = RaceNetwork.snapshot(player);
    }

    boolean usable(ServerPlayer player) {
      return player.isAlive()
          && !player.isSpectator()
          && !player.isRemoved()
          && player
              .getItemInHand(hand)
              .is(identity ? ChangeItems.IDENTITY_SEAL.get() : ChangeItems.KNOTTED_THREAD.get())
          && original.equals(RaceNetwork.snapshot(player));
    }
  }

  private static long token(ServerPlayer player) {
    return player.getRandom().nextLong() | 1L;
  }

  public static void open(ServerPlayer player, InteractionHand hand) {
    if (!CharacterClass.creationAvailable() || RaceIdentity.awaitingChoice(player)) return;
    boolean identity = player.getItemInHand(hand).is(ChangeItems.IDENTITY_SEAL.get());
    var session = new Session(player, hand, identity);
    if (!session.usable(player)) return;
    SESSIONS.put(player, session);
    PacketDistributor.sendToPlayer(player, new Open(session.id, identity));
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("1");
    registrar.playToClient(Open.TYPE, Open.CODEC, (msg, ctx) -> ChangeClient.open(msg));
    registrar.playToClient(Reply.TYPE, Reply.CODEC, (msg, ctx) -> ChangeClient.reply(msg));
    registrar.playToServer(
        Request.TYPE,
        Request.CODEC,
        (msg, ctx) -> {
          if (ctx.player() instanceof ServerPlayer player) preview(player, msg);
        });
    registrar.playToServer(
        Confirm.TYPE,
        Confirm.CODEC,
        (msg, ctx) -> {
          if (ctx.player() instanceof ServerPlayer player) confirm(player, msg);
        });
  }

  public static void preview(ServerPlayer player, Request request) {
    var session = SESSIONS.get(player);
    if (session == null || session.id != request.id() || !session.usable(player)) {
      respond(player, request.id(), "expired");
      return;
    }
    session.operation = null;
    session.ticket = token(player);
    if (session.identity) {
      var choice =
          new CharacterChanges.Selection(
              request.kind(),
              request.target(),
              request.style(),
              request.starter(),
              request.branch());
      var quote = CharacterChanges.preview(player, choice);
      if (quote == null) {
        respond(player, session.id, CharacterChanges.problem(player, choice));
        return;
      }
      session.operation = subject -> CharacterChanges.change(subject, choice, quote);
      send(
          player,
          new Reply(
              session.id,
              session.ticket,
              "identity",
              switch (request.kind()) {
                case "profession" -> RaceIdentity.profession(player).practiceSkill();
                case "class" ->
                    ClassProgression.category(RaceIdentity.characterClass(player)).getPath();
                default -> "";
              },
              quote.removed(),
              quote.returned(),
              quote.spent(),
              quote.advance() ? 1 : 0));
    } else {
      var quote = TalentRefunds.preview(player, request.kind(), request.nodes()).orElse(null);
      if (quote == null) {
        respond(player, session.id, "talents");
        return;
      }
      session.operation = subject -> TalentRefunds.apply(subject, request.nodes(), quote);
      send(
          player,
          new Reply(
              session.id,
              session.ticket,
              "refund",
              quote.pool(),
              quote.nodes(),
              quote.spent(),
              quote.loss(),
              quote.remainder()));
    }
  }

  public static void confirm(ServerPlayer player, Confirm message) {
    var session = SESSIONS.get(player);
    if (session == null || session.id != message.id()) {
      respond(player, message.id(), "expired");
      return;
    }
    if (message.ticket() == 0) {
      SESSIONS.remove(player);
      return;
    }
    if (session.ticket != message.ticket()
        || session.operation == null
        || !session.usable(player)) {
      respond(player, session.id, "expired");
      return;
    }
    SESSIONS.remove(player);
    if (!session.operation.test(player)) {
      respond(player, session.id, "expired");
      return;
    }
    player.getItemInHand(session.hand).shrink(1);
    PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, RaceNetwork.snapshot(player));
    PreparedAbilities.sync(player, true);
    respond(player, session.id, "done");
  }

  private static void respond(ServerPlayer player, long id, String status) {
    send(player, new Reply(id, 0, status, "", List.of(), 0, 0, 0));
  }

  private static void send(ServerPlayer player, Reply reply) {
    PacketDistributor.sendToPlayer(player, reply);
  }

  private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String name) {
    return new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", name));
  }

  public record Open(long id, boolean identity) implements CustomPacketPayload {
    static final Type<Open> TYPE = ChangeNetwork.type("change_open");
    static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC =
        StreamCodec.of(
            (buf, msg) -> {
              buf.writeLong(msg.id());
              buf.writeBoolean(msg.identity());
            },
            buf -> new Open(buf.readLong(), buf.readBoolean()));

    @Override
    public Type<Open> type() {
      return TYPE;
    }
  }

  public record Request(
      long id,
      String kind,
      String target,
      int style,
      String starter,
      boolean branch,
      List<String> nodes)
      implements CustomPacketPayload {
    static final Type<Request> TYPE = ChangeNetwork.type("change_preview");
    static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        StreamCodec.of(
            (buf, msg) -> {
              buf.writeLong(msg.id());
              buf.writeUtf(msg.kind(), 16);
              buf.writeUtf(msg.target(), 32);
              buf.writeVarInt(msg.style());
              buf.writeUtf(msg.starter(), 64);
              buf.writeBoolean(msg.branch());
              NODES.encode(buf, msg.nodes());
            },
            buf ->
                new Request(
                    buf.readLong(),
                    buf.readUtf(16),
                    buf.readUtf(32),
                    buf.readVarInt(),
                    buf.readUtf(64),
                    buf.readBoolean(),
                    NODES.decode(buf)));

    @Override
    public Type<Request> type() {
      return TYPE;
    }
  }

  public record Confirm(long id, long ticket) implements CustomPacketPayload {
    static final Type<Confirm> TYPE = ChangeNetwork.type("change_confirm");
    static final StreamCodec<RegistryFriendlyByteBuf, Confirm> CODEC =
        StreamCodec.of(
            (buf, msg) -> {
              buf.writeLong(msg.id());
              buf.writeLong(msg.ticket());
            },
            buf -> new Confirm(buf.readLong(), buf.readLong()));

    @Override
    public Type<Confirm> type() {
      return TYPE;
    }
  }

  public record Reply(
      long id,
      long ticket,
      String status,
      String pool,
      List<String> nodes,
      int freed,
      int cost,
      int remainder)
      implements CustomPacketPayload {
    static final Type<Reply> TYPE = ChangeNetwork.type("change_result");
    static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC =
        StreamCodec.of(
            (buf, msg) -> {
              buf.writeLong(msg.id());
              buf.writeLong(msg.ticket());
              buf.writeUtf(msg.status(), 24);
              buf.writeUtf(msg.pool(), 16);
              NODES.encode(buf, msg.nodes());
              buf.writeVarInt(msg.freed());
              buf.writeVarInt(msg.cost());
              buf.writeVarInt(msg.remainder());
            },
            buf ->
                new Reply(
                    buf.readLong(),
                    buf.readLong(),
                    buf.readUtf(24),
                    buf.readUtf(16),
                    NODES.decode(buf),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()));

    @Override
    public Type<Reply> type() {
      return TYPE;
    }
  }
}
