package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.magic.SpellProgression;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AbstractSpell.class, remap = false)
abstract class ScrollCompletionMixin {
  @WrapOperation(
      method = "castSpell",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;onCast(Lnet/minecraft/world/level/Level;ILnet/minecraft/world/entity/LivingEntity;Lio/redspace/ironsspellbooks/api/spells/CastSource;Lio/redspace/ironsspellbooks/api/magic/MagicData;)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$chargeAfterTravel(
      AbstractSpell spell,
      Level world,
      int rank,
      LivingEntity caster,
      CastSource source,
      MagicData magic,
      Operation<Void> original,
      @Local(argsOnly = true) boolean terminal) {
    ItemStack item = magic.getPlayerCastingItem();
    original.call(spell, world, rank, caster, source, magic);
    if (source != CastSource.SCROLL || !(caster instanceof ServerPlayer player)) return;
    if (terminal) SpellProgression.completedScrollCast(player, spell, item);
    // Dimension-changing effects clear the source before MagicManager's ordinary scroll charge.
    if (magic.getCastSource() != CastSource.SCROLL
        && !player.isCreative()
        && item.getItem() instanceof Scroll) {
      int count = item.getCount();
      item.shrink(1);
      SpellProgression.scrollConsumed(player, item, count);
    }
  }
}
