package dev.aeroscapes.mixin.technique;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.combat.SwingTempo;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.network.Packets.C2S_AttackRequest;
import net.bettercombat.network.ServerNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerNetwork.class, remap = false)
abstract class BetterCombatTempoServerMixin {
  @WrapMethod(method = "handleAttackRequest")
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$timedAttack(
      C2S_AttackRequest request,
      MinecraftServer server,
      ServerPlayer player,
      ServerGamePacketListenerImpl handler,
      Operation<Void> original) {
    var hand = PlayerAttackHelper.getCurrentAttack(player, request.comboCount());
    SwingTempo.attack(
        player,
        hand == null ? ItemStack.EMPTY : hand.itemStack(),
        hand != null && hand.isOffHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
        request.selectedSlot(),
        request.cursorTarget(),
        request.entityIds(),
        targets -> {
          C2S_AttackRequest validated = request;
          if (targets != request.entityIds()) {
            validated =
                new C2S_AttackRequest(
                    request.comboCount(),
                    request.isSneaking(),
                    request.selectedSlot(),
                    request.cursorTarget(),
                    targets);
          }
          original.call(validated, server, player, handler);
        });
  }

  @WrapOperation(
      method = "handleAttackRequest",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/server/MinecraftServer;executeIfPossible(Ljava/lang/Runnable;)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$runTimedAttack(
      MinecraftServer server, Runnable attack, Operation<Void> original) {
    original.call(server, SwingTempo.carryAttack(attack));
  }
}
