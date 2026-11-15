package dev.aeroscapes.mixin.sky;

import dev.aeroscapes.sky.WhaleEncounters;
import dev.aeroscapes.sky.WhalePods;
import dev.aeroscapes.sky.WhaleWaters;
import dev.dacite.skywhales.config.SWConfig;
import dev.dacite.skywhales.spawn.WhaleSpawner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WhaleSpawner.class, remap = false)
public abstract class WhaleOverworldSpawningMixin {
  @Inject(method = "onLevelTick", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings("PMD.UnusedPrivateMethod") // The native void rule also matches the End.
  private static void aeroscapes$overworldHabitat(
      LevelTickEvent.Post event, CallbackInfo callback) {
    if (!event.getLevel().dimension().equals(Level.OVERWORLD)) callback.cancel();
  }

  /** The configured chance is applied per region in {@link WhaleWaters}, after the native cap. */
  @Redirect(
      method = "onLevelTick",
      at = @At(value = "INVOKE", target = "Ldev/dacite/skywhales/config/SWConfig;spawnChance()D"))
  @SuppressWarnings("PMD.UnusedPrivateMethod") // Invoked through the pinned native spawner.
  private static double aeroscapes$regionalChance() {
    return SWConfig.spawnEnabled() ? 1.0 : 0.0;
  }

  @Inject(method = "trySpawnNear", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native placement checks only the central entity box.
  private static void aeroscapes$visiblePlacement(
      ServerLevel level, ServerPlayer player, RandomSource random, CallbackInfo callback) {
    callback.cancel();
    if (!WhaleWaters.allows(level.getSeed(), player.getX(), player.getZ(), random)) return;
    var whale = WhaleEncounters.spawnNear(level, player, random);
    if (whale != null) WhalePods.afterNaturalEncounter(level, whale, random);
  }
}
