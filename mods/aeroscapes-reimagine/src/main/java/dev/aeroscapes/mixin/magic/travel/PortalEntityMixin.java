package dev.aeroscapes.mixin.magic.travel;

import dev.aeroscapes.magic.travel.TravelMagic;
import io.redspace.ironsspellbooks.entity.spells.portal.PortalEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PortalEntity.class, remap = false)
abstract class PortalEntityMixin {
  @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$absoluteExpiry(CallbackInfo callback) {
    PortalEntity portal = (PortalEntity) (Object) this;
    if (TravelMagic.portalExpired(portal)) {
      portal.discard();
      callback.cancel();
    }
  }
}
