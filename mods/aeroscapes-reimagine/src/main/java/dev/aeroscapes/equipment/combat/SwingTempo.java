package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.magic.client.SwingTempoClient;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

public final class SwingTempo {
  private static final float RECOVERY_FACTOR = 0.70F;
  private static final int MAX_SWING_TICKS = 24;
  private static final Map<ServerPlayer, Opportunities> PLAYERS = new WeakHashMap<>();
  private static final ThreadLocal<Attack> ATTACK = new ThreadLocal<>();

  private SwingTempo() {}

  public enum Style {
    PARRY,
    HEAVY;

    public boolean accepts(ItemStack weapon) {
      return switch (this) {
        case PARRY -> MartialTechniques.isLightWeapon(weapon);
        case HEAVY -> MartialTechniques.isHeavyWeapon(weapon);
      };
    }
  }

  private record Opportunity(
      Style style, int token, @Nullable UUID target, int entityId, long end) {}

  private record Committed(Opportunity window, long end, boolean valid) {}

  private static final class Opportunities {
    private final EnumMap<Style, Opportunity> windows = new EnumMap<>(Style.class);
    private int nextToken;
    private @Nullable Committed committed;
  }

  private static final class Attack {
    private final ServerPlayer player;
    private final @Nullable ItemStack weapon;
    private final InteractionHand hand;
    private final int slot;
    private boolean grantedHeavy;

    private Attack(
        ServerPlayer player, @Nullable ItemStack weapon, InteractionHand hand, int slot) {
      this.player = player;
      this.weapon = weapon;
      this.hand = hand;
      this.slot = slot;
    }
  }

  public static void grantParry(ServerPlayer player, LivingEntity attacker, long deadline) {
    grant(player, Style.PARRY, attacker.getUUID(), attacker.getId(), deadline);
  }

  public static void grantHeavy(ServerPlayer player, long deadline) {
    Attack attack = ATTACK.get();
    if (attack != null && attack.player == player) {
      if (attack.grantedHeavy) return;
      attack.grantedHeavy = true;
    }
    grant(player, Style.HEAVY, null, -1, deadline);
  }

  private static void grant(
      ServerPlayer player, Style style, @Nullable UUID target, int entityId, long deadline) {
    long now = MartialTechniques.now(player);
    if (deadline <= now) return;
    Opportunities state = PLAYERS.computeIfAbsent(player, ignored -> new Opportunities());
    var existing = state.windows.get(style);
    if (existing != null && existing.end() > now) return;
    var window = new Opportunity(style, ++state.nextToken, target, entityId, deadline);
    state.windows.put(style, window);
    sync(player, window, Math.toIntExact(deadline - now));
    if (style == Style.HEAVY) SpellKnowledge.message(player, "technique_stance_swing_ready");
  }

  public static void clearParry(ServerPlayer player) {
    clear(player, Style.PARRY);
  }

  public static void clearHeavy(ServerPlayer player) {
    clear(player, Style.HEAVY);
  }

  private static void clear(ServerPlayer player, Style style) {
    var state = PLAYERS.get(player);
    if (state == null) return;
    var window = state.windows.get(style);
    if (window != null && window.end() != 0) {
      state.windows.put(style, new Opportunity(style, window.token(), null, -1, 0));
      sync(player, window, 0);
    }
    var committed = state.committed;
    if (committed != null && committed.window().style() == style) {
      state.committed = new Committed(committed.window(), committed.end(), false);
    }
  }

  public static void reset(ServerPlayer player) {
    clearParry(player);
    clearHeavy(player);
    PLAYERS.remove(player);
  }

  private static void sync(ServerPlayer player, Opportunity window, int ticks) {
    if (player.connection != null) {
      PacketDistributor.sendToPlayer(
          player, new Window(window.style(), window.token(), window.entityId(), ticks));
    }
  }

  public static void acceptAttempt(ServerPlayer player, Attempt message) {
    var state = PLAYERS.get(player);
    if (state == null) return;
    var committed = state.committed;
    if (!message.begin()) {
      if (committed != null && committed.window().token() == message.token()) {
        state.committed = null;
      }
      return;
    }
    long now = MartialTechniques.now(player);
    if (committed != null && now < committed.end()) return;
    for (Style style : Style.values()) {
      var window = state.windows.get(style);
      if (window == null || window.token() != message.token()) continue;
      state.windows.remove(style);
      state.committed = new Committed(window, now + MAX_SWING_TICKS, now < window.end());
      return;
    }
  }

