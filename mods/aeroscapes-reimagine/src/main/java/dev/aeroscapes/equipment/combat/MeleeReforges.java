package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.ReforgeProfiles;
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
    var rank = ReforgeProfiles.path(weapon);
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
            case "mace_epic", "mace_legendary_brace" -> 0.08F;
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

    switch (ReforgeProfiles.path(weapon)) {
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
