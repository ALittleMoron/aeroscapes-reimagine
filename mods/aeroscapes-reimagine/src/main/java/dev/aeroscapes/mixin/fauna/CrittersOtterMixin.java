package dev.aeroscapes.mixin.fauna;

import dev.aeroscapes.ecology.BirdHabitat;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
    targets = "io.github.bonsaistudi0s.crittersandcompanions.common.entity.OtterEntity",
    remap = false)
public abstract class CrittersOtterMixin {
  @Inject(method = "checkOtterSpawnRules", at = @At("HEAD"), cancellable = true)
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Native NO_RESTRICTIONS would otherwise admit air above island
  // rivers.
  private static void aeroscapes$actualHabitat(
      EntityType<?> type,
      LevelAccessor level,
      MobSpawnType reason,
      BlockPos position,
      RandomSource random,
      CallbackInfoReturnable<Boolean> callback) {
    if (BirdHabitat.owned(level))
      callback.setReturnValue(BirdHabitat.supportedAquatic(level, position));
  }
}
