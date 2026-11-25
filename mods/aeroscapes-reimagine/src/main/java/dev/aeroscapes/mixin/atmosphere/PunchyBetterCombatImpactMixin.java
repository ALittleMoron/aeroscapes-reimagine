package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.atmosphere.ShortBladeAttackPresentation;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "punchy.compat.BetterCombatCompat", remap = false)
abstract class PunchyBetterCombatImpactMixin {
  @WrapOperation(
      method = "handleAttackHit",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lpunchy/client/state/AttackActionTracker;recordAttack(Lnet/minecraft/world/InteractionHand;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Punchy's native callback owns the attack trigger.
  private static void aeroscapes$rememberImpact(
      InteractionHand hand, Operation<Void> original, Object player, Object attackHand) {
    ShortBladeAttackPresentation.recordBetterCombatImpact(player, attackHand, hand);
    original.call(hand);
  }
}
