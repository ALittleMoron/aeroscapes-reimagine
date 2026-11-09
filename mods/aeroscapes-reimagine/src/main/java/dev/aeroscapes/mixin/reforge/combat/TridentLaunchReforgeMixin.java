package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.combat.RangedReforges;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TridentItem.class)
abstract class TridentLaunchReforgeMixin {
  @WrapOperation(
      method = "releaseUsing",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$reforgedThrow(
      Level level, Entity entity, Operation<Boolean> original) {
    RangedReforges.launchTrident((ThrownTrident) entity);
    return original.call(level, entity);
  }
}
