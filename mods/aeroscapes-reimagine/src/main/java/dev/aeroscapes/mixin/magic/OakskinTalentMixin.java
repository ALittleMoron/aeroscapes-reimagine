package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.aeroscapes.magic.SpellSpecializations;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.spells.nature.OakskinSpell;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = OakskinSpell.class, remap = false)
abstract class OakskinTalentMixin {
  // Keep the caster argument for native spell power, amplifier, and cast sound.
  @WrapOperation(
      method = "onCast",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;removeEffect(Lnet/minecraft/core/Holder;)Z"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$chooseRecipient(
      LivingEntity caster,
      Holder<MobEffect> effect,
      Operation<Boolean> original,
      @Local(argsOnly = true) CastSource source,
      @Share("aeroscapes$recipient") LocalRef<LivingEntity> recipient) {
    LivingEntity target = caster;
    if (caster instanceof ServerPlayer player
        && SpellSpecializations.learned(player, "magic_oakskin_ally"))
      target = SpellSpecializations.oakskinTarget(player);
    recipient.set(target);
    return original.call(target, effect);
  }

  @WrapOperation(
      method = "onCast",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/effect/OakskinData;remove(Lnet/minecraft/world/entity/LivingEntity;)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$resetRecipient(
      LivingEntity caster,
      Operation<Void> original,
      @Share("aeroscapes$recipient") LocalRef<LivingEntity> recipient) {
    original.call(recipient.get());
  }

  @WrapOperation(
      method = "onCast",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$applyRecipient(
      LivingEntity caster,
      MobEffectInstance effect,
      Operation<Boolean> original,
      @Share("aeroscapes$recipient") LocalRef<LivingEntity> recipient) {
    return original.call(recipient.get(), effect);
  }

  @WrapOperation(
      method = "onCast",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;position()Lnet/minecraft/world/phys/Vec3;"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private Vec3 aeroscapes$showRecipient(
      LivingEntity caster,
      Operation<Vec3> original,
      @Share("aeroscapes$recipient") LocalRef<LivingEntity> recipient) {
    return original.call(recipient.get());
  }

  @WrapOperation(
      method = "onCast",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/neoforged/neoforge/network/PacketDistributor;sendToPlayersTrackingEntityAndSelf(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;[Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$sendRecipientParticles(
      Entity caster,
      CustomPacketPayload first,
      CustomPacketPayload[] remaining,
      Operation<Void> original,
      @Share("aeroscapes$recipient") LocalRef<LivingEntity> recipient) {
    original.call(recipient.get(), first, remaining);
  }
}
