package dev.aeroscapes.mixin.progression;

import harmonised.pmmo.events.impl.PlaceHandler;
import net.minecraft.server.level.ServerPlayer;
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
}
