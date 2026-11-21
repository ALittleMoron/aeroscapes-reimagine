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
    modBus.addListener(ChangeNetwork::register);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RaceNetwork::login);
    NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RaceNetwork::respawn);
    NeoForge.EVENT_BUS.addListener(RaceNetwork::dimension);
    NeoForge.EVENT_BUS.addListener(RaceNetwork::startTracking);
  }

  private static void payloads(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("4");
    registrar.playToServer(
        Choose.TYPE,
        Choose.CODEC,
        (msg, ctx) -> {
          if (!(ctx.player() instanceof ServerPlayer player)) return;
          Race race = Race.byId(msg.race());
          if (race != null
              && RaceIdentity.choose(
                  player,
                  race,
                  msg.style(),
                  msg.tint(),
                  CharacterClass.byId(msg.classId()),
                  msg.starter(),
                  Profession.byId(msg.professionId()),
                  msg.professionStarter())) {
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
    registrar.playToServer(
        Recolor.TYPE,
        Recolor.CODEC,
        (msg, ctx) -> {
          if (!(ctx.player() instanceof ServerPlayer player)) return;
          Race race = Race.byId(msg.race());
          if (race != null && RaceIdentity.recolor(player, race, msg.style(), msg.tint()))
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, snapshot(player));
        });
    registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (msg, ctx) -> RaceClient.accept(msg));
  }

  public static void choose(
      Race race,
      int style,
      int tint,
      CharacterClass chosenClass,
      String starter,
      Profession profession,
      String professionStarter) {
    if (race.canTint(style, tint)
        && chosenClass.starters().contains(starter)
        && profession.starters().contains(professionStarter))
      PacketDistributor.sendToServer(
          new Choose(
              race.id(),
              style,
              tint,
              chosenClass.id(),
              starter,
              profession.id(),
              professionStarter));
  }

  public static void recolor(Race race, int style, int tint) {
    if (race.canTint(style, tint))
      PacketDistributor.sendToServer(new Recolor(race.id(), style, tint));
  }

  static Snapshot snapshot(ServerPlayer player) {
    Race race = RaceIdentity.race(player);
    CharacterClass chosenClass = RaceIdentity.characterClass(player);
    Profession profession = RaceIdentity.profession(player);
    return new Snapshot(
        player.getUUID(),
        race == null ? "" : race.id(),
        RaceIdentity.style(player),
        RaceIdentity.tint(player),
        chosenClass == null ? "" : chosenClass.id(),
        RaceIdentity.starter(player),
        profession == null ? "" : profession.id(),
        RaceIdentity.professionStarter(player),
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

  public record Choose(
      String race,
      int style,
      int tint,
      String classId,
      String starter,
      String professionId,
      String professionStarter)
      implements CustomPacketPayload {
    public static final Type<Choose> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "race_choose"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Choose> CODEC =
        StreamCodec.of(
            (buf, msg) -> {
              buf.writeUtf(msg.race(), 16);
              buf.writeVarInt(msg.style());
              buf.writeInt(msg.tint());
              buf.writeUtf(msg.classId(), 16);
              buf.writeUtf(msg.starter(), 32);
              buf.writeUtf(msg.professionId(), 16);
              buf.writeUtf(msg.professionStarter(), 32);
            },
            buf ->
                new Choose(
                    buf.readUtf(16),
                    buf.readVarInt(),
                    buf.readInt(),
                    buf.readUtf(16),
                    buf.readUtf(32),
                    buf.readUtf(16),
                    buf.readUtf(32)));

    @Override
    public Type<Choose> type() {
      return TYPE;
    }
  }

  private record Recolor(String race, int style, int tint) implements CustomPacketPayload {
    private static final Type<Recolor> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "race_recolor"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Recolor> CODEC =
        StreamCodec.of(
            (buf, msg) -> {
              buf.writeUtf(msg.race(), 16);
              buf.writeVarInt(msg.style());
              buf.writeInt(msg.tint());
            },
            buf -> new Recolor(buf.readUtf(16), buf.readVarInt(), buf.readInt()));

    @Override
    public Type<Recolor> type() {
      return TYPE;
    }
  }

  public record Snapshot(
      UUID player,
      String race,
      int style,
      int tint,
      String classId,
      String starter,
      String professionId,
      String professionStarter,
      boolean grantPending)
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
              buf.writeInt(msg.tint());
              buf.writeUtf(msg.classId(), 16);
              buf.writeUtf(msg.starter(), 32);
              buf.writeUtf(msg.professionId(), 16);
              buf.writeUtf(msg.professionStarter(), 32);
              buf.writeBoolean(msg.grantPending());
            },
            buf ->
                new Snapshot(
                    buf.readUUID(),
                    buf.readUtf(16),
                    buf.readVarInt(),
                    buf.readInt(),
                    buf.readUtf(16),
                    buf.readUtf(32),
                    buf.readUtf(16),
                    buf.readUtf(32),
                    buf.readBoolean()));

    @Override
    public Type<Snapshot> type() {
      return TYPE;
    }
  }
}
