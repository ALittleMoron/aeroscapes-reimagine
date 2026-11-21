package dev.aeroscapes.mixin.hexquality;

import at.petrak.hexcasting.api.casting.eval.env.PackagedItemCastEnv;
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.hex.HexCastingReports;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PlayerBasedCastEnv.class, remap = false)
abstract class CarrierMishapMixin {
  @WrapOperation(
      method = "sendMishapMsgToPlayer",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/level/ServerPlayer;sendSystemMessage(Lnet/minecraft/network/chat/Component;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$rememberReason(
      ServerPlayer player, Component reason, Operation<Void> original) {
    original.call(player, reason);
    if ((Object) this instanceof PackagedItemCastEnv env)
      HexCastingReports.rememberMishap(
          player.getItemInHand(env.getCastingHand()), reason, player.registryAccess());
  }
}
