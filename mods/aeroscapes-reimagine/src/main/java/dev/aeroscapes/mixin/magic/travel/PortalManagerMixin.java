package dev.aeroscapes.mixin.magic.travel;

import dev.aeroscapes.magic.travel.TravelMagic;
import io.redspace.ironsspellbooks.capabilities.magic.PortalManager;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PortalManager.class, remap = false)
abstract class PortalManagerMixin {
  @Inject(
      method = "canUsePortal(Ljava/util/UUID;Lnet/minecraft/world/entity/Entity;)Z",
      at = @At("RETURN"),
      cancellable = true,
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$temporaryLocalPair(
      UUID portalId, Entity traveler, CallbackInfoReturnable<Boolean> callback) {
    if (callback.getReturnValueZ() && !TravelMagic.canUsePortal(portalId, traveler))
      callback.setReturnValue(false);
  }
}
