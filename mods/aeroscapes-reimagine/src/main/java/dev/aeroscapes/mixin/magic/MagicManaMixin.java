package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.magic.MagicBonuses;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = AbstractSpell.class, remap = false)
abstract class MagicManaMixin {
  @WrapOperation(
      method = "canBeCastedBy",
      at =
          @At(
              value = "INVOKE",
              target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$discountedAffordability(
      AbstractSpell spell,
      int requestedLevel,
      Operation<Integer> original,
      int level,
      CastSource source,
      MagicData magic,
      Player player) {
    return MagicBonuses.manaCost(
        spell, requestedLevel, player, source, original.call(spell, requestedLevel));
  }
}
