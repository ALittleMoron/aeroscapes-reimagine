package dev.aeroscapes.mixin.progression;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.aeroscapes.races.RaceIdentity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayer.class)
abstract class InitialRaceSimulationMixin {
  // Keep networking and synchronisation running while the player's own survival clocks stand still.
  @WrapWithCondition(
      method = "doTick",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;tick()V"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked by MixinExtras.
  private boolean aeroscapes$tickAfterCreation(Player player) {
    return !RaceIdentity.awaitingChoice((ServerPlayer) player);
  }
}
