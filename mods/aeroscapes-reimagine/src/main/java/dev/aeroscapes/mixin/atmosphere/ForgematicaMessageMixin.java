package dev.aeroscapes.mixin.atmosphere;

import dev.aeroscapes.atmosphere.ForgematicaMessages;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "fi.dy.masa.malilib.util.InfoUtils", remap = false)
public abstract class ForgematicaMessageMixin {
  @Inject(
      method =
          "showGuiOrInGameMessage(Lfi/dy/masa/malilib/gui/Message$MessageType;ILjava/lang/String;[Ljava/lang/Object;)V",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes the pinned native message route.
  private static void aeroscapes$translateGuiOrGame(
      MessageType type, int lifeTime, String message, Object[] arguments, CallbackInfo callback) {
    var translation = ForgematicaMessages.resolve(message, arguments);
    if (translation == null) return;
    InfoUtils.showGuiOrInGameMessage(type, lifeTime, translation.key(), translation.arguments());
    callback.cancel();
  }

  @Inject(
      method =
          "showGuiAndInGameMessage(Lfi/dy/masa/malilib/gui/Message$MessageType;ILjava/lang/String;[Ljava/lang/Object;)V",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes the pinned native message route.
  private static void aeroscapes$translateGuiAndGame(
      MessageType type, int lifeTime, String message, Object[] arguments, CallbackInfo callback) {
    var translation = ForgematicaMessages.resolve(message, arguments);
    if (translation == null) return;
    InfoUtils.showGuiAndInGameMessage(type, lifeTime, translation.key(), translation.arguments());
    callback.cancel();
  }

  @Inject(
      method =
          "showGuiOrActionBarMessage(Lfi/dy/masa/malilib/gui/Message$MessageType;ILjava/lang/String;[Ljava/lang/Object;)V",
      at = @At("HEAD"),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Mixin invokes the pinned native message route.
  private static void aeroscapes$translateGuiOrActionBar(
      MessageType type, int lifeTime, String message, Object[] arguments, CallbackInfo callback) {
    var translation = ForgematicaMessages.resolve(message, arguments);
    if (translation == null) return;
    InfoUtils.showGuiOrActionBarMessage(type, lifeTime, translation.key(), translation.arguments());
    callback.cancel();
  }
}
