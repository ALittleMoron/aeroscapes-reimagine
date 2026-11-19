package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import dev.aeroscapes.magic.BindingPact;
import dev.aeroscapes.magic.GuardianAid;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AbstractSpell.class, remap = false)
abstract class PaidSpellTalentMixin {
  @WrapOperation(
      method = "castSpell",
      at =
          @At(
              value = "INVOKE",
              target = "Lio/redspace/ironsspellbooks/api/magic/MagicData;setMana(F)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$measurePayment(
      MagicData magic,
      float remaining,
      Operation<Void> original,
      @Share("aeroscapes$paidMana") LocalFloatRef paid) {
    float before = magic.getMana();
    original.call(magic, remaining);
    paid.set(before - magic.getMana());
  }

  @WrapOperation(
      method = "castSpell",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;onCast(Lnet/minecraft/world/level/Level;ILnet/minecraft/world/entity/LivingEntity;Lio/redspace/ironsspellbooks/api/spells/CastSource;Lio/redspace/ironsspellbooks/api/magic/MagicData;)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$observePaidCast(
      AbstractSpell spell,
      Level world,
      int rank,
      LivingEntity caster,
      CastSource source,
      MagicData magic,
      Operation<Void> original,
      @Share("aeroscapes$paidMana") LocalFloatRef paid) {
    if (caster instanceof ServerPlayer player && !player.isCreative()) {
      if (source == CastSource.SPELLBOOK
          && (spell == SpellRegistry.FORTIFY_SPELL.get()
              || spell == SpellRegistry.BLESSING_OF_LIFE_SPELL.get())
          && !magic.getPlayerRecasts().hasRecastForSpell(spell)) {
        GuardianAid.cast(
            spell,
            player,
            magic,
            paid.get(),
            () -> original.call(spell, world, rank, caster, source, magic));
        return;
      }
      if (spell == SpellRegistry.ROOT_SPELL.get() && paid.get() > 0) {
        var root = BindingPact.paidRoot(spell, player, magic, paid.get());
        original.call(spell, world, rank, caster, source, magic);
        if (root != null) root.bind(player);
        return;
      }
    }
    original.call(spell, world, rank, caster, source, magic);
  }
}
