package dev.aeroscapes.mixin.atmosphere;

import io.redspace.ironsspellbooks.player.ClientMagicData;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 900, remap = false)
abstract class BetterCombatCastingMixin {
  @Dynamic("Added by Better Combat's MinecraftClientInject")
  @Invoker("cancelWeaponSwing")
  abstract void aeroscapes$cancelWeaponSwing();

  @Dynamic("Added by Better Combat's MinecraftClientInject")
  @Inject(method = "startUpswing", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // An active cast prevents another wind-up.
  private void aeroscapes$keepCasting(CallbackInfo callback) {
    if (ClientMagicData.isCasting()) callback.cancel();
  }

  @Dynamic("Added by Better Combat's MinecraftClientInject")
  @Inject(method = "performAttack", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // A queued hit must not survive a cast.
  private void aeroscapes$dropPendingAttack(CallbackInfo callback) {
    if (!ClientMagicData.isCasting()) return;
    aeroscapes$cancelWeaponSwing();
    callback.cancel();
  }
}
