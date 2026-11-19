package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.magic.MagicBonuses;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MagicManager.class, remap = false)
abstract class MagicChannelManaMixin {
  @WrapOperation(
      method = "lambda$tick$0",
      at =
          @At(
              value = "INVOKE",
              target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$discountedChannelThreshold(
      AbstractSpell spell,
      int requestedLevel,
      Operation<Integer> original,
      boolean regenerationTick,
      Player player) {
    return MagicBonuses.manaCost(
        spell,
        requestedLevel,
        player,
        MagicData.getPlayerMagicData(player).getCastSource(),
        original.call(spell, requestedLevel));
  }
}
