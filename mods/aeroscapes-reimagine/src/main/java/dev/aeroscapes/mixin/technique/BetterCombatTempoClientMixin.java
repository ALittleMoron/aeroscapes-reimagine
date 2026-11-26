package dev.aeroscapes.mixin.technique;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.combat.SwingTempo;
import dev.aeroscapes.magic.client.SwingTempoClient;
import java.util.List;
import net.bettercombat.BetterCombatMod;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.MinecraftClient_BetterCombat;
import net.bettercombat.logic.PlayerAttackHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Better Combat adds the target methods at priority 1000. Invoke hooks must rank above it.
@Mixin(value = Minecraft.class, priority = 1001, remap = false)
abstract class BetterCombatTempoClientMixin {
  @Unique private @Nullable SwingTempoClient.Available aeroscapes$starting;

  @Dynamic("Added by Better Combat's MinecraftClientInject")
  @ModifyExpressionValue(
      method = "startUpswing",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$chargeForUpswing(float strength, @Local AttackHand hand) {
    aeroscapes$starting = SwingTempoClient.available((Minecraft) (Object) this, hand);
    return aeroscapes$starting == null ? strength : SwingTempo.fasterCharge(strength);
  }

  @Dynamic("Added by Better Combat's MinecraftClientInject")
  @ModifyExpressionValue(
      method = "startUpswing",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/bettercombat/logic/PlayerAttackHelper;getAttackCooldownTicksCapped(Lnet/minecraft/world/entity/player/Player;)F"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$windup(float ticks) {
    return aeroscapes$starting == null
        ? ticks
        : Math.max(BetterCombatMod.config.attack_interval_cap, SwingTempo.fasterRecovery(ticks));
  }

  @Dynamic("Added by Better Combat's MinecraftClientInject")
  @Inject(
      method = "startUpswing",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/bettercombat/client/animation/PlayerAttackAnimatable;playAttackAnimation(Ljava/lang/String;Lnet/bettercombat/logic/AnimatedHand;FF)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$acceptUpswing(CallbackInfo callback, @Local AttackHand hand) {
    if (aeroscapes$starting != null) {
      SwingTempoClient.commit((Minecraft) (Object) this, aeroscapes$starting, hand.itemStack());
    }
  }

  @Dynamic("Added by Better Combat's MinecraftClientInject")
  @Inject(method = "startUpswing", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$clearStart(CallbackInfo callback) {
    aeroscapes$starting = null;
  }

  @Dynamic("Added by Better Combat's MinecraftClientInject")
  @ModifyExpressionValue(
      method = "performAttack",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$chargeAtImpact(float strength) {
    return SwingTempoClient.inSwing((Minecraft) (Object) this)
        ? SwingTempo.fasterCharge(strength)
        : strength;
  }

  @Dynamic("Added by Better Combat's MinecraftClientInject")
  @ModifyExpressionValue(
      method = "performAttack",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/bettercombat/client/collision/TargetFinder;findAttackTargets(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/bettercombat/api/WeaponAttributes$Attack;D)Ljava/util/List;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private List<Entity> aeroscapes$validateImpact(List<Entity> targets) {
    Minecraft client = (Minecraft) (Object) this;
    if (!SwingTempoClient.inSwing(client) || client.player == null) return targets;
    var hand =
        PlayerAttackHelper.getCurrentAttack(
            client.player, ((MinecraftClient_BetterCombat) this).getComboCount());
    return SwingTempoClient.keepTargets(
        client, hand == null ? ItemStack.EMPTY : hand.itemStack(), targets);
  }

  @Dynamic("Added by Better Combat's MinecraftClientInject")
  @Inject(method = "performAttack", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$afterAttack(CallbackInfo callback) {
    SwingTempoClient.finish((Minecraft) (Object) this);
  }

  @Dynamic("Added by Better Combat's MinecraftClientInject")
  @Inject(method = "cancelWeaponSwing", at = @At("HEAD"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$cancelAttack(CallbackInfo callback) {
    SwingTempoClient.finish((Minecraft) (Object) this);
  }
}
