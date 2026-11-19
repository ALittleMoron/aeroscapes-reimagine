package dev.aeroscapes.mixin.progression;

import dev.aeroscapes.races.RaceIdentity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class InitialRaceInputMixin {
  @Shadow public ServerPlayer player;

  // World and inventory input may arrive before the first chooser has rendered.
  @Inject(
      method = {
        "handleMovePlayer",
        "handlePlayerInput",
        "handleMoveVehicle",
        "handlePaddleBoat",
        "handleUseItem",
        "handleUseItemOn",
        "handleInteract",
        "handlePlayerAction",
        "handlePlayerCommand",
        "handleAnimate",
        "handleSetCarriedItem",
        "handlePickItem",
        "handleContainerClick",
        "handleContainerButtonClick",
        "handlePlaceRecipe",
        "handleSetCreativeModeSlot"
      },
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
              shift = At.Shift.AFTER),
      cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$awaitRaceConfirmation(CallbackInfo callback) {
    if (RaceIdentity.awaitingChoice(player)) callback.cancel();
  }
}