  public static void attack(
      ServerPlayer player,
      ItemStack selectedWeapon,
      InteractionHand hand,
      int inventorySlot,
      int cursorTarget,
      int[] targets,
      Consumer<int[]> original) {
    Opportunities state = PLAYERS.get(player);
    Committed committed = state == null ? null : state.committed;
    boolean accelerated = false;
    if (committed != null) {
      state.committed = null;
      Opportunity window = committed.window();
      long now = MartialTechniques.now(player);
      accelerated =
          committed.valid()
              && now < committed.end()
              && player.isAlive()
              && inventorySlot == player.getInventory().selected
              && window.style().accepts(selectedWeapon);
      if (!accelerated) {
        targets = new int[0];
      } else if (window.style() == Style.PARRY) {
        targets = replyTargets(player, cursorTarget, window, targets);
      }
    }
    int[] acceptedTargets = targets;
    var attack =
        new Attack(player, accelerated ? selectedWeapon.copy() : null, hand, inventorySlot);
    withinAttack(attack, () -> original.accept(acceptedTargets));
  }

  // Better Combat queues the actual swing when the server is handling another task.
  public static Runnable carryAttack(Runnable action) {
    var attack = ATTACK.get();
    if (attack == null) return action;
    var player = attack.player;
    var level = player.level();
    return () -> {
      if (attack.weapon == null
          || player.isAlive()
              && player.level() == level
              && player.getInventory().selected == attack.slot
              && ItemStack.matches(player.getItemInHand(attack.hand), attack.weapon)) {
        withinAttack(attack, action);
      }
    };
  }

  private static void withinAttack(Attack attack, Runnable action) {
    Attack previous = ATTACK.get();
    ATTACK.set(attack);
    try {
      action.run();
    } finally {
      if (previous == null) ATTACK.remove();
      else ATTACK.set(previous);
    }
  }

  private static int[] replyTargets(
      ServerPlayer player, int cursorTarget, Opportunity window, int[] targets) {
    int attackerId = window.entityId();
    boolean intended =
        cursorTarget == attackerId
            ? Arrays.stream(targets).anyMatch(target -> target == attackerId)
            : cursorTarget == -1 && targets.length == 1 && targets[0] == attackerId;
    if (!intended) return new int[0];
    var attacker = player.serverLevel().getEntity(attackerId);
    if (attacker instanceof LivingEntity living
        && living.isAlive()
        && living.getUUID().equals(window.target())) {
      return new int[] {attackerId};
    }
    return new int[0];
  }

  public static float recoveryTicks(Player player, float ticks) {
    Attack attack = ATTACK.get();
    return attack != null && attack.player == player && attack.weapon != null
        ? fasterRecovery(ticks)
        : ticks;
  }

  public static float fasterRecovery(float ticks) {
    return ticks * RECOVERY_FACTOR;
  }

  public static float fasterCharge(float normalCharge) {
    return Math.min(1.0F, normalCharge / RECOVERY_FACTOR);
  }

  public static void register(RegisterPayloadHandlersEvent event) {
    var registrar = event.registrar("1");
    registrar.playToClient(
        Window.TYPE, Window.CODEC, (message, context) -> SwingTempoClient.accept(message));
    registrar.playToServer(
        Attempt.TYPE,
        Attempt.CODEC,
        (message, context) -> {
          if (context.player() instanceof ServerPlayer player) acceptAttempt(player, message);
        });
  }

  public record Window(Style style, int token, int target, int ticks)
      implements CustomPacketPayload {
    public static final Type<Window> TYPE = SwingTempo.type("swing_window");
    public static final StreamCodec<RegistryFriendlyByteBuf, Window> CODEC =
        StreamCodec.of(
            (buffer, message) -> {
              buffer.writeEnum(message.style());
              buffer.writeVarInt(message.token());
              buffer.writeVarInt(message.target());
              buffer.writeVarInt(message.ticks());
            },
            buffer ->
                new Window(
                    buffer.readEnum(Style.class),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt()));

    @Override
    public Type<Window> type() {
      return TYPE;
    }
  }

  public record Attempt(int token, boolean begin) implements CustomPacketPayload {
    public static final Type<Attempt> TYPE = SwingTempo.type("swing_attempt");
    public static final StreamCodec<RegistryFriendlyByteBuf, Attempt> CODEC =
        StreamCodec.of(
            (buffer, message) -> {
              buffer.writeVarInt(message.token());
              buffer.writeBoolean(message.begin());
            },
            buffer -> new Attempt(buffer.readVarInt(), buffer.readBoolean()));

    @Override
    public Type<Attempt> type() {
      return TYPE;
    }
  }

  private static <T extends CustomPacketPayload> Type<T> type(String id) {
    return new Type<>(ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", id));
  }
}
