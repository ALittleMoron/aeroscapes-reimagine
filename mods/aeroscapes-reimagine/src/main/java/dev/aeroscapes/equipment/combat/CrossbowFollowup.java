package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class CrossbowFollowup {
  private static final String RANK = "crossbow_legendary_followup";
  private static final String READY = "crossbow_followup_ready";
  private static final String CHARGING = "crossbow_followup_charging";
  private static final String COOLDOWN = "crossbow_followup_cooldown";

  private CrossbowFollowup() {}

  static void arm(ServerPlayer player) {
    if (!CombatReforgeState.flag(player, READY) && !CombatReforgeState.active(player, COOLDOWN)) {
      CombatReforgeState.activate(player, COOLDOWN, 160);
      CombatReforgeState.setFlag(player, READY, true);
      sync(player);
    }
  }

  public static int chargeDuration(ItemStack stack, LivingEntity user, int duration) {
    String rank = ReforgeProfiles.path(stack);
    return switch (rank) {
      case "ranged_poor_with_crossbow_drag" -> RangedReforges.scaleTicks(duration, 1.16F);
      case "ranged_ordinary_with_crossbow_tempo" -> RangedReforges.scaleTicks(duration, 0.96F);
      case "ranged_fine_with_crossbow_tempo" -> RangedReforges.scaleTicks(duration, 0.92F);
      case "crossbow_epic_with_trajectory" -> RangedReforges.scaleTicks(duration, 0.85F);
      default -> baseChargeDuration(stack, user, duration, ReforgeProfiles.basePath(rank));
    };
  }

  private static int baseChargeDuration(
      ItemStack stack, LivingEntity user, int duration, String rank) {
    return switch (rank) {
      case "crossbow_exceptional_charge" -> RangedReforges.scaleTicks(duration, 0.88F);
      case "crossbow_epic", "crossbow_legendary_fire_line" ->
          RangedReforges.scaleTicks(duration, 0.90F);
      case RANK -> {
        String flag = user.isUsingItem() && user.getUseItem() == stack ? CHARGING : READY;
        yield RangedReforges.scaleTicks(
            duration, CombatReforgeState.flag(user, flag) ? 0.90F * 0.75F : 0.90F);
      }
      default -> duration;
    };
  }

  public static void onChargeCompleted(LivingEntity user, ItemStack weapon) {
    consumeFollowup(user, weapon, CHARGING);
  }

  public static void onAutoChargeCompleted(LivingEntity user, ItemStack weapon) {
    consumeFollowup(user, weapon, READY);
  }

  private static void consumeFollowup(LivingEntity user, ItemStack weapon, String flag) {
    if (user instanceof ServerPlayer player
        && RANK.equals(ReforgeProfiles.basePath(weapon))
        && CombatReforgeState.flag(player, flag)) {
      CombatReforgeState.clear(player, CHARGING);
      CombatReforgeState.setFlag(player, READY, false);
      sync(player);
    }
  }

  @SubscribeEvent
  public static void startCharge(LivingEntityUseItemEvent.Start event) {
    if (RANK.equals(ReforgeProfiles.basePath(event.getItem()))) {
      var user = event.getEntity();
      CombatReforgeState.setFlag(user, CHARGING, CombatReforgeState.flag(user, READY));
    }
  }

  @SubscribeEvent
  public static void login(PlayerEvent.PlayerLoggedInEvent event) {
    if (event.getEntity() instanceof ServerPlayer player
        && CombatReforgeState.flag(player, READY)) {
      sync(player);
    }
  }

  @SubscribeEvent
  public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      sync(player);
    }
  }

  @SubscribeEvent
  public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player
        && CombatReforgeState.flag(player, READY)) {
      sync(player);
    }
  }

  @SubscribeEvent
  public static void returningFromEnd(PlayerEvent.Clone event) {
    if (!event.isWasDeath()) {
      CombatReforgeState.copyState(event.getOriginal(), event.getEntity());
    }
  }

  @SubscribeEvent
  public static void registerPayload(RegisterPayloadHandlersEvent event) {
    event
        .registrar("1")
        .playToClient(
            Status.TYPE,
            Status.CODEC,
            (status, context) ->
                context.enqueueWork(
                    () -> CombatReforgeState.setFlag(context.player(), READY, status.ready())));
  }

  private static void sync(ServerPlayer player) {
    PacketDistributor.sendToPlayer(player, new Status(CombatReforgeState.flag(player, READY)));
  }

  private record Status(boolean ready) implements CustomPacketPayload {
    private static final Type<Status> TYPE =
        new Type<>(
            ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", "crossbow_followup"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC =
        StreamCodec.of(
            (buffer, status) -> buffer.writeBoolean(status.ready),
            buffer -> new Status(buffer.readBoolean()));

    @Override
    public Type<Status> type() {
      return TYPE;
    }
  }
}
