package dev.aeroscapes.mixin.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.magic.MagicBonuses;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.util.TooltipsUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = TooltipsUtils.class, remap = false)
abstract class SpellTooltipCostsMixin {
  @WrapOperation(
      method = "formatActiveSpellTooltip",
      at =
          @At(
              value = "INVOKE",
              target = "Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;getManaCost(I)I"),
      require = 2)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static int aeroscapes$mana(
      AbstractSpell spell,
      int rank,
      Operation<Integer> original,
      ItemStack item,
      SpellData data,
      CastSource source,
      LocalPlayer player) {
    return MagicBonuses.manaCost(spell, rank, player, source, original.call(spell, rank));
  }

  @WrapOperation(
      method = "formatActiveSpellTooltip",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/capabilities/magic/MagicManager;getEffectiveSpellCooldown(Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;Lnet/minecraft/world/entity/player/Player;Lio/redspace/ironsspellbooks/api/spells/CastSource;)I"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static int aeroscapes$cooldown(
      AbstractSpell spell, Player player, CastSource source, Operation<Integer> original) {
    int raw = original.call(spell, player, source);
    return source == CastSource.SCROLL ? raw : MagicBonuses.cooldown(spell, player, raw);
  }
}
