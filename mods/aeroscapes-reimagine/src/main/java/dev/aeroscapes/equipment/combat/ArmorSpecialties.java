package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.ReforgeProfiles;
import dev.aeroscapes.movement.HookReforgeSpeed;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class ArmorSpecialties {
  private static final String HEAD = "armor_head_legendary_air_with_second_impact";
  private static final String CHEST = "armor_chest_legendary_cushion_with_renewed_guard";
  private static final String LEGS = "armor_legs_legendary_retreat_with_hold_ground";
  private static final String FEET = "armor_feet_legendary_landing_with_edge_landing";
  private static final String DIVING_CHEST = "armor_chest_legendary_cushion_with_bottom_brace";
  private static final String DEFENSE = "armor_shared_defense";
  private static final String KNOCKBACK = "armor_shared_knockback";
  private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();
  private static final String WATER_LAUNCH = "diving_launch";

  private ArmorSpecialties() {}

  public static boolean spendDefense(ServerPlayer player) {
    return spend(player, DEFENSE, 20);
  }

  public static boolean spendKnockback(ServerPlayer player) {
    return spend(player, KNOCKBACK, 20);
  }

  private static boolean spend(Player player, String name, int ticks) {
    if (CombatReforgeState.active(player, name)) return false;
    CombatReforgeState.activate(player, name, ticks);
    return true;
  }

  public static String profile(Player player, EquipmentSlot slot) {
    return ReforgeProfiles.path(player.getItemBySlot(slot));
  }

  private static boolean atBottom(Player player) {
    return player.isUnderWater() && player.onGround();
  }

  @SubscribeEvent(priority = EventPriority.LOW)
  public static void breathe(LivingBreatheEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || event.canBreathe()
        || !player.isUnderWater()
        || event.getConsumeAirAmount() <= 0) return;
    switch (profile(player, EquipmentSlot.HEAD)) {
      case "armor_head_poor_with_air_load" -> {
        if (player.isSwimming() && player.getRandom().nextFloat() < 0.18F) {
          event.setConsumeAirAmount(event.getConsumeAirAmount() + 1);
        }
      }
      case "diving_head_exceptional_air_with_bottom_air" -> {
        if (atBottom(player) && player.getRandom().nextFloat() < 0.20F) {
          event.setConsumeAirAmount(0);
        }
      }
      default -> {}
    }
  }

  public static int tankAirCost(ServerPlayer player, int cost) {
    if (!player.isUnderWater() || player.isInLava() || cost != 1) return cost;
    float savings =
        switch (profile(player, EquipmentSlot.HEAD)) {
          case "armor_head_poor_with_air_load" -> player.isSwimming() ? -0.18F : 0.0F;
          case "armor_head_fine_with_air_economy" -> 0.10F;
          case "diving_head_exceptional_air_with_bottom_air" -> atBottom(player) ? 0.20F : 0.0F;
          case "diving_head_epic_with_air_economy" -> 0.18F;
          default -> 0.0F;
        };
    return savings != 0.0F && player.getRandom().nextFloat() < Math.abs(savings)
        ? (savings > 0.0F ? 0 : 2)
        : cost;
  }

  public static void steadyAtBottom(Player player) {
    if (atBottom(player)
        && player.isShiftKeyDown()
        && profile(player, EquipmentSlot.LEGS)
            .equals("armor_legs_legendary_retreat_with_bottom_anchor")) {
      player.setDeltaMovement(player.getDeltaMovement().multiply(0.88, 1, 0.88));
    }
  }

  public static void swimUp(Player player) {
    if (!player.isUnderWater() || player.isPassenger() || player.getAbilities().flying) return;
    var velocity = player.getDeltaMovement();
    if (velocity.y <= 0.0 || velocity.y >= 0.20) return;
    double impulse = 0.0;
    if (atBottom(player)) {
      impulse =
          switch (profile(player, EquipmentSlot.FEET)) {
            case "diving_feet_exceptional_swim_with_push_off" -> 0.02;
            case "armor_feet_epic_with_push_off" -> 0.04;
            case "armor_feet_legendary_landing_with_bottom_launch" -> 0.10;
            default -> 0.0;
          };
    }
    boolean surfacing =
        player.getAirSupply() <= 30
            && profile(player, EquipmentSlot.HEAD)
                .equals("diving_head_legendary_air_with_surface_impulse");
    if (surfacing) impulse = Math.max(impulse, 0.075);
    int cooldown = surfacing ? 400 : impulse >= 0.10 ? 120 : 20;
    if (impulse == 0.0
        || (ModList.get().isLoaded("yo_hooks") && HookReforgeSpeed.taut(player))
        || !spend(player, WATER_LAUNCH, cooldown)) return;
    player.setDeltaMovement(velocity.x, Math.min(0.20, velocity.y + impulse), velocity.z);
  }

  public static float iceFriction(Player player, float friction) {
    return switch (profile(player, EquipmentSlot.FEET)) {
      case "armor_feet_poor_with_ice_slip" -> Math.min(0.998F, friction + 0.012F);
      case "armor_feet_fine_with_ice_grip" -> friction * 0.94F;
      case "armor_feet_exceptional_fall_with_ice_grip" -> friction * 0.90F;
      case "armor_feet_epic_with_ice_grip" -> friction * 0.88F;
      default -> friction;
    };
  }

  @SubscribeEvent(priority = EventPriority.NORMAL)
  public static void protect(LivingDamageEvent.Pre event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || event.getNewDamage() <= 0
        || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
    var source = event.getSource();
    String helmet = profile(player, EquipmentSlot.HEAD);
    String chest = profile(player, EquipmentSlot.CHEST);
    float amount = event.getNewDamage() * damageFactor(player, source, helmet, chest);
    var state = STATES.get(player);
    if (state != null
        && source.is(DamageTypeTags.IS_PROJECTILE)
        && HEAD.equals(helmet)
        && state.head == player.getItemBySlot(EquipmentSlot.HEAD)
        && CombatReforgeState.active(player, HEAD)
        && spendDefense(player)) {
      CombatReforgeState.clear(player, HEAD);
      amount *= 0.75F;
    } else if (state != null
        && CHEST.equals(chest)
        && state.chest == player.getItemBySlot(EquipmentSlot.CHEST)
        && state.guardTicks >= 160
        && source.getEntity() != null
        && source.getEntity() != player
        && spendDefense(player)) {
      state.guardTicks = 0;
      amount -= Math.min(2.0F, amount * 0.40F);
    }
    event.setNewDamage(amount);
  }

  private static float damageFactor(
      ServerPlayer player, DamageSource source, String helmet, String chest) {
    if (source.is(DamageTypeTags.IS_PROJECTILE)) {
      return switch (helmet) {
        case "armor_head_poor_with_exposure" -> 1.08F;
        case "armor_head_fine_with_visor" -> 0.95F;
        case "armor_head_epic_with_visor" -> 0.91F;
        default -> 1.0F;
      };
    }
    if (source.is(DamageTypeTags.IS_EXPLOSION)
        && helmet.equals("armor_head_exceptional_with_blast_lining")) return 0.90F;
    if (source.is(DamageTypes.MOB_ATTACK)
        || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
        || source.is(DamageTypes.PLAYER_ATTACK)) {
      return switch (chest) {
        case "armor_chest_exceptional_with_close_guard" -> 0.93F;
        case "armor_chest_epic_with_close_guard" -> 0.90F;
        case "armor_chest_exceptional_with_water_guard" -> player.isUnderWater() ? 0.92F : 1.0F;
        default -> 1.0F;
      };
    }
    return 1.0F;
  }

  @SubscribeEvent
  public static void damaged(LivingDamageEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || !player.isAlive()
        || event.getNewDamage() <= 0) return;
    var state = STATES.get(player);
    if (state == null) return;
    state.guardTicks = 0;
    if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)
        && HEAD.equals(profile(player, EquipmentSlot.HEAD))
        && spend(player, HEAD + "_cooldown", 200)) {
      CombatReforgeState.activate(player, HEAD, 60);
    }
    if (event.getSource().is(DamageTypeTags.IS_FALL)
        && event.getNewDamage() >= 4.0F
        && FEET.equals(profile(player, EquipmentSlot.FEET))
        && spend(player, FEET + "_cooldown", 240)) {
      CombatReforgeState.activate(player, FEET, 60);
    }
  }

  @SubscribeEvent(priority = EventPriority.LOW)
  public static void knockback(LivingKnockBackEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player) || event.getStrength() <= 0) return;
    String chest = profile(player, EquipmentSlot.CHEST);
    String legs = profile(player, EquipmentSlot.LEGS);
    String feet = profile(player, EquipmentSlot.FEET);
    var state = STATES.get(player);
    float factor = waterKnockback(player, chest, legs);
    if (state != null
        && DIVING_CHEST.equals(chest)
        && state.chest == player.getItemBySlot(EquipmentSlot.CHEST)
        && state.bottomReady
        && player.isUnderWater()
        && spendKnockback(player)) {
      state.bottomReady = false;
      CombatReforgeState.activate(player, DIVING_CHEST, 200);
      factor = 0.30F;
    } else if (state != null
        && state.feet == player.getItemBySlot(EquipmentSlot.FEET)
        && FEET.equals(feet)
        && CombatReforgeState.active(player, FEET)
        && edgeAhead(player, event)
        && spendKnockback(player)) {
      CombatReforgeState.clear(player, FEET);
      factor = 0.30F;
    } else if (state != null
        && LEGS.equals(legs)
        && state.legs == player.getItemBySlot(EquipmentSlot.LEGS)
        && state.stationaryTicks >= 20
        && !CombatReforgeState.active(player, LEGS)
        && spendKnockback(player)) {
      CombatReforgeState.activate(player, LEGS, 160);
      state.stationaryTicks = 0;
      factor = 0.40F;
    }
    event.setStrength(event.getStrength() * factor);
  }

  private static float waterKnockback(ServerPlayer player, String chest, String legs) {
    if (chest.equals("armor_chest_poor_with_loose_fit")) return 1.08F;
    if (!player.isUnderWater()) return 1.0F;
    if (legs.equals("armor_legs_legendary_retreat_with_bottom_anchor")
        && atBottom(player)
        && player.isShiftKeyDown()) return 0.70F;
    return switch (chest) {
      case "armor_chest_poor_with_water_sway" -> 1.12F;
      case "armor_chest_fine_with_water_brace" -> 0.90F;
      case "armor_chest_epic_with_water_brace" -> 0.80F;
      default -> 1.0F;
    };
  }

  private static boolean edgeAhead(ServerPlayer player, LivingKnockBackEvent event) {
    var offset = new Vec3(-event.getRatioX(), 0, -event.getRatioZ()).normalize();
    var ahead = player.getBoundingBox().move(offset);
    return offset.lengthSqr() > 0
        && player.onGround()
        && player.level().noCollision(player, ahead.expandTowards(0, -1.75, 0));
  }

  @SubscribeEvent
  public static void tick(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    var head = player.getItemBySlot(EquipmentSlot.HEAD);
    var chest = player.getItemBySlot(EquipmentSlot.CHEST);
    var legs = player.getItemBySlot(EquipmentSlot.LEGS);
    var feet = player.getItemBySlot(EquipmentSlot.FEET);
    boolean dryBrace = ReforgeProfiles.has(legs, LEGS);
    boolean wetBrace = ReforgeProfiles.has(chest, DIVING_CHEST);
    boolean guard = ReforgeProfiles.has(chest, CHEST);
    if (!dryBrace
        && !wetBrace
        && !guard
        && !ReforgeProfiles.has(head, HEAD)
        && !ReforgeProfiles.has(feet, FEET)) {
      STATES.remove(player);
      CombatReforgeState.clear(player, HEAD);
      CombatReforgeState.clear(player, FEET);
      return;
    }
    var state = STATES.computeIfAbsent(player, ignored -> new State());
    if (state.head != head) CombatReforgeState.clear(player, HEAD);
    if (state.feet != feet) CombatReforgeState.clear(player, FEET);
    boolean sameChest = state.chest == chest;
    state.guardTicks = guard ? Math.min(160, (sameChest ? state.guardTicks : 0) + 1) : 0;
    boolean stationary =
        player.onGround()
            && player.position().distanceToSqr(state.position) < 0.001
            && !player.isPassenger();
    if (!wetBrace || !sameChest || !player.isUnderWater()) state.bottomReady = false;
    if (wetBrace
        && stationary
        && atBottom(player)
        && sameChest
        && !CombatReforgeState.active(player, DIVING_CHEST)) {
      if ((state.bottomTicks = Math.min(20, state.bottomTicks + 1)) >= 20) state.bottomReady = true;
    } else {
      state.bottomTicks = 0;
    }
    state.stationaryTicks =
        dryBrace && stationary && state.legs == legs ? Math.min(20, state.stationaryTicks + 1) : 0;
    state.position = player.position();
    state.head = head;
    state.chest = chest;
    state.legs = legs;
    state.feet = feet;
  }

  private static final class State {
    private Vec3 position = Vec3.ZERO;
    private ItemStack head = ItemStack.EMPTY;
    private ItemStack chest = ItemStack.EMPTY;
    private ItemStack legs = ItemStack.EMPTY;
    private ItemStack feet = ItemStack.EMPTY;
    private int guardTicks;
    private int stationaryTicks;
    private int bottomTicks;
    private boolean bottomReady;
  }
}
