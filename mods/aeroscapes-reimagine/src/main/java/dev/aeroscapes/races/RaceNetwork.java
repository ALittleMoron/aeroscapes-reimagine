package dev.aeroscapes.races;

import dev.aeroscapes.races.client.RaceClient;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class RaceNetwork {
  private RaceNetwork() {}

  public static void register(IEventBus modBus) {
    modBus.addListener(RaceNetwork::payloads);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RaceNetwork::login);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RaceNetwork::respawn);
    NeoForge.EVENT_BUS.addListener(RaceNetwork::dimension);
    NeoForge.EVENT_BUS.addListener(RaceNetwork::startTracking);
  }

  private static void payloads(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("2");
    registrar.playToServer(
        Choose.TYPE,
        Choose.CODEC,
        (msg, ctx) -> {
          if (!(ctx.player() instanceof ServerPlayer player)) return;
          Race race = Race.byId(msg.race());
          if (race != null
              && RaceIdentity.choose(
                  player, race, msg.style(), CharacterClass.byId(msg.classId()), msg.starter())) {
            // A client in a live world can predict movement while the server holds it in place.
            player.connection.teleport(
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, snapshot(player));
            if (ModList.get().isLoaded("irons_spellbooks")
                && ModList.get().isLoaded("puffish_skills"))
              dev.aeroscapes.magic.PreparedAbilities.sync(player, true);
          } else {
            PacketDistributor.sendToPlayer(player, snapshot(player));
          }
        });
    registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (msg, ctx) -> RaceClient.accept(msg));
  }

  public static void choose(Race race, int style, CharacterClass chosenClass, String starter) {
    if (race.validStyle(style) && chosenClass.starters().contains(starter))
      PacketDistributor.sendToServer(new Choose(race.id(), style, chosenClass.id(), starter));
  }

  private static Snapshot snapshot(ServerPlayer player) {
    Race race = RaceIdentity.race(player);
    CharacterClass chosenClass = RaceIdentity.characterClass(player);
    return new Snapshot(
        player.getUUID(),
        race == null ? "" : race.id(),
        RaceIdentity.style(player),
        chosenClass == null ? "" : chosenClass.id(),
        RaceIdentity.starter(player),
        chosenClass != null && RaceIdentity.grantPending(player));
  }

  private static void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player)
      PacketDistributor.sendToPlayer(player, snapshot(player));
  }

  private static void respawn(PlayerEvent.PlayerRespawnEvent event) {
    if (event.getEntity() instanceof ServerPlayer player)
      PacketDistributor.sendToPlayer(player, snapshot(player));
  }

  private static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player)
      PacketDistributor.sendToPlayer(player, snapshot(player));
  }

  private static void startTracking(PlayerEvent.StartTracking event) {
    if (event.getEntity() instanceof ServerPlayer viewer
        && event.getTarget() instanceof ServerPlayer subject)
      PacketDistributor.sendToPlayer(viewer, snapshot(subject));
  }

  public record Choose(String race, int style, String classId, String starter)
      implements CustomPacketPayload {
    public static final Type<Choose> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "race_choose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC =
        StreamCodec.of(
            (buf, msg) -> {
              buf.writeUtf(msg.race(), 16);
              buf.writeVarInt(msg.style());
              buf.writeUtf(msg.classId(), 16);
              buf.writeUtf(msg.starter(), 32);
            },
            buf -> new Choose(buf.readUtf(16), buf.readVarInt(), buf.readUtf(16), buf.readUtf(32)));

    @Override
    public Type<Choose> type() {
      return TYPE;
    }
  }

  public record Snapshot(
      UUID player, String race, int style, String classId, String starter, boolean grantPending)
      implements CustomPacketPayload {
    public static final Type<Snapshot> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "race_appearance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC =
        StreamCodec.of(
            (buf, msg) -> {
              buf.writeUUID(msg.player());
              buf.writeUtf(msg.race(), 16);
              buf.writeVarInt(msg.style());
              buf.writeUtf(msg.classId(), 16);
              buf.writeUtf(msg.starter(), 32);
              buf.writeBoolean(msg.grantPending());
            },
            buf ->
                new Snapshot(
                    buf.readUUID(),
                    buf.readUtf(16),
                    buf.readVarInt(),
                    buf.readUtf(16),
                    buf.readUtf(32),
                    buf.readBoolean()));

    @Override
    public Type<Snapshot> type() {
      return TYPE;
    }
  }
}
