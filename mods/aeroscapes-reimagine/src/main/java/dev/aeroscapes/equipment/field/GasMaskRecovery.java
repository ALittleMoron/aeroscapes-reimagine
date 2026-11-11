package dev.aeroscapes.equipment.field;

import dev.aeroscapes.equipment.ReforgeProfiles;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class GasMaskRecovery {
  private static final ResourceLocation GAS_CLOUD =
      ResourceLocation.fromNamespaceAndPath("createbigcannons", "gas_cloud");
  private static final TagKey<Item> GAS_MASKS =
      TagKey.create(
          Registries.ITEM, ResourceLocation.fromNamespaceAndPath("createbigcannons", "gas_masks"));
  private static final int NATIVE_SAMPLE_INTERVAL = 5;
  private static final int MINIMUM_EXPOSURE = 10;
  private static final int COOLDOWN = 600;
  private static final Map<MinecraftServer, Map<UUID, State>> SESSIONS = new WeakHashMap<>();

  private GasMaskRecovery() {}

  public static void observe(ServerPlayer player) {
    ItemStack mask = wornMask(player);
    int duration = duration(mask);
    if (duration == 0 || player.isSpectator()) return;
    var server = player.serverLevel().getServer();
    State state =
        SESSIONS
            .computeIfAbsent(server, key -> new HashMap<>())
            .computeIfAbsent(player.getUUID(), key -> new State());
    long now = server.overworld().getGameTime();
    Exposure previous = state.exposure;
    long first = now;
    if (previous != null
        && previous.matches(player, mask)
        && (previous.sustained() || now - previous.last() <= NATIVE_SAMPLE_INTERVAL))
      first = previous.first();
    state.exposure = new Exposure(mask, player.getId(), player.level().dimension(), first, now);
  }

  @SubscribeEvent
  public static void finishExposure(ServerTickEvent.Post event) {
    var server = event.getServer();
    var states = SESSIONS.get(server);
    if (states == null || states.isEmpty()) return;
    long now = server.overworld().getGameTime();
    states
        .entrySet()
        .removeIf(
            entry -> {
              State state = entry.getValue();
              if (state.exposure != null)
                checkExit(server.getPlayerList().getPlayer(entry.getKey()), state, now);
              return state.exposure == null && state.recovery == null && now >= state.readyAt;
            });
  }

  private static void checkExit(ServerPlayer player, State state, long now) {
    Exposure exposure = state.exposure;
    if (player == null || !player.isAlive() || player.isSpectator()) {
      state.exposure = null;
      return;
    }
    ItemStack mask = wornMask(player);
    if (!exposure.matches(player, mask)) {
      state.exposure = null;
      return;
    }
    if (insideGas(player)) return;
    state.exposure = null;
    if (!exposure.sustained() || now < state.readyAt || player.hasEffect(MobEffects.REGENERATION))
      return;
    int duration = duration(mask);
    if (duration == 0) return;
    var effect = new MobEffectInstance(MobEffects.REGENERATION, duration);
    if (player.addEffect(effect)) {
      state.recovery = new Recovery(mask, effect);
      state.readyAt = now + COOLDOWN;
    }
  }

  @SubscribeEvent
  public static void stopRecoveryOnReentry(PlayerTickEvent.Pre event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    State state = state(player);
    if (state == null || state.recovery == null) return;
    if (player.getEffect(MobEffects.REGENERATION) != state.recovery.effect()) {
      state.recovery = null;
    } else if (player.isSpectator()
        || wornMask(player) != state.recovery.mask()
        || insideGas(player)) {
      stopRecovery(player, state);
    }
  }

  @SubscribeEvent
  public static void preserveOtherRegeneration(MobEffectEvent.Added event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    State state = state(player);
    // Vanilla may merge another source into our active instance in place.
    if (state != null
        && state.recovery != null
        && event.getOldEffectInstance() == state.recovery.effect()) state.recovery = null;
  }

  @SubscribeEvent
  public static void forgetExposure(PlayerEvent.PlayerLoggedOutEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    State state = state(player);
    if (state == null) return;
    state.exposure = null;
    if (state.recovery != null) stopRecovery(player, state);
  }

  private static void stopRecovery(ServerPlayer player, State state) {
    if (player.getEffect(MobEffects.REGENERATION) == state.recovery.effect())
      player.removeEffect(MobEffects.REGENERATION);
    state.recovery = null;
  }

  private static State state(ServerPlayer player) {
    var states = SESSIONS.get(player.serverLevel().getServer());
    return states == null ? null : states.get(player.getUUID());
  }

  private static boolean insideGas(ServerPlayer player) {
    var gas = BuiltInRegistries.ENTITY_TYPE.get(GAS_CLOUD);
    return !player
        .serverLevel()
        .getEntities(player, player.getBoundingBox(), entity -> entity.getType() == gas)
        .isEmpty();
  }

  private static ItemStack wornMask(ServerPlayer player) {
    ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
    if (head.is(GAS_MASKS)) return head;
    if (!ModList.get().isLoaded("curios")) return ItemStack.EMPTY;
    var curiosHead =
        CuriosApi.getCuriosInventory(player)
            .flatMap(handler -> handler.getStacksHandler("head"))
            .orElse(null);
    if (curiosHead == null) return ItemStack.EMPTY;
    var stacks = curiosHead.getStacks();
    for (int index = 0; index < curiosHead.getSlots(); index++) {
      ItemStack stack = stacks.getStackInSlot(index);
      if (stack.is(GAS_MASKS)) return stack;
    }
    return ItemStack.EMPTY;
  }

  private static int duration(ItemStack mask) {
    return switch (ReforgeProfiles.path(mask)) {
      case "gas_mask_fine" -> 60;
      case "gas_mask_exceptional" -> 80;
      case "gas_mask_epic" -> 100;
      case "gas_mask_legendary" -> 120;
      default -> 0;
    };
  }

  private static final class State {
    private Exposure exposure;
    private Recovery recovery;
    private long readyAt;
  }

  private record Exposure(
      ItemStack mask, int playerId, ResourceKey<Level> dimension, long first, long last) {
    boolean matches(ServerPlayer player, ItemStack worn) {
      return mask == worn
          && playerId == player.getId()
          && dimension.equals(player.level().dimension());
    }

    boolean sustained() {
      return last - first >= MINIMUM_EXPOSURE;
    }
  }

  private record Recovery(ItemStack mask, MobEffectInstance effect) {}
}
