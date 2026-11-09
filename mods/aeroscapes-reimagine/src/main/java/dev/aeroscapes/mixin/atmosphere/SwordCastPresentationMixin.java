package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.SwordCastPresentation;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ICastData;
import io.redspace.ironsspellbooks.player.ClientSpellCastHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientSpellCastHelper.class, remap = false)
abstract class SwordCastPresentationMixin {
  @Inject(method = "handleClientboundOnClientCast", at = @At("RETURN"), require = 1)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The confirmed packet contains the actual cast source.
  private static void aeroscapes$swordFollowThrough(
      String spell, int level, CastSource source, ICastData data, CallbackInfo info) {
    SwordCastPresentation.confirmed(spell, source);
  }
}
