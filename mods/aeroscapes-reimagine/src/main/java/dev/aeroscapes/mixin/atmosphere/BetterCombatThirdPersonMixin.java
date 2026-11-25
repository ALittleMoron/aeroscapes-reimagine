package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.atmosphere.BetterCombatThirdPerson;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier.PartModifier;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = AbstractClientPlayer.class, priority = 1100, remap = false)
abstract class BetterCombatThirdPersonMixin {
  @Dynamic("Added by Better Combat's AbstractClientPlayerEntityMixin")
  @ModifyArg(
      method = "createAttackAdjustment",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/kosmx/playerAnim/api/layered/modifier/AdjustmentModifier;<init>(Ljava/util/function/Function;)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // PlayerAnimator applies the body track to the whole renderer.
  private Function<String, Optional<PartModifier>> aeroscapes$keepFeetUnderPlayer(
      Function<String, Optional<PartModifier>> adjustment) {
    return part ->
        BetterCombatThirdPerson.keepsViewAdjustment(part, FirstPersonMode.isFirstPersonPass())
            ? adjustment.apply(part)
            : Optional.empty();
  }

  @Dynamic("Added by Better Combat's AbstractClientPlayerEntityMixin")
  @WrapOperation(
      method = "playAttackAnimation",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ldev/kosmx/playerAnim/api/layered/ModifierLayer;replaceAnimationWithFade(Ldev/kosmx/playerAnim/api/layered/modifier/AbstractFadeModifier;Ldev/kosmx/playerAnim/api/layered/IAnimation;)V"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Capture the attack layer after Better Combat selects the clip
  // and hand.
  private void aeroscapes$trackAttack(
      ModifierLayer<?> layer,
      AbstractFadeModifier fade,
      IAnimation animation,
      Operation<Void> original,
      String clip) {
    BetterCombatThirdPerson.track((AbstractClientPlayer) (Object) this, layer, clip);
    original.call(layer, fade, animation);
  }
}
