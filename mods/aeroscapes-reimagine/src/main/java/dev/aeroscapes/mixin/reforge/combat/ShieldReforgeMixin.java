package dev.aeroscapes.mixin.reforge.combat;

import dev.aeroscapes.equipment.combat.ShieldReforges;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Player.class)
abstract class ShieldReforgeMixin {
  @ModifyArg(
      method = "hurtCurrentlyUsedShield",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V"),
      index = 0)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private int aeroscapes$shieldWear(int damage) {
    return ShieldReforges.wear((Player) (Object) this, damage);
  }
}
