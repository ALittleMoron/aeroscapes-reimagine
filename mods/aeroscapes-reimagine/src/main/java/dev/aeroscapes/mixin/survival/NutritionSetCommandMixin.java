package dev.aeroscapes.mixin.survival;

import com.dannyandson.nutritionalbalance.network.ModNetworkHandler;
import com.dannyandson.nutritionalbalance.network.PlayerSync;
import com.dannyandson.nutritionalbalance.nutrients.PlayerNutritionData;
import com.mojang.brigadier.context.CommandContext;
import dev.aeroscapes.survival.NutritionDeficits;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.dannyandson.nutritionalbalance.commands.CommandSetNutrient", remap = false)
abstract class NutritionSetCommandMixin {
  @Inject(method = "setNutrients", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static void aeroscapes$refreshPenalties(
      CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> callback) {
    if (context.getSource().getEntity() instanceof ServerPlayer player) {
      var nutrition =
          PlayerNutritionData.getWorldNutritionData().getNutritionalBalancePlayer(player);
      NutritionDeficits.afterCommand(player, nutrition.getPlayerNutrients());
      ModNetworkHandler.sendToClient(new PlayerSync(nutrition), player);
    }
  }
}
