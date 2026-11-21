package dev.aeroscapes.atmosphere;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class ShortBladeAttack {
  public static final TagKey<Item> BLADES =
      TagKey.create(
          Registries.ITEM, ResourceLocation.parse("aeroscapes_reimagine:thrusting_blades"));
  static final int COMBO_RESET_TICKS = 30;
  private static final int CUE_COST = 7;
  private static final int MAX_CUE_BURST = 3;
  private static final Map<ServerPlayer, Long> CUE_BUDGET = new WeakHashMap<>();

  private ShortBladeAttack() {}

  static boolean available() {
    return ModList.get().isLoaded("playeranimator");
  }

  static boolean canAnimate(Player player) {
    return player.getMainHandItem().is(BLADES)
        && player.isAlive()
        && !player.isSpectator()
        && !player.isUsingItem()
        && !player.isSleeping();
  }

  @SubscribeEvent
  public static void register(RegisterPayloadHandlersEvent event) {
    if (!available()) return;
    var registrar = event.registrar("2").optional();
    registrar.playToServer(
        SwingRequest.TYPE,
        SwingRequest.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player) broadcast(player, message.moveId());
        });
    registrar.playToClient(
        Swing.TYPE,
        Swing.CODEC,
        (message, context) ->
            ShortBladeAttackPresentation.remoteSwing(message.entityId(), message.moveId()));
  }

  private static void broadcast(ServerPlayer attacker, int moveId) {
    if (Move.fromId(moveId) == null || !canAnimate(attacker)) return;
    long now = attacker.serverLevel().getGameTime();
    // Allow consecutive valid cues to arrive together after a network stall.
    long next = Math.max(now, CUE_BUDGET.getOrDefault(attacker, now)) + CUE_COST;
    if (next - now > MAX_CUE_BURST * CUE_COST) return;
    CUE_BUDGET.put(attacker, next);
    var cue = new Swing(attacker.getId(), moveId);
    for (ServerPlayer viewer :
        attacker.serverLevel().getChunkSource().chunkMap.getPlayersWatching(attacker)) {
      if (viewer != attacker && viewer.connection.hasChannel(Swing.TYPE))
        PacketDistributor.sendToPlayer(viewer, cue);
    }
  }

  enum Move {
    JAB("short_blade_jab", 7),
    SLASH("short_blade_slash", 7),
    OVERHEAD("short_blade_overhead", 8);

    private static final Move[] MOVES = values();
    final ResourceLocation clip;
    final int followThroughTick;

    Move(String clip, int followThroughTick) {
      this.clip = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", clip);
      this.followThroughTick = followThroughTick;
    }

    Move next() {
      return MOVES[(ordinal() + 1) % MOVES.length];
    }

    static @Nullable Move fromId(int id) {
      return id >= 0 && id < MOVES.length ? MOVES[id] : null;
    }
  }

  record SwingRequest(int moveId) implements CustomPacketPayload {
    static final Type<SwingRequest> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                "aeroscapes_reimagine", "short_blade_combo_request"));
    static final StreamCodec<RegistryFriendlyByteBuf, SwingRequest> CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, SwingRequest::moveId, SwingRequest::new);

    @Override
    public Type<SwingRequest> type() {
      return TYPE;
    }
  }

  private record Swing(int entityId, int moveId) implements CustomPacketPayload {
    private static final Type<Swing> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "short_blade_combo"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Swing> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            Swing::entityId,
            ByteBufCodecs.VAR_INT,
            Swing::moveId,
            Swing::new);

    @Override
    public Type<Swing> type() {
      return TYPE;
    }
  }
}
