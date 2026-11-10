package dev.aeroscapes.mixin.reforge.magic;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.aeroscapes.equipment.magic.MagicSpecialties;
import io.redspace.ironsspellbooks.player.ServerPlayerEvents;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ServerPlayerEvents.class, remap = false)
abstract class SpellInterruptReforgeMixin {
  @WrapOperation(
      method = "onLivingIncomingDamage",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lio/redspace/ironsspellbooks/api/util/Utils;serverSideCancelCast(Lnet/minecraft/server/level/ServerPlayer;)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$lightInterruption(
      ServerPlayer player,
      Operation<Void> original,
      @Local(argsOnly = true) LivingIncomingDamageEvent event) {
    if (!MagicSpecialties.resistInterruption(player, event)) original.call(player);
  }
}
