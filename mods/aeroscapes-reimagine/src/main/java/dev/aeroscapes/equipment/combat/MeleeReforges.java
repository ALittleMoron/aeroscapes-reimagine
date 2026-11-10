package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;

public final class MeleeReforges {
  private static final String RIPOSTE = "riposte";
  private static final String BRACE = "mace_brace";
  private static final String EXECUTE = "melee_execute";
  private static final String SECOND_WIND = "melee_second_wind";

  private MeleeReforges() {}

  public record Strike(float damage, boolean execute, boolean marked, boolean riposte) {}

  public static Strike prepare(
      Player player, LivingEntity target, ItemStack weapon, float damage, boolean charged) {
    var rank = ReforgeProfiles.basePath(weapon);
    boolean execute =
        charged
            && "melee_legendary_execute".equals(rank)
            && target.getHealth() <= target.getMaxHealth() * 0.25F
            && !CombatReforgeState.active(player, EXECUTE);
    boolean marked = charged && CombatReforgeState.markedFor(target, player);
    boolean riposte = charged && CombatReforgeState.active(player, RIPOSTE);
    float bonus = (execute ? 0.20F : 0.0F) + (marked ? 0.15F : 0.0F) + (riposte ? 0.20F : 0.0F);
    if (player.onGround() && !MaceItem.canSmashAttack(player)) {
      bonus +=
          switch (rank) {
            case "mace_exceptional_ground" -> 0.12F;
            case "mace_epic", "mace_legendary_brace", "mace_legendary_breach" -> 0.08F;
            default -> 0.0F;
          };
    }
    return new Strike(damage * (1.0F + bonus), execute, marked, riposte);
  }

  public static void onHit(
      Player player, LivingEntity target, ItemStack weapon, Strike strike, boolean charged) {
    if (strike.execute()) {
      CombatReforgeState.activate(player, EXECUTE, 120);
    }
    if (strike.marked()) {
      CombatReforgeState.clearMark(target);
    }
    if (strike.riposte()) {
      CombatReforgeState.clear(player, RIPOSTE);
    }

    switch (ReforgeProfiles.basePath(weapon)) {
      case "melee_legendary_frost" -> {
        if (charged
            && target.isAlive()
            && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
            && player.getRandom().nextFloat() < 0.20F) {
          target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40), player);
        }
      }
      case "melee_legendary_second_wind" -> {
        if (charged
            && target instanceof Enemy
            && target.getPose() == Pose.DYING
            && player.getHealth() < player.getMaxHealth()
            && !CombatReforgeState.active(player, SECOND_WIND)) {
          CombatReforgeState.activate(player, SECOND_WIND, 240);
          player.heal(2.0F);
        }
      }
      case "mace_legendary_brace" -> {
        if (MaceItem.canSmashAttack(player)
            && !CombatReforgeState.active(player, BRACE + "_cooldown")) {
          CombatReforgeState.activate(player, BRACE + "_cooldown", 160);
          CombatReforgeState.activate(player, BRACE, 40);
          updateBrace(player);
        }
      }
      default -> {}
    }
  }

  public static void onDamagingHit(
      ServerPlayer player, LivingEntity target, ItemStack weapon, boolean charged) {
    if (!hostile(player, target)) {
      return;
    }
    switch (ReforgeProfiles.path(weapon)) {
      case "melee_legendary_circle_guard" -> {
        if (charged && !CombatReforgeState.active(player, "melee_circle_guard")) {
          var previous = StackCombatWindows.get(player, weapon);
          if (previous == null) {
            StackCombatWindows.start(player, weapon, 60, target.getUUID());
          } else if (!target.getUUID().equals(previous.firstTarget())) {
            StackCombatWindows.start(player, weapon, 40, null);
            CombatReforgeState.activate(player, "melee_circle_guard", 160);
          }
        }
      }
      case "mace_legendary_breach" -> {
        if (MaceItem.canSmashAttack(player)
            && target.isAlive()
            && !CombatReforgeState.active(player, "mace_breach")
            && target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40), player)) {
          CombatReforgeState.activate(player, "mace_breach", 120);
        }
      }
      default -> {}
    }
  }

  public static float attackKnockback(
      Player attacker, LivingEntity target, ItemStack weapon, float original, boolean charged) {
    if (!(attacker instanceof ServerPlayer player) || !charged) {
      return original;
    }
    float bonus =
        switch (ReforgeProfiles.path(weapon)) {
          case "melee_exceptional_with_drive" -> 0.45F;
          case "mace_ordinary_with_ground_drive" -> player.onGround() ? 0.10F : 0.0F;
          case "mace_fine_with_ground_drive" -> player.onGround() ? 0.20F : 0.0F;
          case "mace_exceptional_ground_with_ground_drive" -> player.onGround() ? 0.35F : 0.0F;
          case "mace_epic_with_ground_drive" -> player.onGround() ? 0.55F : 0.0F;
          case "trident_legendary_return_thrust" ->
              hostile(player, target) && StackCombatWindows.consume(player, weapon) ? 0.85F : 0.0F;
          default -> 0.0F;
        };
    return original + bonus;
  }

  static boolean guarding(ServerPlayer player) {
    ItemStack weapon = player.getMainHandItem();
    if (!ReforgeProfiles.has(weapon, "melee_legendary_circle_guard")) {
      return false;
    }
    var window = StackCombatWindows.get(player, weapon);
    return window != null && window.firstTarget() == null;
  }

  static boolean hostile(Player player, LivingEntity target) {
    return target instanceof Enemy && !player.isAlliedTo(target);
  }

  static void armRiposte(Player player) {
    if (!CombatReforgeState.active(player, RIPOSTE + "_cooldown")) {
      CombatReforgeState.activate(player, RIPOSTE + "_cooldown", 160);
      CombatReforgeState.activate(player, RIPOSTE, 60);
    }
  }

  static void updateBrace(Player player) {
    CombatReforgeState.updateAttribute(
        player,
        BRACE,
        Attributes.KNOCKBACK_RESISTANCE,
        0.20,
        AttributeModifier.Operation.ADD_VALUE);
  }
}
