package dev.aeroscapes.mixin.travel;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.travel.TravelIntegration;
import java.util.concurrent.CompletableFuture;
import net.blay09.mods.waystones.api.WaystoneTeleportContext;
import net.blay09.mods.waystones.api.WaystoneTeleportResult;
import net.blay09.mods.waystones.core.WaystoneTeleportManager;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = WaystoneTeleportManager.class, remap = false)
public abstract class WaystonePaymentFailureMixin {
  @WrapMethod(method = "tryTeleportAsync")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // The native exceptional path does not fire Complete.
  private static CompletableFuture<WaystoneTeleportResult> aeroscapes$refundAsyncFailure(
      WaystoneTeleportContext context,
      Operation<CompletableFuture<WaystoneTeleportResult>> original) {
    try {
      return original
          .call(context)
          .whenComplete(
              (result, failure) -> {
                if (failure != null) TravelIntegration.failed(context);
              });
    } catch (RuntimeException | Error failure) {
      TravelIntegration.failed(context);
      throw failure;
    }
  }

  @WrapMethod(method = "tryTeleport")
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Legacy callers share the same refund guarantee.
  private static WaystoneTeleportResult aeroscapes$refundFailure(
      WaystoneTeleportContext context, Operation<WaystoneTeleportResult> original) {
    try {
      return original.call(context);
    } catch (RuntimeException | Error failure) {
      TravelIntegration.failed(context);
      throw failure;
    }
  }
}
