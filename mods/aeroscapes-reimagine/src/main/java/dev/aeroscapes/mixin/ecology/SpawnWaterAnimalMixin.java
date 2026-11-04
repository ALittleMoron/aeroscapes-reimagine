package dev.aeroscapes.mixin.ecology;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.aeroscapes.ecology.ScopedSeaLevel;
import dev.aeroscapes.ecology.SpawnWaterline;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WaterAnimal.class)
public abstract class SpawnWaterAnimalMixin {
  @WrapMethod(method = "checkSurfaceWaterAnimalSpawnRules")
  @SuppressWarnings(
      "PMD.UnusedPrivateMethod") // Scope also reaches Spawn's merged HEAD tide-pool predicate.
  private static boolean aeroscapes$localWater(
      EntityType<?> type,
      LevelAccessor level,
      MobSpawnType reason,
      BlockPos position,
      RandomSource random,
      Operation<Boolean> original) {
    return ScopedSeaLevel.call(
        SpawnWaterline.at(level, position),
        () -> original.call(type, level, reason, position, random));
  }
}
