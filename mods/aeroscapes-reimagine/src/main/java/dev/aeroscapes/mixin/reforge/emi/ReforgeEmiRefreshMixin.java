package dev.aeroscapes.mixin.reforge.emi;

import com.herobrot.tieredneo.network.payload.ReforgeItemSyncPayload;
import dev.emi.emi.runtime.EmiReloadManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.herobrot.tieredneo.network.TieredClientPacketHandler", remap = false)
abstract class ReforgeEmiRefreshMixin {
  @Inject(method = "lambda$handleReforgeItemSync$2", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$refreshRecipes(
      ReforgeItemSyncPayload payload, CallbackInfo callback) {
    if (Minecraft.getInstance().level != null) EmiReloadManager.reload();
  }
}
