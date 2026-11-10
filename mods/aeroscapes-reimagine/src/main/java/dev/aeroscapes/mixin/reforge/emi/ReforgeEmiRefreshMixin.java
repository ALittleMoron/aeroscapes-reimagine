package dev.aeroscapes.mixin.reforge.emi;

import com.herobrot.tieredneo.network.payload.AttributeSyncPayload;
import com.herobrot.tieredneo.network.payload.ReforgeItemSyncPayload;
import dev.aeroscapes.equipment.emi.ReforgeEmiReload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.herobrot.tieredneo.network.TieredClientPacketHandler", remap = false)
abstract class ReforgeEmiRefreshMixin {
  @Inject(method = "lambda$handleReforgeItemSync$2", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$materialsUpdated(
      ReforgeItemSyncPayload payload, CallbackInfo callback) {
    ReforgeEmiReload.invalidate();
  }

  @Inject(method = "lambda$handleAttributeSync$3", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$attributesUpdated(
      AttributeSyncPayload payload, CallbackInfo callback) {
    ReforgeEmiReload.invalidate();
  }
}
