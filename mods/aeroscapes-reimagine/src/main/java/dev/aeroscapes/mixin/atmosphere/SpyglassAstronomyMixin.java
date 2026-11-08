package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "com.nettakrim.spyglass_astronomy.SpyglassAstronomyClient", remap = false)
abstract class SpyglassAstronomyMixin {
  @ModifyArg(
      method = "initializeClient",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/nettakrim/spyglass_astronomy/SpyglassAstronomyNeoForge;isModLoaded(Ljava/lang/String;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static String aeroscapes$nativeSpyglassId(String id) {
    // The port still checks the Fabric ID before enabling its drawing fallback.
    return id.equals("spyglass-improvements") ? "spyglass_improvements" : id;
  }

  @WrapOperation(
      method = "requireSpaceLoaded",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static MutableComponent aeroscapes$loadingMessage(
      String message, Operation<MutableComponent> original) {
    return Component.translatable("aeroscapes_reimagine.astronomy.loading");
  }
}
