package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.equipment.combat.WeaponProficiency;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
abstract class MeleeRecoveryMixin {
  @ModifyReturnValue(method = "getCurrentItemAttackStrengthDelay", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private float aeroscapes$weaponRecovery(float ticks) {
    return WeaponProficiency.recoveryTicks((Player) (Object) this, ticks);
  }
}
