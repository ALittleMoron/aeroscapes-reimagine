package dev.aeroscapes.mixin.magic.travel;

import dev.aeroscapes.magic.travel.TravelMagic;
import dev.aeroscapes.magic.travel.TravelMagicPolicy;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.entity.spells.portal.PortalData;
import io.redspace.ironsspellbooks.spells.ender.PortalSpell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PortalSpell.class, remap = false)
abstract class PortalSpellMixin {
  @Inject(method = "getPortalDuration", at = @At("HEAD"), cancellable = true, require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$localDuration(
      int level, LivingEntity caster, CallbackInfoReturnable<Integer> callback) {
    PortalSpell spell = (PortalSpell) (Object) this;
    callback.setReturnValue(
        TravelMagicPolicy.portalDuration(level, spell.getEntityPowerMultiplier(caster)));
  }

  @Inject(method = "handleEntityPortal", at = @At("RETURN"), require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$recordPlacement(
      RecastInstance recast,
      Level level,
      int spellLevel,
      LivingEntity caster,
      CastSource source,
      MagicData magicData,
      Player owner,
      ServerLevel serverLevel,
      BlockHitResult hit,
      CallbackInfo callback) {
    if (!(owner instanceof ServerPlayer player)) return;
    PortalSpell spell = (PortalSpell) (Object) this;
    RecastInstance current = magicData.getPlayerRecasts().getRecastInstance(spell.getSpellId());
    if (current != null && current.getCastData() instanceof PortalData data)
      TravelMagic.portalPlaced(player, data, spell.getRecastDuration(spellLevel, player) + 10);
  }
}
