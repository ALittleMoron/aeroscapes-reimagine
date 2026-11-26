package dev.aeroscapes.equipment.armor;

import dev.aeroscapes.equipment.armor.SpecialistArmor.Set;
import dev.aeroscapes.progression.PracticeActions;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = "aeroscapes_reimagine")
public final class ArmorEffects {
  private static final TagKey<Item> LIGHT_WEAPONS =
      TagKey.create(Registries.ITEM, ArmorProficiency.id("light_weapons"));
  private static final int DUSKSTRIDE_DURATION = 60;
  private static final int DUSKSTRIDE_COOLDOWN = 120;
  private static final Map<ServerPlayer, DuskstrideBurst> DUSKSTRIDE_BURSTS = new WeakHashMap<>();

  private record DuskstrideBurst(long activeUntil, long readyAt) {}

  private ArmorEffects() {}

  private static boolean wearing(Player player, EquipmentSlot slot, Set set) {
    return ArmorProficiency.activeItem(player, slot).getItem()
            instanceof SpecialistArmor.Piece piece
        && piece.set() == set;
  }

  private static boolean wearingSet(Player player, Set set) {
    for (var slot : ArmorProficiency.SLOTS) {
      if (!wearing(player, slot, set)) return false;
    }
    return true;
  }

  @SubscribeEvent
  public static void armorDamage(LivingDamageEvent.Pre event) {
    var source = event.getSource();
    if (event.getNewDamage() <= 0.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

    float factor = 1.0F;
    if (event.getEntity() instanceof ServerPlayer player) {
      if (source.is(DamageTypeTags.IS_PROJECTILE)
          && wearing(player, EquipmentSlot.CHEST, Set.WINDWARD)) {
        factor *= 0.82F;
      }
      if (melee(source) && wearing(player, EquipmentSlot.CHEST, Set.DUSKSTRIDE)) factor *= 0.88F;
      if (source.is(DamageTypeTags.IS_FALL)
          && wearing(player, EquipmentSlot.FEET, Set.DUSKSTRIDE)) {
        factor *= 0.65F;
      }
    }
    if (source.getEntity() instanceof ServerPlayer shooter
        && source.getDirectEntity() instanceof AbstractArrow
        && !(source.getDirectEntity() instanceof ThrownTrident)
        && wearing(shooter, EquipmentSlot.HEAD, Set.WINDWARD)) {
      factor *= 1.08F;
      if (shooter.distanceToSqr(event.getEntity()) >= 8 * 8 && wearingSet(shooter, Set.WINDWARD)) {
        factor *= 1.12F;
      }
    }
    if (factor != 1.0F) event.setNewDamage(event.getNewDamage() * factor);
  }

  private static boolean melee(DamageSource source) {
    return source.getDirectEntity() instanceof LivingEntity
        && source.getDirectEntity() == source.getEntity()
        && (source.is(DamageTypes.PLAYER_ATTACK)
            || source.is(DamageTypes.MOB_ATTACK)
            || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO));
  }

  @SubscribeEvent
  public static void meleeHit(LivingDamageEvent.Post event) {
    if (event.getNewDamage() <= 0.0F
        || !event.getSource().is(DamageTypes.PLAYER_ATTACK)
        || !(event.getSource().getDirectEntity() instanceof ServerPlayer attacker)
        || attacker == event.getEntity()
        || !PracticeActions.personal(attacker)
        || attacker.isAutoSpinAttack()
        || !attacker.getWeaponItem().is(LIGHT_WEAPONS)
        || attacker.getAttackStrengthScale(0.5F) <= 0.9F) return;

    long now = attacker.level().getGameTime();
    var burst = DUSKSTRIDE_BURSTS.get(attacker);
    if ((burst != null && now < burst.readyAt()) || !wearingSet(attacker, Set.DUSKSTRIDE)) return;

    DUSKSTRIDE_BURSTS.put(
        attacker, new DuskstrideBurst(now + DUSKSTRIDE_DURATION, now + DUSKSTRIDE_COOLDOWN));
    attacker
        .serverLevel()
        .playSound(
            null,
            attacker.getX(),
            attacker.getY(),
            attacker.getZ(),
            SoundEvents.ARMOR_EQUIP_LEATHER.value(),
            SoundSource.PLAYERS,
            0.35F,
            1.55F);
  }

  @SubscribeEvent
  public static void movement(PlayerTickEvent.Post event) {
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    int percent = ArmorProficiency.movementPercent(player);
    if (wearing(player, EquipmentSlot.LEGS, Set.WINDWARD)
        && player.isUsingItem()
        && player.getUseItem().getItem() instanceof ProjectileWeaponItem) percent += 40;
    if (wearing(player, EquipmentSlot.LEGS, Set.DUSKSTRIDE) && player.isSprinting()) percent += 12;
    percent += duskstrideBurst(player);

    var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
    if (speed == null) return;
    var id = ArmorProficiency.id("armor_movement");
    double amount = percent / 100.0;
    var old = speed.getModifier(id);
    if (old != null && old.amount() == amount) return;
    if (old != null) speed.removeModifier(id);
    if (percent != 0) {
      speed.addTransientModifier(
          new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
  }

  private static int duskstrideBurst(ServerPlayer player) {
    var burst = DUSKSTRIDE_BURSTS.get(player);
    if (burst == null) return 0;

    long now = player.level().getGameTime();
    if (now >= burst.readyAt()) DUSKSTRIDE_BURSTS.remove(player);
    if (now >= burst.activeUntil()) return 0;
    if (wearingSet(player, Set.DUSKSTRIDE)) return 18;

    DUSKSTRIDE_BURSTS.put(player, new DuskstrideBurst(now, burst.readyAt()));
    return 0;
  }
}
