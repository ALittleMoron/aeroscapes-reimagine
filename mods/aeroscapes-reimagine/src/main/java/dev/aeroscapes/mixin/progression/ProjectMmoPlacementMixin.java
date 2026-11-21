package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.aeroscapes.progression.PracticeActions;
import harmonised.pmmo.api.enums.EventType;
import harmonised.pmmo.core.Core;
import harmonised.pmmo.events.impl.PlaceHandler;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlaceHandler.class, remap = false)
abstract class ProjectMmoPlacementMixin {
  // Saplanting places real blocks with a Player that has no server party or personal XP.
  // PMMO's permission checks and event callbacks have already run at this point.
  @Inject(
      method = "handle",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lharmonised/pmmo/core/Core;getExperienceAwards(Lharmonised/pmmo/api/enums/EventType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Map;"),
      cancellable = true,
      require = 1,
      allow = 1)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by the mixin injector.
  private static void aeroscapes$skipPersonalPlacement(
      BlockEvent.EntityPlaceEvent event, CallbackInfo callback) {
    if (!(event.getEntity() instanceof ServerPlayer)) callback.cancel();
  }

  @WrapOperation(
      method = "handle",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lharmonised/pmmo/core/Core;getExperienceAwards(Lharmonised/pmmo/api/enums/EventType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Map;"))
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private static Map<String, Long> aeroscapes$buildingPractice(
      Core core,
      EventType event,
      BlockPos pos,
      Level level,
      Player player,
      CompoundTag context,
      Operation<Map<String, Long>> original) {
    return PracticeActions.personal(player) && !PracticeActions.crop(level.getBlockState(pos))
        ? new HashMap<>(Map.of("building", 1L))
        : Map.of();
  }
}
