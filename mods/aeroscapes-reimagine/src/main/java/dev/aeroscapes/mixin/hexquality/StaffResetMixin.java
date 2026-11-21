package dev.aeroscapes.mixin.hexquality;

import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.forge.xplat.ForgeXplatImpl;
import dev.aeroscapes.equipment.hex.HexCastingReports;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgeXplatImpl.class, remap = false)
abstract class StaffResetMixin {
  @Inject(method = "clearCastingData", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$reset(ServerPlayer player, CallbackInfo ci) {
    HexCastingReports.reset(player);
  }

  @Inject(method = "setStaffcastImage", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$endSequence(ServerPlayer player, CastingImage image, CallbackInfo ci) {
    if (image == null) HexCastingReports.reset(player);
  }
}
