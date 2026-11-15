package dev.aeroscapes.mixin.navigation;

import dev.aeroscapes.navigation.VerticalMapInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.hud.minimap.info.InfoDisplayManager;
import xaero.hud.minimap.info.InfoDisplays;

@Mixin(value = InfoDisplays.class, remap = false)
public abstract class MinimapLayerInfoMixin {
  @Shadow
  public abstract InfoDisplayManager getManager();

  @Inject(method = "<init>", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by Xaero's pinned constructor Mixin.
  private void aeroscapes$registerLayerInfo(CallbackInfo callback) {
    getManager().add(VerticalMapInfo.create());
  }
}
