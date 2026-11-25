package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.ShortBladeAttack;
import dev.aeroscapes.atmosphere.ShortBladeAttackPresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import punchy.client.animation.PunchyAnimationManager;
import punchy.client.animation.data.AnimationClip;

@Pseudo
@Mixin(targets = "punchy.client.state.AttackStateMachine", remap = false)
abstract class PunchyBetterCombatBladeClipMixin {
  @Inject(method = "resolveBetterCombatClip", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Better Combat supplies the selected move.
  private void aeroscapes$useImpactClip(
      ItemStack stack,
      InteractionHand hand,
      Minecraft client,
      CallbackInfoReturnable<AnimationClip> result) {
    if (!stack.is(ShortBladeAttack.BLADES)) return;
    String clip = ShortBladeAttackPresentation.takeBetterCombatClip(client.player, hand);
    result.setReturnValue(
        clip == null ? null : PunchyAnimationManager.resolveNamedClip(clip, client, hand));
  }
}
