package dev.aeroscapes.equipment.combat;

import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class ArmorReforges {
  private static final String AIR = "armor_air";
  private static final String RETREAT = "armor_retreat";

  private ArmorReforges() {}

  @SubscribeEvent(priority = EventPriority.LOW)
  public static void breathe(LivingBreatheEvent event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || event.canBreathe()
        || !player.isUnderWater()) {
      return;
    }
    var helmet = player.getItemBySlot(EquipmentSlot.HEAD);
    if (!ReforgeProfiles.has(helmet, "armor_head_legendary_air")
        && !ReforgeProfiles.has(helmet, "diving_head_legendary_air")) {
      return;
    }
    if (player.getAirSupply() <= 20 && !CombatReforgeState.active(player, AIR + "_cooldown")) {
      CombatReforgeState.activate(player, AIR, 80);
      CombatReforgeState.activate(player, AIR + "_cooldown", 900);
    }
    if (CombatReforgeState.active(player, AIR)) {
      event.setCanBreathe(true);
      event.setRefillAirAmount(0);
    }
  }

  @SubscribeEvent(priority = EventPriority.LOW)
  public static void cushion(LivingDamageEvent.Pre event) {
    if (!(event.getEntity() instanceof ServerPlayer player)
        || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
      return;
    }
    float damage = event.getNewDamage();
    if (event.getSource().is(DamageTypeTags.IS_FALL)) {
      reduce(
          event,
          player,
          damage,
          4.0F,
          0.50F,
          EquipmentSlot.FEET,
          "armor_feet_legendary_landing",
          400);
    } else if (event.getSource().getEntity() != null) {
      reduce(
          event,
          player,
          damage,
          8.0F,
          0.80F,
          EquipmentSlot.CHEST,
          "armor_chest_legendary_cushion",
          300);
    }
  }

  private static void reduce(
      LivingDamageEvent.Pre event,
      ServerPlayer player,
      float damage,
      float threshold,
      float multiplier,
      EquipmentSlot slot,
      String rank,
      int cooldown) {
    if (damage >= threshold
        && ReforgeProfiles.has(player.getItemBySlot(slot), rank)
        && !CombatReforgeState.active(player, rank)) {
      event.setNewDamage(damage * multiplier);
      CombatReforgeState.activate(player, rank, cooldown);
    }
  }

  @SubscribeEvent
  public static void retreat(LivingDamageEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player
        && player.isAlive()
        && event.getNewDamage() > 0.0F
        && event.getSource().getEntity() != null
        && ReforgeProfiles.has(
            player.getItemBySlot(EquipmentSlot.LEGS), "armor_legs_legendary_retreat")
        && !CombatReforgeState.active(player, RETREAT + "_cooldown")) {
      CombatReforgeState.activate(player, RETREAT, 40);
      CombatReforgeState.activate(player, RETREAT + "_cooldown", 200);
      updateRetreat(player);
    }
  }

  @SubscribeEvent
  public static void tick(PlayerTickEvent.Post event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      updateRetreat(player);
      MeleeReforges.updateBrace(player);
    }
  }

  private static void updateRetreat(ServerPlayer player) {
    CombatReforgeState.updateAttribute(
        player,
        RETREAT,
        Attributes.MOVEMENT_SPEED,
        0.15,
        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
  }
}
