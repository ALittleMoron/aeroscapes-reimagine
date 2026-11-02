package dev.aeroscapes.mixin.travel;

import net.blay09.mods.waystones.handler.EpitaphDeathHandler;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EpitaphDeathHandler.class, remap = false)
public abstract class WaystoneEpitaphMixin {
  @Inject(method = "trySpawnMemorial", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Memorial destinations are disabled, including for existing
  // items.
  private static void aeroscapes$preserveDisabledEpitaph(
      ServerPlayer player, CallbackInfo callback) {
    callback.cancel();
  }
}
