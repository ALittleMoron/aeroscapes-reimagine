package dev.aeroscapes.travel;

import com.mojang.serialization.MapCodec;
import com.sshakusora.waystonessable.compat.SableWaystoneCompat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Stream;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystoneTypes;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.api.event.BuildWaystoneSelectionMenuEvent;
import net.blay09.mods.waystones.api.event.WaystoneTeleportEvent;
import net.blay09.mods.waystones.api.requirement.VariableResolver;
import net.blay09.mods.waystones.api.requirement.WarpRequirement;
import net.blay09.mods.waystones.block.WaystoneBlock;
import net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase;
import net.blay09.mods.waystones.item.ModItems;
import net.blay09.mods.waystones.requirement.ExperiencePointsRequirement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Survival travel policy around the pinned provider's teleport and Sable loading APIs. */
public final class TravelIntegration {
  private static final String NAMESPACE = "aeroscapes_reimagine";
  private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NAMESPACE);
  private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
      DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, NAMESPACE);
  public static final DeferredItem<Item> WAYSTONE_SIGIL =
      ITEMS.register(
          "waystone_sigil", () -> new WaystoneSigilItem(new Item.Properties().rarity(Rarity.RARE)));
  private static final Map<UUID, Payment> PENDING = new HashMap<>();
  private static final long PENDING_NANOS = 30_000_000_000L;

  private TravelIntegration() {}

  public static void register(IEventBus bus) {
    ITEMS.register(bus);
    LOOT_MODIFIERS.register("natural_waystone_drops", () -> NaturalWaystoneDrops.CODEC);
    LOOT_MODIFIERS.register(bus);
    bus.addListener(TravelIntegration::setup);
    bus.addListener(
        net.neoforged.bus.api.EventPriority.LOWEST, TravelIntegration::creativeContents);
    NeoForge.EVENT_BUS.addListener(TravelIntegration::stopped);
    NeoForge.EVENT_BUS.addListener(TravelIntegration::tooltip);
  }

  private static void tooltip(ItemTooltipEvent event) {
    if (disabledItem(event.getItemStack())) {
      event
          .getToolTip()
          .add(
              Component.translatable("gui.aeroscapes_reimagine.travel.disabled_item")
                  .withStyle(ChatFormatting.GRAY));
    }
    if (event.getItemStack().is(ModItems.warpStone)) {
      event
          .getToolTip()
          .add(
              Component.translatable("tooltip.aeroscapes_reimagine.warp_stone_ingredient")
                  .withStyle(ChatFormatting.GRAY));
    }
  }

  private static boolean disabledItem(ItemStack stack) {
    return stack.is(ModItems.epitaph)
        || stack.is(ModItems.dormantShard)
        || stack.is(ModItems.twinboundFeather);
  }

  private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
    if (event.getTab() == ModItems.creativeModeTab.get()) {
      event.accept(WAYSTONE_SIGIL.get(), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
    Stream.concat(event.getParentEntries().stream(), event.getSearchEntries().stream())
        .filter(TravelIntegration::disabledItem)
        .toList()
        .forEach(
            stack -> event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS));
  }

  private static void setup(FMLCommonSetupEvent event) {
    event.enqueueWork(
        () -> {
          WaystonesAPI.registerVariableResolver(new DistanceSteps());
          Balm.getEvents()
              .onEvent(
                  WaystoneTeleportEvent.Pre.class, TravelIntegration::before, EventPriority.Lowest);
          Balm.getEvents()
              .onEvent(
                  WaystoneTeleportEvent.Complete.class,
                  TravelIntegration::complete,
                  EventPriority.Highest);
          Balm.getEvents()
              .onEvent(
                  BuildWaystoneSelectionMenuEvent.class,
                  TravelIntegration::menu,
                  EventPriority.Lowest);
        });
  }

  public static boolean canActivate(Player player, Waystone waystone, boolean consume) {
    if (!WaystoneTypes.WAYSTONE.equals(waystone.getWaystoneType())
        || WaystonesAPI.isWaystoneActivated(player, waystone)) return true;
    if (player.level().isClientSide) {
      return player.getAbilities().instabuild
          || player.getInventory().countItem(WAYSTONE_SIGIL.get()) > 0;
    }
    if (!(player instanceof ServerPlayer serverPlayer) || !atStone(serverPlayer, waystone)) {
      message(player, "physical_only");
      return false;
    }
    if (player.getAbilities().instabuild) return true;
    for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
      var stack = player.getInventory().getItem(slot);
      if (stack.is(WAYSTONE_SIGIL.get())) {
        if (consume) {
          stack.shrink(1);
          player.getInventory().setChanged();
        }
        return true;
      }
    }
    message(player, "sigil_required");
    return false;
  }

  private static void menu(BuildWaystoneSelectionMenuEvent event) {
    var source = event.getSourceWaystone();
    if (source == null
        || !WaystoneTypes.WAYSTONE.equals(source.getWaystoneType())
        || !event.getWarpItem().isEmpty()
        || !event.getFlags().isEmpty()) {
      event.getWaystones().clear();
      return;
    }
    event
        .getWaystones()
        .removeIf(
            target ->
                !WaystoneTypes.WAYSTONE.equals(target.getWaystoneType())
                    || !WaystonesAPI.isWaystoneActivated(event.getPlayer(), target));
  }

  private static void before(WaystoneTeleportEvent.Pre event) {
    var context = event.getContext();
    if (!(context.getEntity() instanceof ServerPlayer player)) {
      event.setCanceled(true);
      return;
    }
    if (event.isCanceled()) return;
    if (!physicalContext(player, context)) {
      message(player, "physical_only");
      event.setCanceled(true);
      return;
    }
    var previous = PENDING.get(player.getUUID());
    if (previous != null && !previous.expired()) {
      message(player, "pending");
      event.setCanceled(true);
      return;
    }
    var payment = new Payment(context, player);
    PENDING.put(player.getUUID(), payment);
    event.setRequirements(payment);
  }

  private static boolean physicalContext(ServerPlayer player, WaystoneTeleportContext context) {
    if (!context.getWarpItem().isEmpty()
        || context.getWarpHand() != null
        || !context.getFlags().isEmpty()) return false;
    var source = context.getFromWaystone().orElse(null);
    var target = context.getTargetWaystone();
    return source != null
        && WaystoneTypes.WAYSTONE.equals(source.getWaystoneType())
        && WaystoneTypes.WAYSTONE.equals(target.getWaystoneType())
        && WaystonesAPI.isWaystoneActivated(player, source)
        && WaystonesAPI.isWaystoneActivated(player, target)
        && atStone(player, source)
        && canonical(player.getServer(), target).isPresent();
  }

  public static boolean atStone(ServerPlayer player, Waystone requested) {
    var stone = canonical(player.getServer(), requested).orElse(null);
    if (stone == null || !player.level().dimension().equals(stone.getDimension())) return false;
    var level = player.serverLevel();
    if (!level.getChunkSource().hasChunk(stone.getPos().getX() >> 4, stone.getPos().getZ() >> 4))
      return false;
    if (!(level.getBlockEntity(stone.getPos()) instanceof WaystoneBlockEntityBase blockEntity)
        || !(level.getBlockState(stone.getPos()).getBlock() instanceof WaystoneBlock)
        || !blockEntity.getWaystone().getWaystoneUid().equals(stone.getWaystoneUid())) return false;
    return player.canInteractWithBlock(stone.getPos(), 0)
        || player.canInteractWithBlock(stone.getPos().above(), 0);
  }

  private static Optional<Waystone> canonical(MinecraftServer server, Waystone requested) {
    return WaystonesAPI.getWaystone(server, requested.getWaystoneUid())
        .filter(stone -> WaystoneTypes.WAYSTONE.equals(stone.getWaystoneType()))
        .filter(stone -> stone.getDimension().equals(requested.getDimension()));
  }

  private static Optional<Vec3> visiblePosition(
      WaystoneTeleportContext context, Waystone requested) {
    var entity = context.getEntity();
    var server = entity.getServer();
    if (server == null) {
      Vec3 projected =
          SableWaystoneCompat.projectToVisible(entity.level(), Vec3.atCenterOf(requested.getPos()));
      return Optional.of(Vec3.atCenterOf(BlockPos.containing(projected)));
    }
    var stone = canonical(server, requested).orElse(null);
    if (stone == null) return Optional.empty();
    var level = server.getLevel(stone.getDimension());
    return level == null
        ? Optional.empty()
        : SableWaystoneCompat.resolveVisibleWaystonePos(level, stone)
            .map(position -> Vec3.atCenterOf(BlockPos.containing(position)));
  }

  private static OptionalDouble horizontalDistance(WaystoneTeleportContext context) {
    var source = context.getFromWaystone().flatMap(stone -> visiblePosition(context, stone));
    var target = visiblePosition(context, context.getTargetWaystone());
    if (source.isEmpty() || target.isEmpty()) return OptionalDouble.empty();
    return OptionalDouble.of(
        Math.hypot(
            source.orElseThrow().x - target.orElseThrow().x,
            source.orElseThrow().z - target.orElseThrow().z));
  }

  private static void complete(WaystoneTeleportEvent.Complete event) {
    var context = event.getContext();
    var payment = PENDING.get(context.getEntity().getUUID());
    if (payment == null || payment.context != context) return;
    boolean successful =
        event.getPrimaryResult().map(result -> result.isSuccessful()).orElse(false);
    payment.finish(successful);
  }

  public static void failed(WaystoneTeleportContext context) {
    var server = context.getEntity().getServer();
    if (server == null) return;
    if (!server.isSameThread()) {
      server.execute(() -> failed(context));
      return;
    }
    var payment = PENDING.get(context.getEntity().getUUID());
    if (payment != null && payment.context == context) payment.finish(false);
  }

  private static void stopped(ServerStoppedEvent event) {
    PENDING.clear();
  }

  private static void message(Player player, String suffix) {
    player.displayClientMessage(
        Component.translatable("chat.aeroscapes_reimagine.travel." + suffix), true);
  }

  public static long experiencePoints(Player player) {
    long cumulative = WaystoneFees.cumulativePoints(player.experienceLevel);
    float progress = player.experienceProgress * player.getXpNeededForNextLevel();
    int rounded = Math.round(progress);
    long fraction = Math.abs(progress - rounded) <= 0.0001f ? rounded : (long) Math.floor(progress);
    return cumulative + fraction;
  }

  private static final class DistanceSteps implements VariableResolver {
    @Override
    public ResourceLocation getId() {
      return ResourceLocation.fromNamespaceAndPath(NAMESPACE, "waystone_distance_steps");
    }

    @Override
    public float resolve(WaystoneTeleportContext context) {
      var distance = horizontalDistance(context);
      return distance.isPresent() ? WaystoneFees.distanceSteps(distance.orElseThrow()) : 13f;
    }
  }

  private static final class Payment implements WarpRequirement {
    private final WaystoneTeleportContext context;
    private final ServerPlayer player;
    private final long started = System.nanoTime();
    private int quoted;
    private int charged;

    private Payment(WaystoneTeleportContext context, ServerPlayer player) {
      this.context = context;
      this.player = player;
    }

    private boolean expired() {
      return System.nanoTime() - started > PENDING_NANOS;
    }

    @Override
    public boolean canAfford(Player payer) {
      if (payer != player
          || PENDING.get(player.getUUID()) != this
          || expired()
          || !physicalContext(player, context)) return false;
      // Unresolved saved ship positions use the displayed cap until native preparation restores
      // them.
      quoted =
          context.isDimensionalTeleport()
              ? WaystoneFees.points(true, 0)
              : WaystoneFees.points(false, horizontalDistance(context).orElse(Double.MAX_VALUE));
      return experiencePoints(player) >= quoted;
    }

    @Override
    public void consume(Player payer) {
      if (payer != player || charged != 0 || player.getAbilities().instabuild) return;
      // The provider calls canAfford immediately before this synchronous consumption.
      charged = quoted;
      player.giveExperiencePoints(-charged);
    }

    @Override
    public void rollback(Player payer) {
      if (payer == player && charged != 0) {
        int refund = charged;
        charged = 0;
        player.giveExperiencePoints(refund);
      }
    }

    private void finish(boolean successful) {
      PENDING.remove(player.getUUID(), this);
      if (!successful) rollback(player);
      charged = 0;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public void appendHoverText(Player payer, List<Component> tooltip) {
      new ExperiencePointsRequirement(quoted).appendHoverText(payer, tooltip);
    }
  }
}
