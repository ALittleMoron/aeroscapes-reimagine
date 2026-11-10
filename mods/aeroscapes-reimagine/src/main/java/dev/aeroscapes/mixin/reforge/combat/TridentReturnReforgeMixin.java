package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.combat.RangedReforges;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
abstract class TridentReturnReforgeMixin {
  @WrapOperation(
      method = "playerTouch",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/projectile/AbstractArrow;tryPickup(Lnet/minecraft/world/entity/player/Player;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$returnThrust(
      AbstractArrow arrow, Player catcher, Operation<Boolean> original) {
    if (!(arrow instanceof ThrownTrident trident)
        || !(catcher instanceof ServerPlayer player)
        || !RangedReforges.returningTrident(trident, player)) {
      return original.call(arrow, catcher);
    }

    // A single non-stackable trident goes into the first free inventory slot.
    var inventory = player.getInventory();
    int slot = inventory.getFreeSlot();
    boolean caught = original.call(arrow, catcher);
    if (caught && slot >= 0) {
      RangedReforges.armReturningTrident(player, inventory.getItem(slot));
    }
    return caught;
  }
}
