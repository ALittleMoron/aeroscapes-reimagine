package dev.aeroscapes.mixin.atmosphere;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.aeroscapes.atmosphere.DistantSceneryPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.AbstractModInitializer_neoforge", remap = false)
public abstract class DistantHorizonsCompatibilityChatMixin {
  @WrapWithCondition(
      method = "logIncompatibilityWarnings()V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/seibel/distanthorizons/core/api/internal/ClientApi;queueSlowChatMessage(Ljava/lang/String;)V"))
  @SuppressWarnings({"PMD.UnusedPrivateMethod", "PMD.UnusedFormalParameter"})
  // The provider invocation supplies the receiver even though only its message is filtered.
  private static boolean aeroscapes$showCompatibilityChat(
      @Coerce Object clientApi, String message) {
    return DistantSceneryPolicy.showCompatibilityChat(message);
  }
}
