package dev.aeroscapes.races;

import dev.aeroscapes.magic.PreparedAbilities;
import dev.aeroscapes.races.client.ChangeClient;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

public final class ChangeNetwork {
  private static final Map<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();

  private ChangeNetwork() {}

  private record Pending(
      long ticket, @Nullable CharacterChanges.Selection selection, CharacterChanges.Preview quote) {
    boolean apply(ServerPlayer player) {
      return selection == null
          ? CharacterChanges.reset(player, quote)
          : CharacterChanges.change(player, selection, quote);
    }
  }

  private static final class Session {
    final long id;
    final boolean identity;
    final InteractionHand hand;
    final ItemStack stack;
    final ItemStack originalStack;
    final RaceNetwork.Snapshot originalIdentity;
    @Nullable Pending pending;
    long lastTicket;

    Session(ServerPlayer player, InteractionHand hand, boolean identity, ItemStack stack) {
      this.id = token(player);
      this.hand = hand;
      this.identity = identity;
      this.stack = stack;
      this.originalStack = stack.copy();
      this.originalIdentity = RaceNetwork.snapshot(player);
    }

    boolean usable(ServerPlayer player) {
      return player.isAlive()
          && !player.isSpectator()
          && !player.isRemoved()
          && !player.hasDisconnected()
          && stack == player.getItemInHand(hand)
          && ItemStack.matches(stack, originalStack)
          && originalIdentity.equals(RaceNetwork.snapshot(player));
    }

    long nextTicket(ServerPlayer player) {
      long next;
      do next = token(player);
      while (next == lastTicket);
      lastTicket = next;
      return next;
    }
  }

  private static long token(ServerPlayer player) {
    return player.getRandom().nextLong() | 1L;
  }

