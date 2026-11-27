package dev.aeroscapes.mixin.technique;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.combat.MartialArchery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class AbilityBowReleaseMixin {
  @WrapOperation(
      method = "handlePlayerAction",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/server/level/ServerPlayer;releaseUsingItem()V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$bowRelease(ServerPlayer player, Operation<Void> original) {
    if (!MartialArchery.ownsBowRelease(player)) original.call(player);
  }

  @WrapOperation(
      method = {"handlePlayerAction", "handleSetCarriedItem"},
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/server/level/ServerPlayer;stopUsingItem()V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$bowInterrupted(ServerPlayer player, Operation<Void> original) {
    original.call(player);
    MartialArchery.interrupted(player);
  }
}
