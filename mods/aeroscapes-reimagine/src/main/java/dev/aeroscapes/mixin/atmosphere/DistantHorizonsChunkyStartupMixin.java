package dev.aeroscapes.mixin.atmosphere;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets =
        "com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractChunkyAccessor",
    remap = false)
public abstract class DistantHorizonsChunkyStartupMixin {
  @Inject(method = "tryRunFirstTimeSetup", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Called by the exact provider startup boundary.
  private void aeroscapes$waitForChunky(CallbackInfo callback) {
    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
    // Spawn loads precede Chunky's ServerStartingEvent; keep DH's bound flag unset.
    if (server == null || !server.isReady()) callback.cancel();
  }
}
