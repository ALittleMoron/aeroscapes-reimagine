package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.magic.SpellKnowledge;
import dev.aeroscapes.magic.travel.TravelMagic;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractSpell.class, remap = false)
abstract class SpellKnowledgeMixin {
  @Inject(method = "getLevelFor", at = @At("RETURN"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$boundedEquipmentBonus(
      int baseLevel, LivingEntity caster, CallbackInfoReturnable<Integer> callback) {
    if (!SpellKnowledge.scrollLevel() && caster instanceof ServerPlayer player) {
      int cap = SpellKnowledge.cap((AbstractSpell) (Object) this, SpellKnowledge.unlocked(player));
      if (baseLevel > 0 && baseLevel <= cap)
        callback.setReturnValue(Math.min(callback.getReturnValue(), cap));
    }
  }

  @WrapOperation(
      method = "canBeCastedBy",
      at =
          @At(
              value = "INVOKE",
              target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;requiresLearning()Z"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$scrollDiscovery(
      AbstractSpell spell,
      Operation<Boolean> original,
      int level,
      CastSource source,
      MagicData magic,
      Player player) {
    return source != CastSource.SCROLL && original.call(spell);
  }

  @Inject(method = "attemptInitiateCast", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$knowledge(
      ItemStack stack,
      int level,
      Level world,
      Player caster,
      CastSource source,
      boolean cooldown,
      String equipmentSlot,
      CallbackInfoReturnable<Boolean> callback) {
    if (caster instanceof ServerPlayer player
        && !SpellKnowledge.canStart(
            (AbstractSpell) (Object) this, player, stack, level, source, true))
      callback.setReturnValue(false);
  }

  @Inject(method = "castSpell", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$completion(
      Level world,
      int level,
      ServerPlayer player,
      CastSource source,
      boolean cooldown,
      CallbackInfo callback) {
    AbstractSpell spell = (AbstractSpell) (Object) this;
    MagicData magic = MagicData.getPlayerMagicData(player);
    if (!SpellKnowledge.executionAllowed(spell, player, level, source)
        || !TravelMagic.canCast(spell, player, magic, true)) {
      // Native channels pulse at remaining-duration multiples of ten. An earlier pulse
      // already used this scroll, even if its held context was removed before completion.
      if (magic.getCastSource() == CastSource.SCROLL
          && magic.getCastType() == CastType.CONTINUOUS
          && magic.getCastDurationRemaining() < magic.getCastDuration() / 10 * 10)
        Scroll.attemptRemoveScrollAfterCast(player);
      // MagicManager checks this state after returning before charging a one-use scroll.
      magic.resetCastingState();
      callback.cancel();
    }
  }
}
