package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import dev.aeroscapes.equipment.combat.InstalledWeaponReforges;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(PotatoCannonItem.class)
abstract class PotatoCannonReforgeMixin {
  @ModifyConstant(method = "use", constant = @Constant(doubleValue = 0.1))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private double aeroscapes$spread(
      double original,
      @Local(ordinal = 0) ItemStack weapon,
      @Local(argsOnly = true) Player player) {
    return InstalledWeaponReforges.cannonSpread(weapon, player, original);
  }

  @ModifyExpressionValue(
      method = "use",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/simibubi/create/api/equipment/potatoCannon/PotatoCannonProjectileType;reloadTicks()I"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$reload(int original, @Local(ordinal = 0) ItemStack weapon) {
    return InstalledWeaponReforges.cannonReload(weapon, original);
  }
}
