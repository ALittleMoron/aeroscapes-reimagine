package dev.aeroscapes.equipment.combat;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

final class CombatReforgeState {
  private static final String ROOT = "aeroscapes_reimagine:combat_reforge";
  private static final String MARK_OWNER = "mark_owner";
  private static final String MARK_EXPIRY = "mark_expiry";

  private CombatReforgeState() {}

  static boolean active(LivingEntity entity, String effect) {
    return state(entity).getLong(effect) > entity.level().getGameTime();
  }

  static void activate(LivingEntity entity, String effect, int ticks) {
    var data = entity.getPersistentData();
    var state = data.getCompound(ROOT);
    state.putLong(effect, entity.level().getGameTime() + ticks);
    data.put(ROOT, state);
  }

  static void clear(LivingEntity entity, String effect) {
    state(entity).remove(effect);
  }

  static boolean flag(LivingEntity entity, String effect) {
    return state(entity).getBoolean(effect);
  }

  static void setFlag(LivingEntity entity, String effect, boolean value) {
    if (value) {
      var data = entity.getPersistentData();
      var state = data.getCompound(ROOT);
      state.putBoolean(effect, true);
      data.put(ROOT, state);
    } else {
      clear(entity, effect);
    }
  }

  static void mark(LivingEntity target, Player shooter) {
    activate(target, MARK_EXPIRY, 80);
    state(target).putUUID(MARK_OWNER, shooter.getUUID());
  }

  static boolean markedFor(LivingEntity target, Player attacker) {
    var state = state(target);
    return active(target, MARK_EXPIRY)
        && state.hasUUID(MARK_OWNER)
        && attacker.getUUID().equals(state.getUUID(MARK_OWNER));
  }

  static void clearMark(LivingEntity target) {
    clear(target, MARK_EXPIRY);
    clear(target, MARK_OWNER);
  }

  static void copyState(Player source, Player target) {
    var state = state(source);
    if (!state.isEmpty()) {
      target.getPersistentData().put(ROOT, state.copy());
    }
  }

  static void updateAttribute(
      Player player,
      String effect,
      Holder<Attribute> attribute,
      double amount,
      AttributeModifier.Operation operation) {
    var instance = player.getAttribute(attribute);
    if (instance == null) {
      return;
    }
    var id = ResourceLocation.fromNamespaceAndPath("aeroscapes_reimagine", effect);
    if (active(player, effect)) {
      if (!instance.hasModifier(id)) {
        instance.addTransientModifier(new AttributeModifier(id, amount, operation));
      }
    } else {
      instance.removeModifier(id);
    }
  }

  private static CompoundTag state(LivingEntity entity) {
    return entity.getPersistentData().getCompound(ROOT);
  }
}
