package dev.aeroscapes.mixin.reforge.combat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.equipment.tool.CardboardSwordItem;
import dev.aeroscapes.equipment.combat.InstalledWeaponReforges;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CardboardSwordItem.class)
abstract class CardboardSwordReforgeMixin {
  @ModifyVariable(
      method = "cardboardSwordsCannotHurtYou",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getYRot()F"),
      index = 4)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static float aeroscapes$strength(float original, AttackEntityEvent event) {
    return (float) InstalledWeaponReforges.cardboardPush(event.getEntity(), original);
  }

  @WrapOperation(
      method = "cardboardSwordsCannotHurtYou",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/simibubi/create/content/equipment/tool/CardboardSwordItem;knockback(Lnet/minecraft/world/entity/LivingEntity;DF)V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$reforgePush(
      LivingEntity target,
      double strength,
      float yaw,
      Operation<Void> original,
      @Local(argsOnly = true) AttackEntityEvent event) {
    var attacker = event.getEntity();
    var before = target.getDeltaMovement();
    original.call(target, strength, yaw);
    if (!before.equals(target.getDeltaMovement())) {
      InstalledWeaponReforges.cardboardHit(attacker, target);
    }
  }
}
