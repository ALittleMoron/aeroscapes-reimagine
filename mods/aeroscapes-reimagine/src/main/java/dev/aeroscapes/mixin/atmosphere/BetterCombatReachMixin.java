package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.equipment.combat.ReforgedReach;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.bettercombat.logic.PlayerAttackHelper", remap = false)
abstract class BetterCombatReachMixin {
  @Shadow
  private static double getRangeWithItem(ItemStack stack, double reach) {
    throw new AssertionError();
  }

  @Inject(method = "getRangeWithWeapon", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The effective player reach already includes the main-hand roll.
  private static void aeroscapes$mainHandReach(
      Player player, double reach, CallbackInfoReturnable<Double> result) {
    aeroscapes$applyReforgedReach(player, player.getMainHandItem(), reach, result);
  }

  @Inject(method = "getRangeForItem", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // A dual-wield swing can select the other hand's weapon.
  private static void aeroscapes$attackingHandReach(
      Player player, ItemStack stack, CallbackInfoReturnable<Double> result) {
    aeroscapes$applyReforgedReach(
        player, stack, player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE), result);
  }

  @ModifyReturnValue(method = "getStaticRange", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Better Combat uses this value on the weapon's tooltip.
  private static double aeroscapes$displayReach(double reach, Player player, ItemStack stack) {
    return reach + ReforgedReach.bonus(stack);
  }

  @Unique
  private static void aeroscapes$applyReforgedReach(
      Player player, ItemStack weapon, double reach, CallbackInfoReturnable<Double> result) {
    double equippedBonus = ReforgedReach.equippedBonus(player);
    double weaponBonus = ReforgedReach.bonus(weapon);
    if (equippedBonus == 0 && weaponBonus == 0) return;
    var basis = weaponBonus == 0 ? weapon : ReforgedReach.withoutBonus(weapon);
    result.setReturnValue(getRangeWithItem(basis, reach - equippedBonus) + weaponBonus);
  }
}
