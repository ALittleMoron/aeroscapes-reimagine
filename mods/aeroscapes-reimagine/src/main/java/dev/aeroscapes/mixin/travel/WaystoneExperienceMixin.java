package dev.aeroscapes.mixin.travel;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.travel.TravelIntegration;
import dev.aeroscapes.travel.WaystoneCostText;
import java.util.List;
import net.blay09.mods.waystones.requirement.ExperiencePointsRequirement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ExperiencePointsRequirement.class, remap = false)
public abstract class WaystoneExperienceMixin {
  @Inject(method = "appendHoverText", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native level-loss sprites cannot describe an exact point
  // balance.
  private void aeroscapes$exactPointTooltip(
      Player player, List<Component> tooltip, CallbackInfo callback) {
    WaystoneCostText.append(
        player, ((ExperiencePointsRequirement) (Object) this).getPoints(), tooltip);
    callback.cancel();
  }

  @ModifyReturnValue(method = "canAfford", at = @At("RETURN"))
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Keep exact point budgets usable in the native client UI.
  private boolean aeroscapes$roundVisiblePoints(boolean original, Player player) {
    return TravelIntegration.experiencePoints(player)
        >= ((ExperiencePointsRequirement) (Object) this).getPoints();
  }
}
