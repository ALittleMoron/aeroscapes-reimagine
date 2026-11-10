package dev.aeroscapes.mixin.reforge;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.equipment.GliderReforge;
import dev.aeroscapes.equipment.ReforgeProfiles;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import tictim.paraglider.api.stamina.Stamina;
import tictim.paraglider.impl.movement.PlayerMovement;

@Mixin(value = PlayerMovement.class, remap = false)
abstract class GliderLandingReforgeMixin {
  @WrapOperation(
      method = "applyMovement",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(DDD)V"),
      require = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$gliderLanding(
      Player player, double x, double y, double z, Operation<Void> original) {
    String profile = GliderReforge.profile(player);
    String baseline = ReforgeProfiles.basePath(profile);
    if (y >= 0
        || (!baseline.equals("glider_epic") && !baseline.equals("glider_legendary_catch"))
        || player.level().noCollision(player, player.getBoundingBox().move(0, -1.25, 0))) {
      original.call(player, x, y, z);
      return;
    }
    if (profile.equals("glider_legendary_catch")
        && player.isShiftKeyDown()
        && !Stamina.get(player).isDepleted()
        && Stamina.get(player).stamina() > 0) {
      original.call(player, x * 0.65, Math.max(y, -0.025), z * 0.65);
    } else {
      original.call(player, x, Math.max(y, -0.035), z);
    }
  }
}
