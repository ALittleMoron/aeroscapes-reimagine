package dev.aeroscapes.enchanting;

import java.util.HashMap;
import java.util.Map;
import me.alfie.immersiveenchanting.gui.EnchantingTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class LibraryNetwork {
  private LibraryNetwork() {}

  public static void register(IEventBus modBus) {
    modBus.addListener(LibraryNetwork::payloads);
    NeoForge.EVENT_BUS.addListener(LibraryNetwork::tick);
  }

  private static void tick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || !(player.containerMenu instanceof EnchantingTableMenu menu)
        || player.tickCount % 10 != 0
        || !menu.stillValid(player)) return;
    var state = (LibraryStatus.Menu) menu;
    var update =
        new LibraryStatus(
            menu.containerId,
            EnchantingMastery.rank(player),
            10 - EnchantingMastery.discountedLevels(player, 10),
            LibraryBooks.ceilings(player.level(), menu.getBlockPos()));
    if (update.equals(state.aeroscapes$libraryStatus())) return;
    state.aeroscapes$libraryStatus(update);
    PacketDistributor.sendToPlayer(player, new Sync(update));
  }

  private static void payloads(RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToClient(
            Sync.TYPE,
            Sync.CODEC,
            (message, context) -> {
              if (!(context.player().containerMenu instanceof EnchantingTableMenu menu)
                  || menu.containerId != message.status().menuId()) return;
              ((LibraryStatus.Menu) menu).aeroscapes$libraryStatus(message.status());
              var registry =
                  context.player().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
              menu.setAvailableEnchantments(
                  message.status().ceilings().keySet().stream()
                      .flatMap(
                          id ->
                              registry.get(ResourceKey.create(Registries.ENCHANTMENT, id)).stream())
                      .map(
                          holder ->
                              (net.minecraft.core.Holder<
                                      net.minecraft.world.item.enchantment.Enchantment>)
                                  holder)
                      .toList());
            });
  }

  public record Sync(LibraryStatus status) implements CustomPacketPayload {
    public static final Type<Sync> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "enchanting_library"));
    private static final StreamCodec<FriendlyByteBuf, Map<ResourceLocation, Integer>> CEILINGS =
        ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.VAR_INT, 1024);
    private static final StreamCodec<FriendlyByteBuf, LibraryStatus> STATUS =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            LibraryStatus::menuId,
            ByteBufCodecs.VAR_INT,
            LibraryStatus::mastery,
            ByteBufCodecs.VAR_INT,
            LibraryStatus::savings,
            CEILINGS,
            LibraryStatus::ceilings,
            LibraryStatus::new);
    public static final StreamCodec<FriendlyByteBuf, Sync> CODEC =
        STATUS.map(Sync::new, Sync::status);

    @Override
    public Type<Sync> type() {
      return TYPE;
    }
  }
}
