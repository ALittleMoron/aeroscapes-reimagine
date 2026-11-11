package dev.aeroscapes.mixin.reforge.engineering;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.field.GasMaskRecovery;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "rbasamoyai.createbigcannons.munitions.GasCloudEntity", remap = false)
abstract class GasMaskExposureMixin {
  @ModifyExpressionValue(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lrbasamoyai/createbigcannons/munitions/GasCloudEntity;isBlockedBy(Lnet/minecraft/world/entity/LivingEntity;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$observeProtection(boolean blocked, @Local LivingEntity target) {
    if (blocked && target instanceof ServerPlayer player) GasMaskRecovery.observe(player);
    return blocked;
  }
}
