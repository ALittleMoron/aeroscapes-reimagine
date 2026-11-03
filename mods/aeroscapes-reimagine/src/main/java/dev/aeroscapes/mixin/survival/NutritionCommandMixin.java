package dev.aeroscapes.mixin.survival;

import com.dannyandson.nutritionalbalance.nutrients.PlayerNutritionData;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.dannyandson.nutritionalbalance.commands.CommandGetNutrients", remap = false)
public abstract class NutritionCommandMixin {
  @Inject(method = "run", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void aeroscapes$localizedNutrition(
      CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> callback) {
    if (context.getSource().getEntity() instanceof ServerPlayer player) {
      var message = Component.translatable("aeroscapes_reimagine.survival.command.nutrients");
      for (var nutrient :
          PlayerNutritionData.getWorldNutritionData()
              .getNutritionalBalancePlayer(player)
              .getPlayerNutrients()) {
        message
            .append("\n")
            .append(
                Component.translatable(
                    "aeroscapes_reimagine.survival.command.group",
                    Component.translatable(
                        "nutritionalbalance.nutrient." + nutrient.getNutrientName()),
                    Math.round(nutrient.getValue() * 10) / 10.0f,
                    Component.translatable(
                        "nutritionalbalance.nutrientstatus." + nutrient.getStatus().name())));
      }
      context.getSource().sendSuccess(() -> message, false);
    }
    callback.setReturnValue(0);
  }
}
