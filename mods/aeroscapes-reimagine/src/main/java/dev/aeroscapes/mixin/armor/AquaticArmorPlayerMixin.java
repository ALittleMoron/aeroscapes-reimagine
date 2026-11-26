package dev.aeroscapes.mixin.armor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.armor.ArmorProficiency;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Method merging precedes injection preparation. INVOKE requires a higher priority than the merge.
@Mixin(value = Player.class, priority = 1100)
abstract class AquaticArmorPlayerMixin {
  @WrapOperation(
      method = "updateDivingHelmet",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Player;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
      require = 6,
      allow = 6)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$divingEffect(
      Player player, MobEffectInstance effect, Operation<Boolean> original) {
    return !ArmorProficiency.undertrained(player, player.getItemBySlot(EquipmentSlot.HEAD))
        && original.call(player, effect);
  }

  @WrapOperation(
      method = "updateTurtleChestplate",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Player;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
      require = 2,
      allow = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$turtleResistance(
      Player player, MobEffectInstance effect, Operation<Boolean> original) {
    return (!effect.is(MobEffects.DAMAGE_RESISTANCE)
            || !ArmorProficiency.undertrained(player, player.getItemBySlot(EquipmentSlot.CHEST)))
        && original.call(player, effect);
  }
}
