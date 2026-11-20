package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.progression.RefundPenalty;
import dev.aeroscapes.races.RaceTraits;
import harmonised.pmmo.api.events.XpEvent;
import harmonised.pmmo.storage.PmmoSavedData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PmmoSavedData.class, remap = false)
abstract class ProjectMmoRacePracticeMixin {
  // Native admin commands also post XpEvent, after changing XP. Only committed practice earns
  // carry.
  @WrapOperation(
      method = "addXp",
      at = @At(value = "INVOKE", target = "Lharmonised/pmmo/api/events/XpEvent;isCanceled()Z"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private boolean aeroscapes$applyAffinity(XpEvent event, Operation<Boolean> original) {
    boolean cancelled = original.call(event);
    if (!cancelled && event.getEntity() instanceof ServerPlayer player) {
      event.amountAwarded = RaceTraits.practiceAward(player, event.skill, event.amountAwarded);
      RefundPenalty.practice(player, event.skill, event.amountAwarded);
    }
    return cancelled;
  }
}
