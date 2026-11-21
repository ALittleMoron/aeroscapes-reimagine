package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.aeroscapes.progression.PracticeActions;
import dev.aeroscapes.races.ProfessionEffects;
import harmonised.pmmo.core.Core;
import harmonised.pmmo.events.impl.BreakHandler;
import harmonised.pmmo.storage.DataAttachmentTypes;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BreakHandler.class, remap = false)
abstract class ProjectMmoHarvestMixin {
  @ModifyReturnValue(method = "calculateXpAward", at = @At("RETURN"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static Map<String, Long> aeroscapes$personalHarvest(
      Map<String, Long> awards, Core core, BlockEvent.BreakEvent event, CompoundTag context) {
    if (!PracticeActions.personal(event.getPlayer())) return Map.of();
    // PMMO rewrites awards in place while applying its native multipliers.
    if (PracticeActions.crop(event.getState()))
      return ProfessionEffects.mature(event.getState())
          ? new HashMap<>(Map.of("farming", 3L))
          : Map.of();
    if (event.getLevel() instanceof ServerLevel level
        && level
            .getChunkAt(event.getPos())
            .getData(DataAttachmentTypes.PLACED_MAP)
            .containsKey(event.getPos())) return Map.of();
    return awards;
  }
}