  public static void open(ServerPlayer player, InteractionHand hand) {
    if (!CharacterClass.creationAvailable() || RaceIdentity.awaitingChoice(player)) return;
    ItemStack stack = player.getItemInHand(hand);
    boolean identity = stack.is(ChangeItems.IDENTITY_SEAL.get());
    if (!identity && !stack.is(ChangeItems.KNOTTED_THREAD.get())) return;
    var session = new Session(player, hand, identity, stack);
    if (!session.usable(player)) return;
    SESSIONS.put(player, session);
    send(player, new Open(session.id, identity));
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("3");
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
      respond(player, request.id(), request.sequence(), 0, "expired");
      return;
    }
    session.pending = null;
    CharacterChanges.Selection selection = null;
    CharacterChanges.Preview quote;
    if (session.identity) {
      selection =
          new CharacterChanges.Selection(
              request.kind(),
              request.target(),
              request.style(),
              request.starter(),
              request.subclassId());
      quote = CharacterChanges.preview(player, selection);
    } else if (request.isReset()) {
      quote = CharacterChanges.previewReset(player);
    } else {
      respond(player, request.id(), request.sequence(), 0, "invalid");
      return;
    }
    if (quote == null) {
      respond(
          player,
          request.id(),
          request.sequence(),
          0,
          selection == null ? "reset_unavailable" : CharacterChanges.problem(player, selection));
      return;
    }
    long ticket = session.nextTicket(player);
    session.pending = new Pending(ticket, selection, quote);
    send(player, Reply.preview(request, ticket, session.identity, quote));
  }

  public static void confirm(ServerPlayer player, Confirm message) {
    var session = SESSIONS.get(player);
    if (message.ticket() == 0) {
      if (session != null && session.id == message.id()) {
        if (message.discardPreview()) session.pending = null;
        else SESSIONS.remove(player);
      }
      return;
    }
    if (session == null
        || session.id != message.id()
        || message.discardPreview()
        || session.pending == null
        || session.pending.ticket() != message.ticket()) {
      respond(player, message.id(), 0, message.ticket(), "expired");
      return;
    }
    var pending = session.pending;
    SESSIONS.remove(player);
    if (!session.usable(player) || !pending.apply(player)) {
      respond(player, message.id(), 0, message.ticket(), "expired");
      return;
    }
    session.stack.shrink(1);
    PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, RaceNetwork.snapshot(player));
    PreparedAbilities.sync(player, true);
    respond(player, message.id(), 0, message.ticket(), "done");
  }

  private static void respond(
      ServerPlayer player, long id, int sequence, long ticket, String status) {
    send(
        player,
        new Reply(
            id, sequence, ticket, status, List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of()));
  }

  private static void send(ServerPlayer player, CustomPacketPayload packet) {
    PacketDistributor.sendToPlayer(player, packet);
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
      int sequence,
      String kind,
      String target,
      int style,
      String starter,
      String subclassId)
      implements CustomPacketPayload {
    public Request(
        long id, String kind, String target, int style, String starter, String subclassId) {
      this(id, 0, kind, target, style, starter, subclassId);
    }

    public static Request reset(long id) {
      return new Request(id, "reset", "", 0, "", "");
    }

    public Request numbered(int number) {
      return new Request(id, number, kind, target, style, starter, subclassId);
    }

    private boolean isReset() {
      return kind.equals("reset")
          && target.isEmpty()
          && style == 0
          && starter.isEmpty()
          && subclassId.isEmpty();
    }

    static final Type<Request> TYPE = ChangeNetwork.type("change_preview");
    static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC =
        StreamCodec.of(
            (buf, msg) -> {
              buf.writeLong(msg.id());
              buf.writeVarInt(msg.sequence());
              buf.writeUtf(msg.kind(), 16);
              buf.writeUtf(msg.target(), 32);
              buf.writeVarInt(msg.style());
              buf.writeUtf(msg.starter(), 64);
              buf.writeUtf(msg.subclassId(), 16);
            },
            buf ->
                new Request(
                    buf.readLong(),
                    buf.readVarInt(),
                    buf.readUtf(16),
                    buf.readUtf(32),
                    buf.readVarInt(),
                    buf.readUtf(64),
                    buf.readUtf(16)));

    @Override
    public Type<Request> type() {
      return TYPE;
    }
  }

  public record Confirm(long id, long ticket, boolean discardPreview)
      implements CustomPacketPayload {
    public Confirm(long id, long ticket) {
      this(id, ticket, false);
    }

    static final Type<Confirm> TYPE = ChangeNetwork.type("change_confirm");
    static final StreamCodec<RegistryFriendlyByteBuf, Confirm> CODEC =
        StreamCodec.of(
            (buf, msg) -> {
              buf.writeLong(msg.id());
              buf.writeLong(msg.ticket());
              buf.writeBoolean(msg.discardPreview());
            },
            buf -> new Confirm(buf.readLong(), buf.readLong(), buf.readBoolean()));

    @Override
    public Type<Confirm> type() {
      return TYPE;
    }
  }

  public record Pool(String pool, int before, int after, int paid, int loss, int remainder) {
    private static final StreamCodec<ByteBuf, Pool> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32),
            Pool::pool,
            ByteBufCodecs.VAR_INT,
            Pool::before,
            ByteBufCodecs.VAR_INT,
            Pool::after,
            ByteBufCodecs.VAR_INT,
            Pool::paid,
            ByteBufCodecs.VAR_INT,
            Pool::loss,
            ByteBufCodecs.VAR_INT,
            Pool::remainder,
            Pool::new);
  }

  public record Grant(String pool, String id) {
    private static final StreamCodec<ByteBuf, Grant> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32),
            Grant::pool,
            ByteBufCodecs.stringUtf8(80),
            Grant::id,
            Grant::new);
  }

  public record Reply(
      long id,
      int sequence,
      long ticket,
      String status,
      List<Pool> pools,
      List<Grant> addedGrants,
      List<Grant> retiredGrants,
      List<String> addedSpells,
      List<String> retiredSpells,
      List<String> disabled,
      List<String> wornGear)
      implements CustomPacketPayload {
    private static final StreamCodec<ByteBuf, List<Pool>> POOLS =
        Pool.CODEC.apply(ByteBufCodecs.list(32));
    private static final StreamCodec<ByteBuf, List<Grant>> GRANTS =
        Grant.CODEC.apply(ByteBufCodecs.list(256));
    private static final StreamCodec<ByteBuf, List<String>> IDS =
        ByteBufCodecs.stringUtf8(128).apply(ByteBufCodecs.list(256));
    static final Type<Reply> TYPE = ChangeNetwork.type("change_result");
    static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC =
        StreamCodec.of(
            (buf, msg) -> {
              buf.writeLong(msg.id());
              buf.writeVarInt(msg.sequence());
              buf.writeLong(msg.ticket());
              buf.writeUtf(msg.status(), 32);
              POOLS.encode(buf, msg.pools());
              GRANTS.encode(buf, msg.addedGrants());
              GRANTS.encode(buf, msg.retiredGrants());
              IDS.encode(buf, msg.addedSpells());
              IDS.encode(buf, msg.retiredSpells());
              IDS.encode(buf, msg.disabled());
              IDS.encode(buf, msg.wornGear());
            },
            buf ->
                new Reply(
                    buf.readLong(),
                    buf.readVarInt(),
                    buf.readLong(),
                    buf.readUtf(32),
                    POOLS.decode(buf),
                    GRANTS.decode(buf),
                    GRANTS.decode(buf),
                    IDS.decode(buf),
                    IDS.decode(buf),
                    IDS.decode(buf),
                    IDS.decode(buf)));

    private static Reply preview(
        Request request, long ticket, boolean identity, CharacterChanges.Preview quote) {
      var talents = quote.talents();
      return new Reply(
          request.id(),
          request.sequence(),
          ticket,
          identity ? "identity" : "refund",
          talents.pools().stream()
              .map(
                  pool ->
                      new Pool(
                          pool.pool(),
                          pool.before(),
                          pool.after(),
                          pool.paid(),
                          pool.loss(),
                          pool.remainder()))
              .toList(),
          talents.addedGrants().stream().map(node -> new Grant(node.pool(), node.id())).toList(),
          talents.retiredGrants().stream().map(node -> new Grant(node.pool(), node.id())).toList(),
          quote.addedSpells(),
          quote.retiredSpells(),
          quote.disabled(),
          quote.wornGear());
    }

    @Override
    public Type<Reply> type() {
      return TYPE;
    }
  }
}
