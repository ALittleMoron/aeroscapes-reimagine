package dev.aeroscapes.mixin.survival;

import com.dannyandson.nutritionalbalance.network.ModNetworkHandler;
import com.dannyandson.nutritionalbalance.network.PlayerSync;
import com.dannyandson.nutritionalbalance.nutrients.PlayerNutritionData;
import dev.aeroscapes.survival.NutritionDeficits;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.dannyandson.nutritionalbalance.events.EventPlayerTick", remap = false)
public abstract class NutritionDeficitMixin {
  @ModifyArg(
      method = "onPlayerTick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/player/Player;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"),
      require = 3)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private MobEffectInstance aeroscapes$matchDeficitDuration(MobEffectInstance effect) {
    return NutritionDeficits.permanentAttack(effect);
  }

  @Inject(method = "onPlayerTick", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$applyDeficitTiers(PlayerTickEvent.Post event, CallbackInfo callback) {
    // Match the provider's status refresh so penalties change together.
    if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 200 == 0) {
      var nutrition =
          PlayerNutritionData.getWorldNutritionData().getNutritionalBalancePlayer(player);
      // A higher-tier transition can leave the provider's aggregate status unchanged.
      if (NutritionDeficits.update(player, nutrition.getPlayerNutrients()))
        ModNetworkHandler.sendToClient(new PlayerSync(nutrition), player);
    }
  }
}
