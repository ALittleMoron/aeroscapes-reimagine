package dev.aeroscapes.mixin.armor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.armor.ArmorAttributes;
import dev.aeroscapes.equipment.armor.ArmorProficiency;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class ArmorAttributesMixin {
  @Unique
  private static final ResourceLocation AEROSCAPES$ARMOR_PIERCE =
      ResourceLocation.fromNamespaceAndPath("irons_lib", "armor_pierce");

  @Unique private int aeroscapes$undertrainedSlots;

  @WrapOperation(
      method = "collectEquipmentChanges",
      at =
          @At(
              value = "INVOKE",
              ordinal = 1,
              target =
                  "Lnet/minecraft/world/item/ItemStack;forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$applyArmor(
      ItemStack stack,
      EquipmentSlot slot,
      BiConsumer<Holder<Attribute>, AttributeModifier> consumer,
      Operation<Void> original) {
    if ((Object) this instanceof Player player
        && ArmorProficiency.SLOTS.contains(slot)
        && ArmorProficiency.undertrained(player, stack)) {
      ArmorAttributes.penalizedModifiers(stack, slot, consumer);
    } else {
      original.call(stack, slot, consumer);
    }
  }

  @Inject(method = "collectEquipmentChanges", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$updateTraining(
      CallbackInfoReturnable<Map<EquipmentSlot, ItemStack>> cir) {
    if (!((Object) this instanceof Player player)) return;
    var changed = cir.getReturnValue();
    int current = 0;
    for (var slot : ArmorProficiency.SLOTS) {
      var stack = player.getItemBySlot(slot);
      boolean undertrained = ArmorProficiency.undertrained(player, stack);
      int slotBit = 1 << slot.getIndex();
      if (undertrained) current |= slotBit;
      boolean trainingChanged = ((aeroscapes$undertrainedSlots ^ current) & slotBit) != 0;
      if (trainingChanged && (changed == null || !changed.containsKey(slot))) {
        ArmorAttributes.refresh(player, stack, slot, undertrained);
      }
    }
    aeroscapes$undertrainedSlots = current;
  }

  @WrapOperation(
      method = "getDamageAfterArmorAbsorb",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterAbsorb(Lnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/damagesource/DamageSource;FF)F"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$fractionalUndertrainedArmor(
      LivingEntity wearer,
      float damage,
      DamageSource source,
      float armor,
      float toughness,
      Operation<Float> original) {
    if (aeroscapes$undertrainedSlots != 0) {
      // Armor is an integer at this point, including the armor pierce applied by Iron's Lib.
      // Keep the fractional defense and apply the same pierce before clamping at zero.
      double armorPoints = wearer.getAttributeValue(Attributes.ARMOR);
      if (armorPoints > 0.0 && source.getEntity() instanceof LivingEntity attacker) {
        var attribute = BuiltInRegistries.ATTRIBUTE.getHolder(AEROSCAPES$ARMOR_PIERCE).orElse(null);
        if (attribute != null) {
          var pierce = attacker.getAttribute(attribute);
          if (pierce != null) armorPoints -= Math.max(0.0, pierce.getValue());
        }
      }
      armor = (float) Math.max(0.0, armorPoints);
    }
    return original.call(wearer, damage, source, armor, toughness);
  }
}
